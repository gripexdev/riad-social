package com.instagramclone.backend.message;

public class TypingEventResponse {
    private final Long conversationId;
    private final String senderUsername;
    private final boolean typing;

    public TypingEventResponse(Long conversationId, String senderUsername, boolean typing) {
        this.conversationId = conversationId;
        this.senderUsername = senderUsername;
        this.typing = typing;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public boolean isTyping() {
        return typing;
    }
}
