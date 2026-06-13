package com.zhishu.knowledge;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String fileName;
    private String fileMd5;
    private String objectKey;
    private String status;        // PARSING / READY / FAILED
    private Integer version;      // 向量版本号：更新时 +1，旧向量整体失效
    private Integer deleted;      // 软删除
    private Integer chunkCount;
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
