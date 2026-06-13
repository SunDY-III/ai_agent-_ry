package com.zhishu.rag;

import com.zhishu.vector.ScoredChunk;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RagResult {
    private List<ScoredChunk> chunks;   // 重排后 TopN，进入 Prompt + 作为引用来源
    private double confidence;          // 向量通道最高相似度，作为“知识库能否回答”的置信度
}
