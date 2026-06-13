package com.zhishu.chat;

import com.zhishu.governance.TokenAuditService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文窗口控制：滑动窗口保留最近 N 轮 + 超长触发“历史摘要压缩”。
 * 把早期对话用 LLM 压成一段摘要塞回 SystemMessage，控制每次请求的 Token 成本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final RedisChatMemoryStore memoryStore;
    private final ChatLanguageModel chatModel;
    private final TokenAuditService tokenAuditService;

    @Value("${app.memory.window-size}")     private int windowSize;
    @Value("${app.memory.summary-trigger}") private int summaryTrigger;

    public List<ChatMessage> load(String memoryId) {
        return new ArrayList<>(memoryStore.getMessages(memoryId));
    }

    public void append(String memoryId, Long userId, UserMessage userMessage, AiMessage aiMessage) {
        List<ChatMessage> messages = load(memoryId);
        messages.add(userMessage);
        messages.add(aiMessage);

        if (messages.size() > summaryTrigger) {
            messages = compress(memoryId, userId, messages);
        }
        memoryStore.updateMessages(memoryId, messages);
    }

    /** 把窗口外的早期消息压成摘要，保留最近 windowSize*2 条原文 */
    private List<ChatMessage> compress(String memoryId, Long userId, List<ChatMessage> messages) {
        int keep = windowSize * 2;
        List<ChatMessage> old = messages.subList(0, messages.size() - keep);
        List<ChatMessage> recent = new ArrayList<>(messages.subList(messages.size() - keep, messages.size()));

        StringBuilder sb = new StringBuilder("请把以下多轮对话压缩成不超过 200 字的客观摘要，保留关键事实与未决问题：\n");
        old.forEach(m -> sb.append(m.type()).append(": ").append(m.toString()).append('\n'));

        try {
            Response<AiMessage> resp = chatModel.generate(UserMessage.from(sb.toString()));
            if (resp.tokenUsage() != null) {
                tokenAuditService.record(userId, "SUMMARY",
                        resp.tokenUsage().inputTokenCount(), resp.tokenUsage().outputTokenCount());
            }
            List<ChatMessage> compressed = new ArrayList<>();
            compressed.add(SystemMessage.from("[早期对话摘要] " + resp.content().text()));
            compressed.addAll(recent);
            log.info("memory compressed: {} -> {} messages, memoryId={}", messages.size(), compressed.size(), memoryId);
            return compressed;
        } catch (Exception e) {
            log.warn("memory compress failed, fallback to truncation", e);
            return recent;   // 摘要失败兜底：直接截断，可用性优先
        }
    }
}
