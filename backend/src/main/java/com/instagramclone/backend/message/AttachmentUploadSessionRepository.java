package com.instagramclone.backend.message;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentUploadSessionRepository extends JpaRepository<AttachmentUploadSession, String> {
    Optional<AttachmentUploadSession> findByIdAndOwnerUsername(String id, String ownerUsername);
}
