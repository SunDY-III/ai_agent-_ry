package com.zhishu.knowledge;

import com.rabbitmq.client.Channel;
import com.zhishu.config.RabbitConfig;
import com.zhishu.governance.TokenAuditService;
import com.zhishu.vector.VectorRecord;
import com.zhishu.vector.VectorStoreService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * 文档解析消费者：MinIO 取原文 -> Tika 抽取文本 -> 语义切分 -> 逐块向量化 -> 入向量库 + 落 chunk 表。
 * 进度写 Redis，前端轮询 /api/doc/{id}/progress 展示。
 * 手动 ack：成功 ack；失败 nack 不重回队列（标记 FAILED 由用户重传，避免坏文件无限重试）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseListener {

    private final KnowledgeDocumentRepository docRepository;
    private final DocumentChunkRepository chunkRepository;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingModel embeddingModel;
    private final TextSplitter textSplitter;
    private final MinioClient minioClient;
    private final StringRedisTemplate redis;
    private final TokenAuditService tokenAuditService;
    private final Tika tika = new Tika();

    @Value("${minio.bucket}")
    private String bucket;

    @RabbitListener(queues = RabbitConfig.DOC_PARSE_QUEUE)
    public void onMessage(String docIdStr, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        Long docId = Long.valueOf(docIdStr);
        KnowledgeDocument doc = docRepository.findById(docId).orElse(null);
        if (doc == null) {
            channel.basicAck(tag, false);
            return;
        }
        try {
            parse(doc);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("doc parse failed, docId={}", docId, e);
            doc.setStatus("FAILED");
            docRepository.save(doc);
            channel.basicNack(tag, false, false);   // 不重回队列
        }
    }

    private void parse(KnowledgeDocument doc) throws Exception {
        // 1. 取原文 + Tika 抽取纯文本（统一支持 PDF/Word/Markdown/TXT）
        String text;
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(doc.getObjectKey()).build())) {
            text = tika.parseToString(in);
        }
        setProgress(doc.getId(), 20);

        // 2. 语义切分（带 overlap）
        List<String> chunks = textSplitter.split(text);
        if (chunks.isEmpty()) throw new IllegalStateException("文档无有效文本");
        setProgress(doc.getId(), 30);

        // 3. 逐块向量化入库（落 MySQL 拿 chunkId 作引用溯源锚点，再写向量库）
        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);

            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocId(doc.getId());
            chunk.setDocVersion(doc.getVersion());
            chunk.setSeq(i);
            chunk.setContent(content);
            chunkRepository.save(chunk);

            float[] vector = embeddingModel.embed(content).content().vector();
            tokenAuditService.record(doc.getUserId(), "EMBEDDING", content.length() / 2, 0);  // 估算
            vectorStoreService.save(new VectorRecord(
                    doc.getId(), doc.getVersion(), chunk.getId(), i, doc.getFileName(), content, vector));

            setProgress(doc.getId(), 30 + (int) (70.0 * (i + 1) / chunks.size()));
        }

        doc.setStatus("READY");
        doc.setChunkCount(chunks.size());
        docRepository.save(doc);
        setProgress(doc.getId(), 100);
        log.info("doc parsed: id={}, chunks={}", doc.getId(), chunks.size());
    }

    private void setProgress(Long docId, int progress) {
        redis.opsForValue().set(DocumentService.PROGRESS_KEY + docId, String.valueOf(progress), Duration.ofHours(1));
    }
}
