package com.zhishu.governance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TokenUsageLogRepository extends JpaRepository<TokenUsageLog, Long> {

    @Query("""
            select l.userId as userId, l.scene as scene,
                   sum(l.inputTokens) as inputTokens, sum(l.outputTokens) as outputTokens
            from TokenUsageLog l
            where l.createdAt between :start and :end
            group by l.userId, l.scene
            """)
    List<DailyUsage> aggregate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    interface DailyUsage {
        Long getUserId();
        String getScene();
        Long getInputTokens();
        Long getOutputTokens();
    }
}
