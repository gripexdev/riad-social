package com.instagramclone.backend.message;

import com.instagramclone.backend.storage.AttachmentStorageService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttachmentExpiryService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentExpiryService.class);

    private final MessageAttachmentRepository attachmentRepository;
    private final AttachmentStorageService storageService;
    private final MessageService messageService;
    private final MessageAttachmentProperties properties;

    public AttachmentExpiryService(
            MessageAttachmentRepository attachmentRepository,
            AttachmentStorageService storageService,
            MessageService messageService,
            MessageAttachmentProperties properties
    ) {
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
        this.messageService = messageService;
        this.properties = properties;
    }

    @Scheduled(cron = "${message.attachments.expiry-cron:0 */15 * * * *}")
    @Transactional
    public void expireAttachments() {
        LocalDateTime now = LocalDateTime.now();
        List<MessageAttachment> attachments = attachmentRepository.findByStatusInAndExpiresAtBefore(
                java.util.List.of(AttachmentStatus.READY, AttachmentStatus.QUARANTINED),
                now
        );
        if (attachments.isEmpty()) {
            return;
        }
        for (MessageAttachment attachment : attachments) {
            attachment.setStatus(AttachmentStatus.EXPIRED);
            attachmentRepository.save(attachment);
            try {
                storageService.deletePermanent(attachment.getStorageKey());
                if (attachment.getThumbnailKey() != null) {
                    storageService.deleteThumbnail(attachment.getThumbnailKey());
                }
            } catch (RuntimeException ex) {
                logger.warn("Failed to delete expired attachment {}: {}", attachment.getId(), ex.getMessage());
            }
            messageService.notifyMessageUpdated(attachment.getMessage());
        }
    }
}
