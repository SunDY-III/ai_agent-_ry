package com.zhishu.ticket;

import com.zhishu.common.BizException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Agent 工具集：@Tool 的方法名/描述/参数 schema 会随 Prompt 下发给 LLM，
 * LLM 输出结构化的调用意图（函数名 + JSON 参数），框架反射执行后把结果回填，
 * LLM 基于结果继续推理 —— 这就是 ReAct（Reasoning + Acting）循环的落地形态。
 *
 * 可靠性兜底（面试点）：
 * 1. 入参校验：LLM 可能抽出不存在的工单号/非法分类，每个工具入口先校验再执行；
 * 2. 轮次限制：ThreadLocal 计数器限制单次会话的工具调用总轮次，防 Agent 死循环烧 Token；
 * 3. 重复检测：同一轮会话内“同工具 + 同参数”的重复调用直接拒绝（典型死循环特征）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketTools {

    private final TicketService ticketService;

    @Value("${app.agent.max-tool-rounds}")
    private int maxToolRounds;

    /** 每次 Agent 会话的调用上下文（轮次 + 去重集 + 当前用户） */
    private static final ThreadLocal<Context> CTX = new ThreadLocal<>();

    static class Context {
        Long userId;
        int rounds = 0;
        Set<String> invoked = new HashSet<>();
    }

    public void beginSession(Long userId) {
        Context ctx = new Context();
        ctx.userId = userId;
        CTX.set(ctx);
    }

    public void endSession() {
        CTX.remove();
    }

    private void guard(String toolName, String argsFingerprint) {
        Context ctx = CTX.get();
        if (ctx == null) throw new BizException("Agent 会话未初始化");
        if (++ctx.rounds > maxToolRounds) {
            throw new BizException("已达到最大工具调用轮次(" + maxToolRounds + ")，请人工介入");
        }
        String key = toolName + "|" + argsFingerprint;
        if (!ctx.invoked.add(key)) {
            throw new BizException("检测到重复的相同工具调用，已中断以防止死循环: " + toolName);
        }
    }

    @Tool("当知识库无法回答用户问题、或用户明确要求人工处理时，创建一个工单。返回工单号。")
    public String createTicket(@P("工单标题，简洁概括问题") String title,
                               @P("问题的详细描述") String description) {
        guard("createTicket", title);
        if (title == null || title.isBlank()) throw new BizException("工单标题不能为空");
        Ticket t = ticketService.create(CTX.get().userId, title,
                description == null ? title : description);
        log.info("[agent] createTicket -> {}", t.getTicketNo());
        return "工单创建成功，工单号: " + t.getTicketNo();
    }

    @Tool("为指定工单设置分类。分类只能是: FAULT(故障) / CONSULT(咨询) / PERMISSION(权限) / PURCHASE(采购)")
    public String classifyTicket(@P("工单号，形如 TK20260612xxxx") String ticketNo,
                                 @P("分类，FAULT/CONSULT/PERMISSION/PURCHASE 四选一") String category) {
        guard("classifyTicket", ticketNo + ":" + category);
        Ticket t = ticketService.classify(ticketNo, category == null ? "" : category.trim().toUpperCase());
        log.info("[agent] classifyTicket {} -> {}", ticketNo, t.getCategory());
        return "工单 " + ticketNo + " 已分类为 " + t.getCategory();
    }

    @Tool("将已分类的工单派发给当前负载最低的处理人，工单进入处理中状态。")
    public String assignTicket(@P("工单号") String ticketNo) {
        guard("assignTicket", ticketNo);
        Ticket t = ticketService.assign(ticketNo);
        log.info("[agent] assignTicket {} -> handler {}", ticketNo, t.getAssigneeId());
        return "工单 " + ticketNo + " 已派发给处理人 #" + t.getAssigneeId() + "，状态: 处理中";
    }

    @Tool("查询某个工单的当前状态与处理进度。")
    public String queryTicketStatus(@P("工单号") String ticketNo) {
        guard("queryTicketStatus", ticketNo);
        Ticket t = ticketService.mustGet(ticketNo);
        return "工单 %s | 标题: %s | 分类: %s | 状态: %s | 处理人: %s".formatted(
                t.getTicketNo(), t.getTitle(),
                t.getCategory() == null ? "未分类" : t.getCategory(),
                t.getStatus(),
                t.getAssigneeId() == null ? "未派发" : "#" + t.getAssigneeId());
    }
}
