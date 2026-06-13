package com.zhishu.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 文档解析异步化：上传接口只负责落对象存储 + 发消息，耗时的解析/切分/向量化全部在消费者侧完成 */
@Configuration
public class RabbitConfig {

    public static final String DOC_EXCHANGE   = "zhishu.doc.exchange";
    public static final String DOC_PARSE_QUEUE = "zhishu.doc.parse";
    public static final String DOC_ROUTING_KEY = "doc.parse";

    @Bean
    public DirectExchange docExchange() { return new DirectExchange(DOC_EXCHANGE, true, false); }

    @Bean
    public Queue docParseQueue() {
        // 死信省略：解析失败标记文档 FAILED 即可，重试由用户重新触发
        return QueueBuilder.durable(DOC_PARSE_QUEUE).build();
    }

    @Bean
    public Binding docBinding() {
        return BindingBuilder.bind(docParseQueue()).to(docExchange()).with(DOC_ROUTING_KEY);
    }
}
