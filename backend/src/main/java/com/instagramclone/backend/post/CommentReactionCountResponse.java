package com.instagramclone.backend.post;

public class CommentReactionCountResponse {
    private final String emoji;
    private final long count;

    public CommentReactionCountResponse(String emoji, long count) {
        this.emoji = emoji;
        this.count = count;
    }

    public String getEmoji() {
        return emoji;
    }

    public long getCount() {
        return count;
    }
}
