package com.zhishu.governance;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "token_usage_log")
public class TokenUsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String scene;          // CHAT / AGENT / SUMMARY / EMBEDDING
    private Integer inputTokens;
    private Integer outputTokens;
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
