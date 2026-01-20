package com.instagramclone.backend.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class MessageResponse {
    private Long id;
    private Long conversationId;
    private String senderUsername;
    private String recipientUsername;
    private String content;
    private LocalDateTime createdAt;
    @JsonProperty("isRead")
    private boolean isRead;
    private LocalDateTime readAt;

    public MessageResponse(
            Long id,
            Long conversationId,
            String senderUsername,
            String recipientUsername,
            String content,
            LocalDateTime createdAt,
            boolean isRead,
            LocalDateTime readAt
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderUsername = senderUsername;
        this.recipientUsername = recipientUsername;
        this.content = content;
        this.createdAt = createdAt;
        this.isRead = isRead;
        this.readAt = readAt;
    }

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @JsonProperty("isRead")
    public boolean getIsRead() {
        return isRead;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}
