package com.zhishu.rag;

import com.zhishu.governance.TokenAuditService;
import com.zhishu.knowledge.DocumentChunkRepository;
import com.zhishu.vector.ScoredChunk;
import com.zhishu.vector.VectorStoreService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 读链路：混合检索（向量 + 关键词）-> RRF 融合 -> 重排序 TopN。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final EmbeddingModel embeddingModel;
    private final VectorStoreService vectorStoreService;
    private final DocumentChunkRepository chunkRepository;
    private final Reranker reranker;
    private final TokenAuditService tokenAuditService;

    @Value("${app.rag.vector-top-k}")  private int vectorTopK;
    @Value("${app.rag.keyword-top-k}") private int keywordTopK;
    @Value("${app.rag.rerank-top-n}")  private int rerankTopN;
    @Value("${app.rag.rrf-k}")         private int rrfK;

    public float[] embed(Long userId, String text) {
        float[] v = embeddingModel.embed(text).content().vector();
        tokenAuditService.record(userId, "EMBEDDING", text.length() / 2, 0);
        return v;
    }

    public RagResult retrieve(Long userId, String question) {
        // 通道 1：向量相似度召回（语义近似强，专名/编号弱）
        float[] queryVector = embed(userId, question);
        List<ScoredChunk> vectorHits = vectorStoreService.search(queryVector, vectorTopK);
        double confidence = vectorHits.isEmpty() ? 0 : vectorHits.get(0).getScore();

        // 通道 2：关键词全文召回（专名/编号强，同义改写弱）
        List<ScoredChunk> keywordHits = chunkRepository.keywordSearch(question, keywordTopK).stream()
                .map(c -> new ScoredChunk(c.getId(), c.getDocId(), c.getSeq(), "", c.getContent(), 0.0))
                .toList();

        // RRF 融合（只看排名，规避两路分数量纲不一致）+ 规则重排取 TopN
        List<ScoredChunk> fused = RrfFusion.fuse(vectorHits, keywordHits, rrfK);
        List<ScoredChunk> topN = reranker.rerank(question, fused, rerankTopN);

        log.info("rag retrieve: q={}, vec={}, kw={}, fused={}, confidence={}",
                question, vectorHits.size(), keywordHits.size(), fused.size(), confidence);
        return new RagResult(topN, confidence);
    }

    /** 组装进 Prompt 的上下文段，带编号供 LLM 引用 */
    public String buildContext(List<ScoredChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            ScoredChunk c = chunks.get(i);
            sb.append("[片段").append(i + 1).append(" 来源:").append(c.getFileName())
              .append(" #").append(c.getSeq()).append("]\n")
              .append(c.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
