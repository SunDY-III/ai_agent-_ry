package com.zhishu.knowledge;

import com.zhishu.cache.SemanticCacheService;
import com.zhishu.common.BizException;
import com.zhishu.config.RabbitConfig;
import com.zhishu.vector.VectorStoreService;
import io.minio.PutObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    public static final String PROGRESS_KEY = "doc:progress:";   // doc:progress:{docId} -> 0~100

    private final KnowledgeDocumentRepository docRepository;
    private final DocumentChunkRepository chunkRepository;
    private final VectorStoreService vectorStoreService;
    private final SemanticCacheService semanticCacheService;
    private final MinioClient minioClient;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redis;

    @Value("${minio.bucket}")
    private String bucket;

    /**
     * 上传入口：只做轻量操作（MD5 去重 + 落 MinIO + 发 MQ），耗时解析全部异步。
     * 幂等设计：同 MD5 文档秒级返回已有记录 —— 与“秒传”同一方法论。
     */
    @SneakyThrows
    public KnowledgeDocument upload(Long userId, MultipartFile file) {
        byte[] bytes = file.getBytes();
        String md5 = DigestUtils.md5DigestAsHex(bytes);

        var existed = docRepository.findByFileMd5AndDeleted(md5, 0);
        if (existed.isPresent()) {
            log.info("doc md5 hit, instant return: {}", md5);
            return existed.get();    // 秒传：不重复解析、不重复向量化
        }

        String objectKey = UUID.randomUUID() + "/" + file.getOriginalFilename();
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(objectKey)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType(file.getContentType())
                .build());

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setUserId(userId);
        doc.setFileName(file.getOriginalFilename());
        doc.setFileMd5(md5);
        doc.setObjectKey(objectKey);
        doc.setStatus("PARSING");
        doc.setVersion(1);
        doc.setDeleted(0);
        doc.setChunkCount(0);
        docRepository.save(doc);

        redis.opsForValue().set(PROGRESS_KEY + doc.getId(), "0", Duration.ofHours(1));
        rabbitTemplate.convertAndSend(RabbitConfig.DOC_EXCHANGE, RabbitConfig.DOC_ROUTING_KEY, String.valueOf(doc.getId()));
        return doc;
    }

    public List<KnowledgeDocument> listMine(Long userId) {
        return docRepository.findByUserIdAndDeletedOrderByIdDesc(userId, 0);
    }

    public Map<String, Object> progress(Long docId) {
        KnowledgeDocument doc = docRepository.findById(docId).orElseThrow(() -> new BizException("文档不存在"));
        String p = redis.opsForValue().get(PROGRESS_KEY + docId);
        return Map.of("status", doc.getStatus(), "progress", p == null ? "100" : p);
    }

    /**
     * 软删除 + 向量清理 + 语义缓存联动失效。
     * 顺序很重要：先标记软删除（检索侧立即不可见），再清向量与缓存，
     * 即使后两步失败，软删除标记 + version 校验也能挡住旧内容被检索到。
     */
    @Transactional
    public void delete(Long userId, Long docId) {
        KnowledgeDocument doc = docRepository.findById(docId).orElseThrow(() -> new BizException("文档不存在"));
        if (!doc.getUserId().equals(userId)) throw new BizException(403, "无权操作该文档");
        doc.setDeleted(1);
        docRepository.save(doc);

        chunkRepository.deleteByDocId(docId);
        vectorStoreService.deleteByDoc(docId);
        semanticCacheService.invalidateByDoc(docId);   // 知识库变更 -> 相关语义缓存失效
    }
}
