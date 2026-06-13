package com.zhishu.ticket;

import com.zhishu.chat.RedisChatMemoryStore;
import com.zhishu.governance.TokenAuditService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 工单 Agent：AiServices 把「接口声明 + 工具集 + 记忆」编织成一个可自主决策的 Agent。
 * 整体超时 + 轮次限制 + 参数校验三层兜底见 TicketTools。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAgentService {

    private final ChatLanguageModel chatModel;
    private final TicketTools ticketTools;
    private final RedisChatMemoryStore memoryStore;
    private final TokenAuditService tokenAuditService;

    private TicketAssistant assistant;

    interface TicketAssistant {
        @SystemMessage("""
                你是企业 IT 服务台的工单助手。知识库无法解答用户的问题时，请按以下流程处理：
                1. 调用 createTicket 创建工单（标题简洁、描述完整）；
                2. 根据问题内容判断分类（FAULT 故障 / CONSULT 咨询 / PERMISSION 权限 / PURCHASE 采购），
                   调用 classifyTicket 设置分类；
                3. 调用 assignTicket 派发给处理人；
                4. 用一段友好的中文向用户总结：工单号、分类、当前状态，并告知可随时询问进度。
                如果用户是在查询已有工单的进度，直接调用 queryTicketStatus 并转述结果。
                不要编造工单号；工具返回什么就基于什么回答。
                """)
        String handle(@MemoryId String memoryId, @UserMessage String question);
    }

    @PostConstruct
    void init() {
        this.assistant = AiServices.builder(TicketAssistant.class)
                .chatLanguageModel(chatModel)
                .tools(ticketTools)
                // Agent 会话与问答会话共用 Redis 记忆体系，多实例共享
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(memoryStore)
                        .build())
                .build();
    }

    public String handle(Long userId, String conversationId, String question) {
        ticketTools.beginSession(userId);   // 初始化轮次计数与去重集
        long start = System.currentTimeMillis();
        try {
            String reply = assistant.handle("agent:" + conversationId, question);
            // Agent 多轮工具调用的 Token 用量难以逐次回调，这里按字数估算入账（标注 AGENT 场景）
            tokenAuditService.record(userId, "AGENT", question.length(), reply.length());
            return reply;
        } catch (Exception e) {
            log.error("agent failed", e);
            return "抱歉，工单助手处理时遇到问题：" + e.getMessage() + "。您可以稍后重试或联系管理员。";
        } finally {
            ticketTools.endSession();
            log.info("agent session cost {} ms", System.currentTimeMillis() - start);
        }
    }
}
