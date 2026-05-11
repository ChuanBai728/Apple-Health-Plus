package app.healthplus.api.service;

import app.healthplus.api.dto.AuthDtos.*;
import app.healthplus.api.repository.UserRepository;
import app.healthplus.api.security.JwtTokenProvider;
import app.healthplus.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户认证服务 — 处理注册与登录的核心业务逻辑。
 *
 * <p>职责：</p>
 * <ul>
 *   <li><b>注册</b>：校验用户名/邮箱唯一性 -> BCrypt 加密密码 -> 持久化用户 -> 签发 JWT。</li>
 *   <li><b>登录</b>：根据用户名查找用户 -> BCrypt 校验密码 -> 检查账户状态 -> 签发 JWT。</li>
 * </ul>
 *
 * <h3>设计思路</h3>
 * <ul>
 *   <li>注册方法使用 {@code @Transactional} 确保"唯一性校验 + 插入"的原子性，
 *       避免并发场景下出现重复用户。</li>
 *   <li>登录方法不加事务注解 — 纯读操作 + JWT 签发，无需事务保护。</li>
 *   <li>注册和登录失败均抛出 {@link IllegalArgumentException}，
 *       由全局异常处理器统一返回 400 响应，避免泄露具体的失败原因给攻击者
 *       （登录时统一返回 "Invalid credentials"）。</li>
 * </ul>
 */
@Service
public class AuthService {

    /** 用户数据访问层 */
    private final UserRepository userRepo;

    /** BCrypt 密码编码器，用于加密和校验密码 */
    private final PasswordEncoder encoder;

    /** JWT 令牌提供者，用于签发登录/注册后的访问令牌 */
    private final JwtTokenProvider jwtProvider;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtTokenProvider jwtProvider) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtProvider = jwtProvider;
    }

    /**
     * 用户注册。
     *
     * <p>流程：唯一性校验 -> 密码加密 -> 持久化 -> 签发 JWT。</p>
     *
     * @param req 注册请求 DTO，包含 username、email、password
     * @return 认证响应 DTO，包含 JWT 令牌、用户 ID、用户名、角色
     * @throws IllegalArgumentException 用户名或邮箱已存在时抛出
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        // 校验用户名唯一性 — 数据库层面也有唯一约束，此处提前拦截以提供更友好的错误信息
        if (userRepo.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Username already exists");
        }
        // 校验邮箱唯一性
        if (userRepo.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already exists");
        }
        // BCrypt 加密密码 — 每次调用都会生成随机盐，即使相同密码也会产生不同哈希值
        User user = new User(req.username(), req.email(), encoder.encode(req.password()));
        user = userRepo.save(user);
        // 注册成功后立即签发 JWT，用户无需再调用登录接口
        String token = jwtProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, "Bearer", user.getId().toString(), user.getUsername(), user.getRole());
    }

    /**
     * 用户登录。
     *
     * <p>流程：查找用户 -> 密码校验 -> 账户状态检查 -> 签发 JWT。</p>
     *
     * @param req 登录请求 DTO，包含 username、password
     * @return 认证响应 DTO，包含 JWT 令牌、用户 ID、用户名、角色
     * @throws IllegalArgumentException 用户名不存在、密码错误或账户被禁用时抛出
     */
    public AuthResponse login(LoginRequest req) {
        // 根据用户名查找用户 — 不存在则抛出异常
        User user = userRepo.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        // 使用 BCrypt 校验明文密码与数据库中存储的哈希值是否匹配
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        // 检查账户是否被管理员禁用
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account disabled");
        }
        // 校验通过，签发 JWT 令牌
        String token = jwtProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, "Bearer", user.getId().toString(), user.getUsername(), user.getRole());
    }
}
