package com.zhishu.chat;

import com.zhishu.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 流式对话入口。
 * 为什么选 SSE 而不是 WebSocket（面试点）：流式回答是典型的服务端单向推送，
 * SSE 基于普通 HTTP、浏览器原生自动重连、无需协议升级握手；需要双向实时交互才值得上 WS。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String conversationId, @RequestParam String question) {
        // 超时 3 分钟：到点触发 onTimeout 回调统一释放，防连接泄漏
        SseEmitter emitter = new SseEmitter(180_000L);
        Long userId = UserContext.require();   // ThreadLocal 在异步线程不可见，这里先取值再传递
        chatService.streamChat(userId, userId + ":" + conversationId, question, emitter);
        return emitter;
    }
}
