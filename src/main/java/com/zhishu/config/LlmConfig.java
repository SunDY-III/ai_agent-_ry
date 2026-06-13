package com.zhishu.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.Semaphore;

/**
 * LangChain4j 模型装配。
 * 国产模型（DeepSeek / 通义 / 智谱）都兼容 OpenAI 协议，换模型只改 base-url + model 配置。
 */
@Configuration
public class LlmConfig {

    @Value("${llm.base-url}")           private String baseUrl;
    @Value("${llm.api-key}")            private String apiKey;
    @Value("${llm.chat-model}")         private String chatModel;
    @Value("${llm.embedding-base-url}") private String embBaseUrl;
    @Value("${llm.embedding-api-key}")  private String embApiKey;
    @Value("${llm.embedding-model}")    private String embModel;
    @Value("${llm.max-concurrency}")    private int maxConcurrency;

    /** 非流式模型：工单分类、历史摘要压缩、Agent 推理用 */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(chatModel)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /** 流式模型：SSE 对话用 */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(chatModel)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(embBaseUrl).apiKey(embApiKey).modelName(embModel)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * LLM 调用层信号量：控制同时在途的模型请求数，防止突发流量打爆 API 配额。
     * （网关限流挡“请求量”，信号量挡“并发量”，两层各管一段）
     */
    @Bean
    public Semaphore llmConcurrencyLimiter() {
        return new Semaphore(maxConcurrency);
    }
}
