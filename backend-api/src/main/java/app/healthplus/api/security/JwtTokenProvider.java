package app.healthplus.api.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌的生成、验证与 Claims 解析工具类。
 *
 * <p>职责单一：封装所有与 JWT 相关的操作，对外提供简洁的 API。</p>
 *
 * <h3>设计思路</h3>
 * <ul>
 *   <li>使用 HMAC-SHA 对称签名算法（{@link Keys#hmacShaKeyFor}），
 *       适合单体或少量服务节点的部署场景。</li>
 *   <li>密钥与过期时间通过 {@code @Value} 注入，支持 application.yml 配置覆盖，
 *       同时提供安全的默认值以降低开发环境配置门槛。</li>
 *   <li>将 userId（UUID）作为 JWT Subject，username 和 role 作为自定义 Claim，
 *       实现轻量级身份自包含，无需查库即可完成鉴权。</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    /** HMAC-SHA 签名密钥，由配置的 secret 字符串派生 */
    private final SecretKey key;

    /** 令牌有效期（毫秒），默认 24 小时 */
    private final long expirationMs;

    /**
     * 构造函数 — 初始化签名密钥与过期时间。
     *
     * @param secret       HMAC 签名密钥的原始字符串（至少 256 位/32 字节才能满足 HS256 最低要求）
     * @param expirationMs 令牌有效期，单位毫秒；默认值 86400000 = 24 小时
     */
    public JwtTokenProvider(
            @Value("${app.jwt.secret:AppleHealthPlusJwtSecretKey2026VeryLongSecureString!!}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {
        // 将密钥字符串转为字节数组，生成符合 HMAC-SHA 算法要求的 SecretKey
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * 生成 JWT 令牌。
     *
     * <p>令牌结构：</p>
     * <ul>
     *   <li>sub (subject) — 用户 ID（UUID 字符串）</li>
     *   <li>username — 用户名（自定义 Claim）</li>
     *   <li>role — 角色标识（自定义 Claim）</li>
     *   <li>iat (issued at) — 签发时间</li>
     *   <li>exp (expiration) — 过期时间</li>
     * </ul>
     *
     * @param userId   用户唯一标识
     * @param username 用户名
     * @param role     用户角色（如 USER、ADMIN）
     * @return 签名后的 JWT 令牌字符串
     */
    public String generateToken(UUID userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())                          // 用户 ID 作为主题
                .claim("username", username)                         // 自定义 Claim：用户名
                .claim("role", role)                                 // 自定义 Claim：角色
                .issuedAt(now)                                       // 签发时间
                .expiration(new Date(now.getTime() + expirationMs))  // 签发时间 + 有效期 = 过期时间
                .signWith(key)                                       // 使用 HMAC 密钥签名
                .compact();
    }

    /**
     * 验证并解析 JWT 令牌。
     *
     * <p>验证内容包括：签名有效性、令牌是否过期、格式是否正确。</p>
     *
     * @param token JWT 令牌字符串
     * @return 解析后的 {@link Claims}，包含所有声明信息
     * @throws ExpiredJwtException     令牌已过期
     * @throws MalformedJwtException   令牌格式错误
     * @throws SignatureException      签名验证失败
     * @throws IllegalArgumentException 令牌为 null 或空
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(key)          // 设置签名验证密钥
                .build()
                .parseSignedClaims(token) // 解析并验证签名 + 有效期
                .getPayload();            // 提取 Claims 载荷
    }

    /**
     * 从 Claims 中提取用户 ID。
     *
     * @param claims 已解析的 JWT Claims
     * @return 用户 UUID
     */
    public UUID getUserId(Claims claims) {
        // JWT Subject 存储的是 UUID 字符串，需反序列化为 UUID 对象
        return UUID.fromString(claims.getSubject());
    }

    /**
     * 从 Claims 中提取用户名。
     *
     * @param claims 已解析的 JWT Claims
     * @return 用户名字符串
     */
    public String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    /**
     * 从 Claims 中提取用户角色。
     *
     * @param claims 已解析的 JWT Claims
     * @return 角色标识字符串（如 USER、ADMIN）
     */
    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
