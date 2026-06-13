# 智枢 —— 企业知识库问答与智能工单 Agent 平台

基于 **SpringBoot 3 + LangChain4j** 的「RAG 检索增强 + Agent 工具编排」双引擎平台：

- 文档上传自动构建私有知识库，混合检索（向量 + 关键词）+ RRF 融合 + 重排序，回答附带片段级**引用溯源**，全程 **SSE 流式输出**；
- 知识库置信度不足时，**工单 Agent** 经 Function Calling 自动完成创建工单 → 智能分类 → 负载派单 → 进度查询的业务闭环；
- 配套语义缓存、Redis+Lua 双维度限流、Token 审计、Resilience4j 熔断降级、DFA 敏感词过滤等稳定性与成本治理手段。

> 适合作为 Java 后端 + 大模型应用方向的简历项目。各模块与面试考点的映射见文末。

---

## 架构总览

```
┌──────────────────────────────────────────────────────────┐
│  前端 frontend/index.html —— SSE 流式对话 / 文档管理       │
└─────────────────────────┬────────────────────────────────┘
                          │ HTTP / SSE (JWT)
┌─────────────────────────▼────────────────────────────────┐
│  SpringBoot 3 应用层                                      │
│  · JwtInterceptor 鉴权   · RateLimitInterceptor 限流      │
│  ┌──────────┐  ┌────────────┐  ┌──────────────────────┐  │
│  │ chat     │  │ knowledge  │  │ ticket (Agent)       │  │
│  │ SSE 流式  │  │ 解析/切分   │  │ @Tool 编排 / ReAct    │  │
│  │ 多轮记忆  │  │ 向量化入库  │  │ 状态机 + 乐观锁        │  │
│  └──────────┘  └────────────┘  └──────────────────────┘  │
├──────────────────────────────────────────────────────────┤
│  AI 编排层 LangChain4j                                    │
│  · RAG Pipeline（混合检索 + RRF + 重排序）                 │
│  · AiServices + @Tool（Function Calling）                 │
│  · ChatMemoryStore（Redis 持久化 + 摘要压缩）              │
├──────────────────────────────────────────────────────────┤
│  MySQL │ Redis（缓存/会话/限流/向量/派单负载）              │
│  RabbitMQ（文档异步解析）│ MinIO（原始文档）                │
└──────────────────────────────────────────────────────────┘
```

## 技术栈

| 层 | 选型 |
|---|---|
| 框架 | Spring Boot 3.2.5（Java 17）、Spring Data JPA |
| AI 编排 | LangChain4j 0.36.2（OpenAI 协议，可接 DeepSeek / 硅基流动等国产模型） |
| 向量检索 | Redis 轻量向量库（SCAN + 余弦相似度，接口已抽象，可平滑换 Milvus） |
| 关键词检索 | MySQL FULLTEXT（ngram 中文分词） |
| 中间件 | Redis、RabbitMQ、MinIO |
| 文档解析 | Apache Tika（PDF / Word / Markdown） |
| 稳定性 | Resilience4j 熔断、Redis+Lua 滑动窗口限流、信号量并发控制 |

## 目录结构

```
src/main/java/com/zhishu
├── auth/         JWT 登录注册（SSE 兼容 query token）
├── chat/         SSE 流式对话主链路、Redis ChatMemoryStore、摘要压缩
├── knowledge/    文档上传(MD5 幂等) → MQ 异步解析 → 语义切分 → 向量化
├── rag/          混合检索、RRF 融合、规则重排序、引用溯源
├── cache/        语义缓存（阈值 0.95，按来源文档联动失效）
├── ticket/       工单 Agent：@Tool 工具集、状态机+乐观锁、ZSet 负载派单
├── vector/       Redis 向量存取与余弦相似度
├── governance/   Lua 限流、Token 审计(AOP/@Async)、DFA 敏感词
├── common/       统一响应、全局异常（乐观锁冲突 → 409）
└── config/       LLM 模型、MQ、MinIO、拦截器装配
```

## 快速启动

### 1. 启动中间件

