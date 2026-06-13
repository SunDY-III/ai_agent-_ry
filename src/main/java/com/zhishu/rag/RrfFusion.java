package com.zhishu.rag;

import com.zhishu.vector.ScoredChunk;

import java.util.*;

/**
 * RRF（Reciprocal Rank Fusion）融合向量召回与关键词召回两路结果。
 *
 * 公式：score(d) = sum_i 1 / (k + rank_i(d))，k 为平滑常数（通常 60）。
 * 关键性质（面试必讲）：只看“排名”不看“分数”——向量相似度（0~1）和 BM25/全文得分
 * 量纲完全不同，没法直接加权；RRF 按排名倒数融合，天然规避量纲对齐问题。
 */
public final class RrfFusion {

    private RrfFusion() {}

    public static List<ScoredChunk> fuse(List<ScoredChunk> vectorHits, List<ScoredChunk> keywordHits, int k) {
        Map<Long, Double> rrfScore = new HashMap<>();
        Map<Long, ScoredChunk> byId = new HashMap<>();

        accumulate(vectorHits, k, rrfScore, byId);
        accumulate(keywordHits, k, rrfScore, byId);

        return rrfScore.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(e -> {
                    ScoredChunk c = byId.get(e.getKey());
                    return new ScoredChunk(c.getChunkId(), c.getDocId(), c.getSeq(), c.getFileName(), c.getContent(), e.getValue());
                })
                .toList();
    }

    private static void accumulate(List<ScoredChunk> hits, int k, Map<Long, Double> rrfScore, Map<Long, ScoredChunk> byId) {
        for (int rank = 0; rank < hits.size(); rank++) {
            ScoredChunk c = hits.get(rank);
            rrfScore.merge(c.getChunkId(), 1.0 / (k + rank + 1), Double::sum);
            byId.putIfAbsent(c.getChunkId(), c);
        }
    }
}
