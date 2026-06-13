package com.zhishu.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 自定义 ChatMemoryStore：把多轮会话记忆持久化到 Redis。
 *
 * 为什么不用默认内存实现（面试点）：
 * 1. 服务重启上下文全丢；
 * 2. 多实例部署时会话不共享（用户两次请求落到不同实例就“失忆”）；
 * 3. Redis 设 TTL 让不活跃会话自然过期，省去手动清理。
 */
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final Duration TTL = Duration.ofDays(3);

    private final StringRedisTemplate redis;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redis.opsForValue().get(KEY_PREFIX + memoryId);
        return json == null ? List.of() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        redis.opsForValue().set(KEY_PREFIX + memoryId, ChatMessageSerializer.messagesToJson(messages), TTL);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redis.delete(KEY_PREFIX + memoryId);
    }
}
