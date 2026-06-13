package com.zhishu.ticket;

import com.zhishu.common.BizException;

import java.util.Map;
import java.util.Set;

/**
 * 工单状态机：PENDING -> PROCESSING -> RESOLVED -> CLOSED。
 * 状态流转白名单 + JPA 乐观锁（version 字段）双保险：
 * 白名单挡“非法流转”，乐观锁挡“并发流转”（两个处理人同时点处理）。
 */
public enum TicketStatus {
    PENDING, PROCESSING, RESOLVED, CLOSED;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            PENDING,    Set.of(PROCESSING, CLOSED),
            PROCESSING, Set.of(RESOLVED, CLOSED),
            RESOLVED,   Set.of(CLOSED, PROCESSING),   // 用户不满意可重开
            CLOSED,     Set.of()
    );

    public void assertCanTransitTo(TicketStatus target) {
        if (!ALLOWED.get(this).contains(target)) {
            throw new BizException("非法状态流转: " + this + " -> " + target);
        }
    }
}
