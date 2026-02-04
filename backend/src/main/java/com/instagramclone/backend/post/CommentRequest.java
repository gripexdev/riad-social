package com.instagramclone.backend.post;

public class CommentRequest {
    private String content;
    private Long parentCommentId;

    // Getter and Setter
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(Long parentCommentId) {
        this.parentCommentId = parentCommentId;
    }
}
