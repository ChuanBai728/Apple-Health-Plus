package app.healthplus.api.controller;

import app.healthplus.api.dto.AuthDtos.*;
import app.healthplus.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证控制器。
 * <p>
 * 职责：处理用户注册和登录请求，基于 JWT（JSON Web Token）实现无状态认证。
 * 注册成功后返回 JWT 令牌，登录验证凭据后签发新令牌。
 * </p>
 * <p>
 * 对应 API 端点：{@code /api/v1/auth}
 * </p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** 认证服务 —— 负责用户注册、密码校验和 JWT 令牌签发 */
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册。
     * <p>
     * 校验用户名和邮箱的唯一性，创建用户记录，并返回 JWT 令牌。
     * </p>
     *
     * @param req 注册请求，包含用户名、邮箱和密码（均经过 JSR-303 校验）
     * @return 包含 JWT 令牌和用户信息的认证响应
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    /**
     * 用户登录。
     * <p>
     * 验证用户名和密码，成功后签发 JWT 令牌。
     * </p>
     *
     * @param req 登录请求，包含用户名和密码
     * @return 包含 JWT 令牌和用户信息的认证响应
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
