package com.zhishu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 「智枢」企业知识库问答与智能工单 Agent 平台
 * RAG 检索增强 + LangChain4j Agent 工具编排双引擎
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class ZhishuApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZhishuApplication.class, args);
    }
}
