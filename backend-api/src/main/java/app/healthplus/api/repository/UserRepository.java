package app.healthplus.api.repository;

import app.healthplus.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户实体仓库。
 * <p>
 * 基于 Spring Data JPA 提供 User 实体的 CRUD 操作及自定义查询方法。
 * 用于认证流程中的用户名/邮箱查找和唯一性校验。
 * </p>
 */
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
