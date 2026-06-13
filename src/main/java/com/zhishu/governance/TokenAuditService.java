package com.zhishu.governance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Token 计费审计：
 * - 流式调用在 onComplete 回调里拿真实 TokenUsage 记账；
 * - Agent / Embedding 场景按内容长度估算（标注场景，方便区分精确值与估算值）；
 * - 异步落库不阻塞主链路；定时任务每日聚合出日报（实际可接邮件/钉钉）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenAuditService {

    private final TokenUsageLogRepository repository;

    @Async
    public void record(Long userId, String scene, Integer inputTokens, Integer outputTokens) {
        try {
            TokenUsageLog logRow = new TokenUsageLog();
            logRow.setUserId(userId == null ? 0L : userId);
            logRow.setScene(scene);
            logRow.setInputTokens(inputTokens == null ? 0 : inputTokens);
            logRow.setOutputTokens(outputTokens == null ? 0 : outputTokens);
            repository.save(logRow);
        } catch (Exception e) {
            log.warn("token audit failed (non-blocking)", e);   // 审计失败不影响业务
        }
    }

    /** 每日 1 点出前一天的 Token 消耗日报 */
    @Scheduled(cron = "0 0 1 * * ?")
    public void dailyReport() {
        LocalDateTime start = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime end = LocalDate.now().atStartOfDay();
        var rows = repository.aggregate(start, end);
        log.info("===== Token 日报 {} =====", start.toLocalDate());
        rows.forEach(r -> log.info("user={} scene={} in={} out={}",
                r.getUserId(), r.getScene(), r.getInputTokens(), r.getOutputTokens()));
    }
}
