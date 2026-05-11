package app.healthplus.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 认证相关的 DTO（数据传输对象）定义。
 * <p>
 * 采用 Java record 定义，配合 JSR-303 注解实现请求参数的声明式校验。
 * </p>
 */
public class AuthDtos {

    /**
     * 用户注册请求体。
     * <p>
     * 校验规则：用户名 3-64 字符、邮箱格式合法、密码 6-128 字符。
     * </p>
     *
     * @param username 用户名，全局唯一
     * @param email    邮箱地址，全局唯一
     * @param password 明文密码（传输层走 HTTPS，存储层由 AuthService 负责 BCrypt 加密）
     */
    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 128) String password
    ) {}

    /**
     * 用户登录请求体。
     *
     * @param username 用户名
     * @param password 明文密码
     */
    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    /**
     * 认证响应体（注册和登录共用）。
     * <p>
     * 返回 JWT 令牌及用户基本信息，前端存储令牌后在后续请求的
     * Authorization 头中携带。
     * </p>
     *
     * @param token      JWT 令牌字符串
     * @param tokenType  令牌类型，固定为 "Bearer"
     * @param userId     用户唯一标识
     * @param username   用户名
     * @param role       用户角色（如 ROLE_USER）
     */
    public record AuthResponse(
            String token,
            String tokenType,
            String userId,
            String username,
            String role
    ) {}
}
