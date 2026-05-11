package app.healthplus.api.repository;

import app.healthplus.domain.ChatSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 聊天会话实体仓库。
 * <p>
 * 基于 Spring Data JPA 提供 ChatSession 实体的 CRUD 操作。
 * 当前仅使用 JpaRepository 内置方法（findById、save、delete 等），
 * 无需额外自定义查询。
 * </p>
 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
}
