package app.healthplus.api.repository;

import app.healthplus.domain.ChatSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
}
