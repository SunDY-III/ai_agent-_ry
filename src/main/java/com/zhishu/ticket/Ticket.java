package com.zhishu.ticket;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ticket")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ticketNo;
    private Long userId;
    private String title;
    private String description;
    private String category;        // FAULT / CONSULT / PERMISSION / PURCHASE
    @Enumerated(EnumType.STRING)
    private TicketStatus status;
    private Long assigneeId;

    @Version                        // JPA 乐观锁：并发更新时版本不一致抛 OptimisticLockException
    private Integer version;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
