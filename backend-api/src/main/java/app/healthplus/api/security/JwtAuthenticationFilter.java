package app.healthplus.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 认证过滤器 — 每个 HTTP 请求执行一次，负责从请求中提取并验证 JWT 令牌。
 *
 * <p>设计思路：</p>
 * <ul>
 *   <li>继承 {@link OncePerRequestFilter} 保证同一请求只过滤一次，避免重复解析。</li>
 *   <li>从 {@code Authorization: Bearer <token>} 头中提取 JWT；无令牌则直接放行
 *       （交由后续的授权层决定是否拒绝）。</li>
 *   <li>验证通过后，将用户 ID 作为 principal、角色作为 authority 注入
 *       {@link SecurityContextHolder}，使后续 Controller 能通过
 *       {@code @AuthenticationPrincipal} 获取当前用户。</li>
 *   <li>针对令牌过期、格式错误、签名无效分别返回 401 并携带可读错误信息，
 *       方便前端展示。</li>
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 令牌解析与验证工具 */
    private final JwtTokenProvider tokenProvider;

    /** JSON 序列化器，用于向响应体写入错误信息 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造过滤器。
     *
     * @param tokenProvider JWT 令牌提供者，注入依赖
     */
    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /**
     * 核心过滤逻辑：提取令牌 -> 验证 -> 设置安全上下文。
     *
     * @param request     当前 HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤链，调用 doFilter 继续后续处理
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. 从请求头提取 Bearer Token
        String token = extractToken(request);
        if (token == null) {
            // 无令牌 — 不设置认证信息，直接放行；
            // 若目标端点需要认证，Spring Security 后续会返回 401/403
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 2. 验证令牌签名与有效期，解析出 Claims
            Claims claims = tokenProvider.validateToken(token);

            // 3. 从 Claims 中提取用户身份信息
            UUID userId = tokenProvider.getUserId(claims);
            String username = tokenProvider.getUsername(claims);
            String role = tokenProvider.getRole(claims);

            // 4. 构建 Spring Security 认证对象并注入安全上下文
            //    - principal = userId（UUID），供 @AuthenticationPrincipal 注解使用
            //    - authorities 必须以 "ROLE_" 前缀，Spring 才能匹配 hasRole() 表达式
            var auth = new UsernamePasswordAuthenticationToken(
                    userId, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            // 将 username 存入 details，方便后续日志或审计获取
            auth.setDetails(username);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (ExpiredJwtException e) {
            // 令牌已过期 — 返回 401 + 明确提示，前端据此引导用户重新登录
            writeError(response, "Token expired", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (MalformedJwtException | SignatureException | IllegalArgumentException e) {
            // 令牌格式错误、签名不匹配或参数非法 — 统一视为无效令牌
            writeError(response, "Invalid token", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 5. 认证成功，继续执行后续过滤链
        filterChain.doFilter(request, response);
    }

    /**
     * 从 HTTP 请求头中提取 JWT 令牌。
     *
     * <p>期望格式：{@code Authorization: Bearer eyJhbGci...}</p>
     *
     * @param request HTTP 请求
     * @return 提取到的令牌字符串；若请求头缺失或格式不符则返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            // 截取 "Bearer "（7 个字符）之后的部分作为令牌
            return header.substring(7);
        }
        return null;
    }

    /**
     * 向响应体写入 JSON 格式的错误信息。
     *
     * <p>统一错误格式：{@code {"error":"Unauthorized","message":"...","status":401}}</p>
     *
     * @param response HTTP 响应
     * @param message  错误描述信息
     * @param status   HTTP 状态码
     * @throws IOException 写入响应流时可能抛出的 IO 异常
     */
    private void writeError(HttpServletResponse response, String message, int status) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Map.of("error", "Unauthorized", "message", message, "status", status));
    }
}
