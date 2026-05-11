package app.healthplus.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 用户实体，对应数据库表 {@code users}。
 * <p>
 * 存储系统注册用户的基本信息和认证凭据。密码以 BCrypt 哈希形式存储，
 * 通过 JPA 生命周期回调自动维护创建时间和更新时间。
 * </p>
 *
 * <h3>关键字段说明</h3>
 * <ul>
 *   <li>{@code username} — 登录用户名，全局唯一</li>
 *   <li>{@code email} — 用户邮箱，全局唯一，用于找回密码等场景</li>
 *   <li>{@code passwordHash} — BCrypt 加密后的密码哈希值</li>
 *   <li>{@code role} — 用户角色，默认 {@code "USER"}，可为 {@code "ADMIN"}</li>
 *   <li>{@code enabled} — 账户是否启用，禁用后无法登录</li>
 * </ul>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    /** 登录用户名，全局唯一，最大 64 字符 */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** 用户邮箱，全局唯一，最大 128 字符 */
    @Column(nullable = false, unique = true, length = 128)
    private String email;

    /** BCrypt 加密后的密码哈希值，数据库列名为 password_hash */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** 用户角色，默认 "USER"，可选 "ADMIN" */
    @Column(nullable = false, length = 32)
    private String role = "USER";

    /** 账户启用状态，禁用后 JWT 认证将被拒绝 */
    @Column(nullable = false)
    private boolean enabled = true;

    /** 记录创建时间，由 {@link #prePersist()} 自动填充 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 记录最后更新时间，由 {@link #prePersist()} 和 {@link #preUpdate()} 自动维护 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * JPA 持久化前回调：自动生成 UUID 主键并填充创建/更新时间。
     * 仅在对应字段为 null 时赋值，避免覆盖手动设置的值。
     */
    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    /**
     * JPA 更新前回调：自动刷新 updatedAt 为当前时间。
     */
    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public User() {}

    /**
     * 带参构造方法，用于注册新用户。
     * 角色默认设为 "USER"，ID 和时间戳由 {@link #prePersist()} 自动填充。
     *
     * @param username     用户名
     * @param email        邮箱
     * @param passwordHash BCrypt 密码哈希
     */
    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = "USER";
    }

    // Getters
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(UUID id) { this.id = id; }
    public void setRole(String role) { this.role = role; }
}
