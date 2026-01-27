package com.instagramclone.backend.message;

import com.instagramclone.backend.storage.AttachmentStorageService;
import com.instagramclone.backend.storage.VirusScanResult;
import com.instagramclone.backend.storage.VirusScanService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttachmentProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentProcessingService.class);

    private final MessageAttachmentRepository attachmentRepository;
    private final AttachmentStorageService storageService;
    private final VirusScanService virusScanService;
    private final MessageService messageService;

    public AttachmentProcessingService(
            MessageAttachmentRepository attachmentRepository,
            AttachmentStorageService storageService,
            VirusScanService virusScanService,
            MessageService messageService
    ) {
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
        this.virusScanService = virusScanService;
        this.messageService = messageService;
    }

    @Async
    @Transactional
    public void processAttachmentAsync(Long attachmentId) {
        MessageAttachment attachment = attachmentRepository.findById(attachmentId).orElse(null);
        if (attachment == null) {
            return;
        }
        if (attachment.getStatus() != AttachmentStatus.UPLOADING) {
            return;
        }
        if (attachment.getExpiresAt() != null && attachment.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            attachment.setStatus(AttachmentStatus.EXPIRED);
            attachmentRepository.save(attachment);
            storageService.deletePermanent(attachment.getStorageKey());
            safeNotifyMessageUpdate(attachment);
            return;
        }

        Path filePath = storageService.resolvePermanentPath(attachment.getStorageKey());
        VirusScanResult scanResult = virusScanService.scan(filePath);
        logger.info("Attachment scan result {} => {}", attachmentId, scanResult.status());
        switch (scanResult.status()) {
            case INFECTED -> {
                attachment.setStatus(AttachmentStatus.QUARANTINED);
                attachmentRepository.save(attachment);
                storageService.moveToQuarantine(attachment.getStorageKey());
                safeNotifyMessageUpdate(attachment);
                return;
            }
            case FAILED -> {
                attachment.setStatus(AttachmentStatus.FAILED);
                attachmentRepository.save(attachment);
                storageService.deletePermanent(attachment.getStorageKey());
                safeNotifyMessageUpdate(attachment);
                return;
            }
            case SKIPPED, CLEAN -> {
                // continue
            }
        }

        if (attachment.getType() == AttachmentType.IMAGE) {
            try {
                ThumbnailResult thumbnailResult = generateThumbnail(filePath, attachment.getOriginalFilename());
                if (thumbnailResult != null) {
                    attachment.setThumbnailKey(thumbnailResult.thumbnailKey());
                    attachment.setWidth(thumbnailResult.width());
                    attachment.setHeight(thumbnailResult.height());
                }
            } catch (Exception ex) {
                logger.warn("Thumbnail generation failed for attachment {}: {}", attachmentId, ex.getMessage());
            }
        }

        attachment.setStatus(AttachmentStatus.READY);
        attachmentRepository.save(attachment);
        safeNotifyMessageUpdate(attachment);
    }

    private ThumbnailResult generateThumbnail(Path filePath, String originalFilename) throws IOException {
        if (!Files.exists(filePath)) {
            return null;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Thumbnails.of(filePath.toFile())
                    .size(360, 360)
                    .outputQuality(0.85)
                    .outputFormat("jpg")
                    .toOutputStream(outputStream);
            byte[] data = outputStream.toByteArray();
            if (data.length == 0) {
                return null;
            }
            String thumbnailKey = storageService.storeThumbnail(new ByteArrayInputStream(data), "thumb-" + originalFilename);
            var bufferedImage = Thumbnails.of(new ByteArrayInputStream(data)).scale(1).asBufferedImage();
            return new ThumbnailResult(thumbnailKey, bufferedImage.getWidth(), bufferedImage.getHeight());
        }
    }

    private void safeNotifyMessageUpdate(MessageAttachment attachment) {
        try {
            messageService.notifyMessageUpdated(attachment.getMessage());
        } catch (RuntimeException ex) {
            logger.warn("Failed to notify attachment update {}: {}", attachment.getId(), ex.getMessage());
        }
    }

    private record ThumbnailResult(String thumbnailKey, int width, int height) {}
}
