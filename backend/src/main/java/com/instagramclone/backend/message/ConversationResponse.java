package com.instagramclone.backend.message;

import java.time.LocalDateTime;

public class ConversationResponse {
    private Long id;
    private String participantUsername;
    private String participantFullName;
    private String participantProfilePictureUrl;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private String lastMessageSenderUsername;
    private long unreadCount;

    public ConversationResponse(
            Long id,
            String participantUsername,
            String participantFullName,
            String participantProfilePictureUrl,
            String lastMessagePreview,
            LocalDateTime lastMessageAt,
            String lastMessageSenderUsername,
            long unreadCount
    ) {
        this.id = id;
        this.participantUsername = participantUsername;
        this.participantFullName = participantFullName;
        this.participantProfilePictureUrl = participantProfilePictureUrl;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageAt = lastMessageAt;
        this.lastMessageSenderUsername = lastMessageSenderUsername;
        this.unreadCount = unreadCount;
    }

    public Long getId() {
        return id;
    }

    public String getParticipantUsername() {
        return participantUsername;
    }

    public String getParticipantFullName() {
        return participantFullName;
    }

    public String getParticipantProfilePictureUrl() {
        return participantProfilePictureUrl;
    }

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public String getLastMessageSenderUsername() {
        return lastMessageSenderUsername;
    }

    public long getUnreadCount() {
        return unreadCount;
    }
}
