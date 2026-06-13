package com.zhishu.auth;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String password;
    private String role;            // USER / HANDLER / ADMIN
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
