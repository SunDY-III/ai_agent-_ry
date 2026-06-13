package com.zhishu.rag;

import com.zhishu.vector.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 规则重排序：RRF 分 + 查询词命中数加权。
 * 生产环境会换 Rerank 模型（如 bge-reranker，对 query-doc 做交叉编码精排），
 * 这里用规则打分起步并保留同样的接口形状，知其所以然即可（面试点）。
 */
@Component
public class Reranker {

    public List<ScoredChunk> rerank(String query, List<ScoredChunk> candidates, int topN) {
        List<String> terms = tokenize(query);
        return candidates.stream()
                .map(c -> new ScoredChunk(c.getChunkId(), c.getDocId(), c.getSeq(), c.getFileName(), c.getContent(),
                        c.getScore() + 0.01 * hitCount(c.getContent(), terms)))
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topN)
                .toList();
    }

    private int hitCount(String content, List<String> terms) {
        int hits = 0;
        for (String t : terms) {
            if (content.contains(t)) hits++;
        }
        return hits;
    }

    /** 简易二元切词，与 MySQL ngram(2) 行为对齐 */
    private List<String> tokenize(String query) {
        String q = query.replaceAll("\\s+", "");
        java.util.List<String> terms = new java.util.ArrayList<>();
        for (int i = 0; i + 2 <= q.length(); i++) terms.add(q.substring(i, i + 2));
        return terms;
    }
}
