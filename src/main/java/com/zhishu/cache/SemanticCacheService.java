package com.zhishu.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishu.vector.VectorStoreService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 语义缓存：相似问题（向量相似度 >= 阈值）直接命中历史回答，省 Token、降延迟。
 *
 * 三个设计点（面试点）：
 * 1. 阈值偏保守（默认 0.95）：宁可漏命中走一次 LLM，也不要误命中答非所问；
 * 2. 命中回答前端展示“来自历史相似问题”标识，用户可强制重新生成；
 * 3. 失效联动：缓存条目记录其依据的 docId 列表，知识库按文档维度变更时定向清除，
 *    避免“文档已更新、缓存还在答旧内容”。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private static final String KEY_PREFIX = "sem:cache:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.semantic-cache.threshold}") private double threshold;
    @Value("${app.semantic-cache.ttl-hours}") private long ttlHours;

    @Data
    @NoArgsConstructor
    public static class CacheEntry {
        private String question;
        private String answer;
        private float[] vector;
        private List<Long> sourceDocIds;
    }

    public Optional<CacheEntry> lookup(float[] queryVector) {
        CacheEntry best = null;
        double bestScore = 0;
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build())) {
            while (cursor.hasNext()) {
                String json = redis.opsForValue().get(cursor.next());
                if (json == null) continue;
                try {
                    CacheEntry e = objectMapper.readValue(json, CacheEntry.class);
                    double score = VectorStoreService.cosine(queryVector, e.getVector());
                    if (score > bestScore) { bestScore = score; best = e; }
                } catch (Exception ignore) { }
            }
        }
        if (best != null && bestScore >= threshold) {
            log.info("semantic cache HIT, score={}, q={}", bestScore, best.getQuestion());
            return Optional.of(best);
        }
        return Optional.empty();
    }

    @SneakyThrows
    public void put(String question, String answer, float[] vector, List<Long> sourceDocIds) {
        CacheEntry e = new CacheEntry();
        e.setQuestion(question);
        e.setAnswer(answer);
        e.setVector(vector);
        e.setSourceDocIds(sourceDocIds);
        redis.opsForValue().set(KEY_PREFIX + Math.abs(question.hashCode()) + ":" + System.currentTimeMillis(),
                objectMapper.writeValueAsString(e), Duration.ofHours(ttlHours));
    }

    /** 知识库文档变更 -> 清除依据该文档生成的缓存条目 */
    public void invalidateByDoc(Long docId) {
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String json = redis.opsForValue().get(key);
                if (json == null) continue;
                try {
                    CacheEntry e = objectMapper.readValue(json, CacheEntry.class);
                    if (e.getSourceDocIds() != null && e.getSourceDocIds().contains(docId)) {
                        redis.delete(key);
                    }
                } catch (Exception ignore) { }
            }
        }
    }
}
