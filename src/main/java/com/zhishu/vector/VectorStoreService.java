package com.zhishu.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 基于 Redis 的轻量向量存储：每个 chunk 一个 key，检索时 SCAN 全量算余弦相似度。
 *
 * 选型说明（面试点）：
 * 1. 学生项目知识库量级（万级以内 chunk）线性扫描完全够用，没必要为了简历堆 Milvus；
 * 2. 但要知其所以然：数据量上去后应换 RediSearch(HNSW) 或 Milvus(HNSW/IVF_FLAT)，
 *    本类抽象了 save/search/deleteByDoc 三个方法，替换实现即可无感迁移；
 * 3. 旧版本向量按 docId+version 物理删除，配合 MySQL 软删除 + 版本号，保证检索不命中旧内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private static final String KEY_PREFIX = "vec:chunk:";   // vec:chunk:{docId}:{chunkId}

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SneakyThrows
    public void save(VectorRecord record) {
        String key = KEY_PREFIX + record.getDocId() + ":" + record.getChunkId();
        redis.opsForValue().set(key, objectMapper.writeValueAsString(record));
    }

    /** 余弦相似度 TopK。返回 score 已归一化到 [0,1] 附近，可直接做置信度判断 */
    public List<ScoredChunk> search(float[] queryVector, int topK) {
        List<ScoredChunk> result = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build())) {
            while (cursor.hasNext()) {
                String json = redis.opsForValue().get(cursor.next());
                if (json == null) continue;
                try {
                    VectorRecord r = objectMapper.readValue(json, VectorRecord.class);
                    double score = cosine(queryVector, r.getVector());
                    result.add(new ScoredChunk(r.getChunkId(), r.getDocId(), r.getSeq(), r.getFileName(), r.getContent(), score));
                } catch (Exception e) {
                    log.warn("bad vector record skipped", e);
                }
            }
        }
        result.sort(Comparator.comparingDouble(ScoredChunk::getScore).reversed());
        return result.size() > topK ? result.subList(0, topK) : result;
    }

    /** 文档删除/更新时清理对应向量（更新 = 删旧版本 + 写新版本） */
    public void deleteByDoc(Long docId) {
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(KEY_PREFIX + docId + ":*").count(500).build())) {
            while (cursor.hasNext()) {
                redis.delete(cursor.next());
            }
        }
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
