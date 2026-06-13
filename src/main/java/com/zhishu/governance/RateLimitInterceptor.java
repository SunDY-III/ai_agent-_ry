package com.zhishu.governance;

import com.zhishu.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * Redis + Lua 滑动窗口限流，双维度：
 *   用户级 —— 防单用户刷接口烧 Token；
 *   接口级 —— 防整体流量打垮 LLM 配额。
 * Lua 保证「清旧 + 计数 + 写入」三步原子执行，避免并发下超卖；
 * 滑动窗口相比固定窗口没有“窗口边界双倍突刺”问题（面试点）。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    @Value("${app.rate-limit.user-capacity}")  private int userCapacity;
    @Value("${app.rate-limit.api-capacity}")   private int apiCapacity;
    @Value("${app.rate-limit.window-seconds}") private int windowSeconds;

    public RateLimitInterceptor(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/sliding_window_rate_limit.lua")));
        this.script.setResultType(Long.class);
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        String path = req.getRequestURI();

        // 用户级
        Long userId = UserContext.get();
        if (userId != null) {
            Long pass = redis.execute(script, List.of("rl:user:" + userId + ":" + path),
                    String.valueOf(windowMillis), String.valueOf(userCapacity), String.valueOf(now));
            if (pass == null || pass == 0) {
                reject(resp, "请求过于频繁，请稍后再试");
                return false;
            }
        }
        // 接口级
        Long pass = redis.execute(script, List.of("rl:api:" + path),
                String.valueOf(windowMillis), String.valueOf(apiCapacity), String.valueOf(now));
        if (pass == null || pass == 0) {
            reject(resp, "系统繁忙，请稍后再试");
            return false;
        }
        return true;
    }

    private void reject(HttpServletResponse resp, String msg) throws Exception {
        resp.setStatus(429);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":429,\"message\":\"" + msg + "\"}");
    }
}