```bash
docker compose up -d        # MySQL / Redis / RabbitMQ / MinIO
```

首次启动后导入表结构：

```bash
mysql -h127.0.0.1 -uroot -proot zhishu < sql/schema.sql
```

### 2. 配置模型 API Key

支持任意 OpenAI 协议兼容服务（DeepSeek、硅基流动等，成本几十元内可完成全部开发测试）：

```bash
export LLM_API_KEY=sk-xxx          # 对话模型
export EMBEDDING_API_KEY=sk-xxx    # Embedding 模型（可与上面相同）
```

模型名、base-url 在 `application.yml` 的 `llm:` 段调整。

### 3. 运行

```bash
mvn spring-boot:run
```

浏览器打开 `frontend/index.html`，注册登录后即可上传文档、流式提问、观察工单 Agent 接管。

> 说明：本仓库在离线环境中生成，未经 `mvn compile` 实际编译验证，本地首次运行请以编译器提示为准微调（依赖版本均为 Maven 中央仓库真实存在的版本）。

## 核心链路速览

**RAG 读侧**：提问 → 敏感词检测 → 语义缓存 → 向量召回 + 关键词召回 → RRF 融合（score = Σ 1/(k+rank)）→ 重排序取 TopN → 置信度判断 → 组装 Prompt（[片段n] 编号）→ LLM 流式生成 → SSE 推送 token/source/done 事件 → 回填缓存 + Token 审计。

**Agent 接管**：置信度 < 0.45 → TicketAgentService（AiServices + Redis 记忆）→ LLM 按 ReAct 循环调用 createTicket / classifyTicket / assignTicket / queryTicketStatus → ThreadLocal 会话上下文做轮次上限 + 同工具同参去重，防死循环。

**写侧**：上传 → MD5 秒传判断 → MinIO 存原文 → MQ 投递 → Tika 解析 → 语义段落切分（带 overlap）→ 逐块向量化 → Redis 进度上报（前端轮询）。

## 面试考点映射

| 简历表述 | 对应代码 |
|---|---|
| 混合检索 + RRF 融合 | `rag/RrfFusion.java`、`RagService.java` |
| 重排序（规则打分） | `rag/Reranker.java` |
| 语义切分 + overlap | `knowledge/TextSplitter.java` |
| MD5 幂等秒传 / 软删除防旧向量 | `knowledge/DocumentService.java` |
| SSE 断连/超时资源释放 | `chat/ChatService.java`（AtomicBoolean closed） |
| ChatMemoryStore 持久化 Redis | `chat/RedisChatMemoryStore.java` |
| 历史摘要压缩控 Token | `chat/MemoryService.java` |
| @Tool / ReAct / 防死循环 | `ticket/TicketTools.java`、`TicketAgentService.java` |
| 状态机 + 乐观锁 | `ticket/TicketStatus.java`、`Ticket.java`（@Version） |
| ZSet 最小负载派单 | `ticket/AssignService.java` |
| Redis + Lua 滑动窗口限流 | `resources/lua/sliding_window_rate_limit.lua`、`governance/RateLimitInterceptor.java` |
| 语义缓存联动失效 | `cache/SemanticCacheService.java` |
| Token 审计 + 日报 | `governance/TokenAuditService.java` |
| Resilience4j 熔断降级（回退纯检索） | `chat/ChatService.java`（@CircuitBreaker + fallback） |
| DFA 敏感词双向过滤 | `governance/SensitiveWordFilter.java` |

## 与 4 周路线图的对应

- 第 1 周（骨架 + 基础问答）：auth/、chat/、config/、Redis 记忆 ✅
- 第 2 周（RAG 链路）：knowledge/、vector/、rag/、引用溯源 ✅
- 第 3 周（工单 Agent）：ticket/ 全模块 ✅
- 第 4 周（治理 + 打磨）：governance/、cache/、frontend/、README ✅

建议按此顺序自己跑一遍、改一遍，并准备至少一个真实 badcase 调优故事（如调整 chunk 大小/overlap 解决某类问题召回失败），面试更有说服力。
