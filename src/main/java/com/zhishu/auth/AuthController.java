package com.zhishu.auth;

import com.zhishu.common.ApiResponse;
import com.zhishu.common.BizException;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public record AuthRequest(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody AuthRequest req) {
        userRepository.findByUsername(req.username()).ifPresent(u -> {
            throw new BizException("用户名已存在");
        });
        User user = new User();
        user.setUsername(req.username());
        user.setPassword(BCrypt.hashpw(req.password(), BCrypt.gensalt()));
        user.setRole("USER");
        userRepository.save(user);
        return login(req);
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody AuthRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new BizException(401, "用户名或密码错误"));
        if (!BCrypt.checkpw(req.password(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getRole());
        return ApiResponse.ok(Map.of("token", token, "userId", user.getId(), "username", user.getUsername()));
    }
}
