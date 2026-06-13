package com.zhishu.knowledge;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "document_chunk")
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long docId;
    private Integer docVersion;
    private Integer seq;
    private String content;
}
