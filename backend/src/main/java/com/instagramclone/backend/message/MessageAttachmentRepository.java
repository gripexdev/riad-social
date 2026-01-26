package com.instagramclone.backend.message;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {
    List<MessageAttachment> findByStatusInAndExpiresAtBefore(Collection<AttachmentStatus> statuses, LocalDateTime timestamp);

    long countByMessageSenderUsernameAndStatus(String senderUsername, AttachmentStatus status);
}
