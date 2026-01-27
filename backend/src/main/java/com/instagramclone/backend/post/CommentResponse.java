package com.instagramclone.backend.post;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class CommentResponse {
    private Long id;
    private String content;
    private String username;
    private LocalDateTime createdAt;
    private Long parentId;
    private List<CommentResponse> replies;

    public CommentResponse(Long id, String content, String username, LocalDateTime createdAt, Long parentId, List<CommentResponse> replies) {
        this.id = id;
        this.content = content;
        this.username = username;
        this.createdAt = createdAt;
        this.parentId = parentId;
        this.replies = replies == null ? Collections.emptyList() : replies;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getParentId() {
        return parentId;
    }

    public List<CommentResponse> getReplies() {
        return replies;
    }
}
