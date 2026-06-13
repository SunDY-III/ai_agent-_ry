package com.zhishu.auth;

import com.zhishu.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        // SSE 用 EventSource 无法带 Header，兼容 query 参数携带 token
        String token = req.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        if (token == null) token = req.getParameter("token");
        if (token == null) {
            resp.setStatus(401);
            return false;
        }
        try {
            UserContext.set(jwtUtil.parseUserId(token));
            return true;
        } catch (Exception e) {
            resp.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        UserContext.clear();   // 防止线程池复用导致的用户串号
    }
}
