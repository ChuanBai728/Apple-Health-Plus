package app.healthplus.api.repository;

import app.healthplus.domain.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 聊天消息实体仓库。
 * <p>
 * 基于 Spring Data JPA 提供 ChatMessage 实体的 CRUD 操作。
 * 支持按会话 ID 查询消息列表，用于加载聊天历史记录。
 * </p>
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
