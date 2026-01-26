package com.instagramclone.backend.message;

import java.util.List;

public class CreateAttachmentUploadSessionRequest {
    private String recipientUsername;
    private String content;
    private Long expiresInSeconds;
    private List<AttachmentUploadRequest> attachments;

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public void setRecipientUsername(String recipientUsername) {
        this.recipientUsername = recipientUsername;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(Long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public List<AttachmentUploadRequest> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentUploadRequest> attachments) {
        this.attachments = attachments;
    }
}
