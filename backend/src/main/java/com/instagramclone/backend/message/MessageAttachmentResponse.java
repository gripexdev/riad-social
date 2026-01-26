package com.instagramclone.backend.message;

import java.time.LocalDateTime;

public class MessageAttachmentResponse {
    private Long id;
    private AttachmentType type;
    private String mimeType;
    private long sizeBytes;
    private String checksum;
    private Integer width;
    private Integer height;
    private Integer durationSeconds;
    private String altText;
    private String url;
    private String thumbnailUrl;
    private AttachmentStatus status;
    private LocalDateTime expiresAt;
    private String originalFilename;

    public MessageAttachmentResponse(
            Long id,
            AttachmentType type,
            String mimeType,
            long sizeBytes,
            String checksum,
            Integer width,
            Integer height,
            Integer durationSeconds,
            String altText,
            String url,
            String thumbnailUrl,
            AttachmentStatus status,
            LocalDateTime expiresAt,
            String originalFilename
    ) {
        this.id = id;
        this.type = type;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
        this.width = width;
        this.height = height;
        this.durationSeconds = durationSeconds;
        this.altText = altText;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.status = status;
        this.expiresAt = expiresAt;
        this.originalFilename = originalFilename;
    }

    public Long getId() {
        return id;
    }

    public AttachmentType getType() {
        return type;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public String getAltText() {
        return altText;
    }

    public String getUrl() {
        return url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public AttachmentStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }
}
