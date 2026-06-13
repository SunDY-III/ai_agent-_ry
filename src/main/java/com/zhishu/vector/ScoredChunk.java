package com.zhishu.vector;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 检索结果统一载体：向量通道与关键词通道都转成它，方便后续 RRF 融合 */
@Data
@AllArgsConstructor
public class ScoredChunk {
    private Long chunkId;
    private Long docId;
    private Integer seq;
    private String fileName;
    private String content;
    private double score;
}
