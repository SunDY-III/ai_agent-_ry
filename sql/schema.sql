CREATE DATABASE IF NOT EXISTS zhishu DEFAULT CHARACTER SET utf8mb4;
USE zhishu;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  username    VARCHAR(64)  NOT NULL UNIQUE,
  password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 摘要',
  role        VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER/HANDLER(工单处理人)/ADMIN',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 知识库文档元数据（软删除 + 版本号：文档更新时旧向量按 doc_id+version 失效，避免检索命中旧内容）
CREATE TABLE IF NOT EXISTS knowledge_document (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT       NOT NULL,
  file_name    VARCHAR(255) NOT NULL,
  file_md5     CHAR(32)     NOT NULL COMMENT '幂等去重：同 MD5 文档秒级返回',
  object_key   VARCHAR(255) NOT NULL COMMENT 'MinIO 对象键',
  status       VARCHAR(16)  NOT NULL DEFAULT 'PARSING' COMMENT 'PARSING/READY/FAILED',
  version      INT          NOT NULL DEFAULT 1 COMMENT '向量版本号',
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除',
  chunk_count  INT          NOT NULL DEFAULT 0,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_md5 (file_md5, deleted)
) ENGINE=InnoDB;

-- 文档切分块（保留原文用于：关键词全文检索 + 引用溯源定位）
CREATE TABLE IF NOT EXISTS document_chunk (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_id      BIGINT      NOT NULL,
  doc_version INT         NOT NULL DEFAULT 1,
  seq         INT         NOT NULL COMMENT '块序号，用于引用定位',
  content     TEXT        NOT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc (doc_id, doc_version),
  -- ngram 解析器支持中文全文检索：关键词召回通道（与向量召回做 RRF 融合）
  FULLTEXT KEY ft_content (content) WITH PARSER ngram
) ENGINE=InnoDB;

-- 工单表（@Version 乐观锁防状态流转并发冲突）
CREATE TABLE IF NOT EXISTS ticket (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_no    VARCHAR(32)  NOT NULL UNIQUE,
  user_id      BIGINT       NOT NULL,
  title        VARCHAR(255) NOT NULL,
  description  TEXT,
  category     VARCHAR(16)  COMMENT 'FAULT/CONSULT/PERMISSION/PURCHASE',
  status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/CLOSED',
  assignee_id  BIGINT       COMMENT '处理人',
  version      INT          NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user (user_id),
  KEY idx_assignee (assignee_id, status)
) ENGINE=InnoDB;

-- Token 计费审计流水（AOP/回调记录，定时任务按用户聚合出日报）
CREATE TABLE IF NOT EXISTS token_usage_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT      NOT NULL,
  scene         VARCHAR(32) NOT NULL COMMENT 'CHAT/AGENT/SUMMARY/CLASSIFY/EMBEDDING',
  input_tokens  INT         NOT NULL DEFAULT 0,
  output_tokens INT         NOT NULL DEFAULT 0,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB;

-- 预置两个工单处理人账号（密码均为 123456 的 BCrypt，可自行重置）
INSERT INTO sys_user(username, password, role) VALUES
 ('handler_a', '$2a$10$N.kmcuVHRJB7g5oZkX9ZUO9C3Hq0eY4F5n5o5o5o5o5o5o5o5o5oW', 'HANDLER'),
 ('handler_b', '$2a$10$N.kmcuVHRJB7g5oZkX9ZUO9C3Hq0eY4F5n5o5o5o5o5o5o5o5o5oW', 'HANDLER')
 ON DUPLICATE KEY UPDATE username = username;
