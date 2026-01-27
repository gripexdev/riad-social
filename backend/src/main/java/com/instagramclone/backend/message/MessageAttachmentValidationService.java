package com.instagramclone.backend.message;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MessageAttachmentValidationService {

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );
    private static final Set<String> VIDEO_MIME_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/quicktime"
    );
    private static final Set<String> DOCUMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private final MessageAttachmentProperties properties;
    private final MessageAttachmentRepository attachmentRepository;

    public MessageAttachmentValidationService(
            MessageAttachmentProperties properties,
            MessageAttachmentRepository attachmentRepository
    ) {
        this.properties = properties;
        this.attachmentRepository = attachmentRepository;
    }

    public void validateNewAttachments(String senderUsername, List<AttachmentUploadRequest> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one attachment is required.");
        }
        if (attachments.size() > properties.getMaxFiles()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You can attach up to " + properties.getMaxFiles() + " files."
            );
        }
        long pendingCount = attachmentRepository.countByMessageSenderUsernameAndStatus(senderUsername, AttachmentStatus.UPLOADING);
        if (pendingCount + attachments.size() > properties.getMaxPendingPerUser()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many pending uploads.");
        }
        for (AttachmentUploadRequest attachment : attachments) {
            validateAttachmentMetadata(attachment);
        }
    }

    public void validateAttachmentMetadata(AttachmentUploadRequest attachment) {
        if (attachment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment metadata is required.");
        }
        if (attachment.getFileName() == null || attachment.getFileName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment filename is required.");
        }
        String mimeType = normalize(attachment.getMimeType());
        if (mimeType.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment mime type is required.");
        }
        AttachmentType type = resolveType(mimeType);
        long size = attachment.getSizeBytes();
        if (size <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment size is required.");
        }
        long maxSize = maxSizeForType(type);
        if (size > maxSize) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Attachment exceeds max size of " + formatMaxSize(maxSize) + "."
            );
        }
        if (!isMimeAllowed(type, mimeType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment type is not supported.");
        }
        if (attachment.getAltText() != null) {
            validateAltText(attachment.getAltText());
        }
    }

    public AttachmentType resolveType(String mimeType) {
        String normalized = normalize(mimeType);
        if (IMAGE_MIME_TYPES.contains(normalized)) {
            return AttachmentType.IMAGE;
        }
        if (VIDEO_MIME_TYPES.contains(normalized)) {
            return AttachmentType.VIDEO;
        }
        return AttachmentType.DOCUMENT;
    }

    public boolean isMimeAllowed(AttachmentType type, String mimeType) {
        String normalized = normalize(mimeType);
        return switch (type) {
            case IMAGE -> IMAGE_MIME_TYPES.contains(normalized);
            case VIDEO -> VIDEO_MIME_TYPES.contains(normalized);
            case DOCUMENT -> DOCUMENT_MIME_TYPES.contains(normalized);
        };
    }

    public long maxSizeForType(AttachmentType type) {
        return switch (type) {
            case IMAGE -> properties.getMaxImageBytes();
            case VIDEO -> properties.getMaxVideoBytes();
            case DOCUMENT -> properties.getMaxDocumentBytes();
        };
    }

    public void validateAltText(String altText) {
        String trimmed = altText.trim();
        if (trimmed.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alt text must be 200 characters or less.");
        }
        if (trimmed.contains("<") || trimmed.contains(">")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alt text cannot contain HTML.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String formatMaxSize(long bytes) {
        long mb = Math.max(1, bytes / (1024 * 1024));
        return mb + " MB";
    }
}
