package com.zhishu.ticket;

import com.zhishu.auth.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 派单负载均衡：Redis ZSet 维护「处理人 -> 当前在手工单数」，
 * ZRANGE 取 score 最小者派单，O(logN) 拿到最小负载，天然支持多实例并发派单。
 */
@Service
@RequiredArgsConstructor
public class AssignService {

    private static final String LOAD_KEY = "ticket:handler:load";

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;

    /** 启动时把所有处理人灌入 ZSet（已存在则不覆盖其当前负载） */
    @PostConstruct
    public void init() {
        userRepository.findByRole("HANDLER").forEach(h ->
                redis.opsForZSet().addIfAbsent(LOAD_KEY, String.valueOf(h.getId()), 0));
    }

    /** 取当前负载最小的处理人并 +1 */
    public Long pickLeastLoaded() {
        Set<String> least = redis.opsForZSet().range(LOAD_KEY, 0, 0);
        if (least == null || least.isEmpty()) return null;
        String handlerId = least.iterator().next();
        redis.opsForZSet().incrementScore(LOAD_KEY, handlerId, 1);
        return Long.valueOf(handlerId);
    }

    /** 工单解决/关闭时归还负载 */
    public void release(Long handlerId) {
        if (handlerId != null) {
            redis.opsForZSet().incrementScore(LOAD_KEY, String.valueOf(handlerId), -1);
        }
    }
}
