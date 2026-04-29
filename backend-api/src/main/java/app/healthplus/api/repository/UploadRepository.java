package app.healthplus.api.repository;

import app.healthplus.domain.Upload;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRepository extends JpaRepository<Upload, UUID> {
}
