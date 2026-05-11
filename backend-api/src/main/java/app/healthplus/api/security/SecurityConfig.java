package app.healthplus.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 全局安全配置类。
 *
 * <p>职责：定义 HTTP 请求的认证/授权规则、禁用 CSRF、配置无状态会话策略、
 * 注册 JWT 过滤器并声明密码编码器 Bean。</p>
 *
 * <h3>设计思路</h3>
 * <ul>
 *   <li>采用 <b>JWT 无状态认证</b>，不依赖服务端 Session，适合前后端分离架构。</li>
 *   <li>通过白名单机制区分公开端点（auth、上传、演示、报告、聊天、健康检查）
 *       和受保护端点（其余 /api/v1/** 需要认证）。</li>
 *   <li>将 {@link JwtAuthenticationFilter} 插入到
 *       {@code UsernamePasswordAuthenticationFilter} 之前，确保在 Spring 默认
 *       表单认证逻辑之前完成 JWT 解析与身份设置。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** JWT 令牌提供者，用于创建认证过滤器 */
    private final JwtTokenProvider tokenProvider;

    public SecurityConfig(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /**
     * 构建核心安全过滤链。
     *
     * <p>配置要点：</p>
     * <ol>
     *   <li>CSRF 禁用 — 纯 REST API 无 Cookie 会话，无需 CSRF 保护。</li>
     *   <li>Session 策略为 STATELESS — 不创建/使用 HttpSession。</li>
     *   <li>公开端点通过 permitAll() 放行，其余 /api/v1/** 需认证。</li>
     *   <li>JWT 过滤器在 UsernamePasswordAuthenticationFilter 之前执行。</li>
     * </ol>
     *
     * @param http {@link HttpSecurity} 配置构建器
     * @return 构建完成的安全过滤链
     * @throws Exception 配置过程中可能抛出的异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 无状态 REST API 不使用 Cookie/Session，无需 CSRF 防护
            .csrf(csrf -> csrf.disable())
            // 完全禁用 HttpSession，每次请求都通过 JWT 自包含身份信息
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ---- 公开端点（无需认证） ----
                .requestMatchers("/api/v1/auth/**").permitAll()           // 注册/登录
                .requestMatchers(HttpMethod.POST, "/api/v1/uploads").permitAll()  // 文件上传入口
                .requestMatchers("/api/v1/uploads/**").permitAll()        // 上传相关子路径
                .requestMatchers(HttpMethod.POST, "/api/v1/demo").permitAll()     // 演示数据生成
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/**").permitAll() // 报告查询（只读）
                .requestMatchers("/api/v1/chat/**").permitAll()           // AI 对话接口
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll() // 健康检查探针
                // ---- 受保护端点（写操作，需要 JWT 认证） ----
                .requestMatchers("/api/v1/**").authenticated()
                // 其余未匹配的请求（如静态资源）默认放行
                .anyRequest().permitAll()
            )
            // 将 JWT 认证过滤器插入到 Spring 默认表单认证过滤器之前
            .addFilterBefore(new JwtAuthenticationFilter(tokenProvider),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 声明全局密码编码器。
     *
     * <p>使用 BCrypt 强哈希算法，每次生成的哈希值包含随机盐，
     * 有效防御彩虹表攻击。用于注册时加密密码、登录时校验密码。</p>
     *
     * @return BCrypt 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
