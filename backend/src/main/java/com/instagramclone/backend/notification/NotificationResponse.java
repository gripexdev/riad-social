package com.instagramclone.backend.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class NotificationResponse {
    private Long id;
    private String type;
    private String actorUsername;
    private String actorProfilePictureUrl;
    private Long postId;
    private String postImageUrl;
    private Long commentId;
    private Long parentCommentId;
    private String commentPreview;
    private LocalDateTime createdAt;
    @JsonProperty("isRead")
    private boolean isRead;
    @JsonProperty("actorFollowed")
    private boolean actorFollowed;

    public NotificationResponse(
            Long id,
            String type,
            String actorUsername,
            String actorProfilePictureUrl,
            Long postId,
            String postImageUrl,
            Long commentId,
            Long parentCommentId,
            String commentPreview,
            LocalDateTime createdAt,
            boolean isRead,
            boolean actorFollowed
    ) {
        this.id = id;
        this.type = type;
        this.actorUsername = actorUsername;
        this.actorProfilePictureUrl = actorProfilePictureUrl;
        this.postId = postId;
        this.postImageUrl = postImageUrl;
        this.commentId = commentId;
        this.parentCommentId = parentCommentId;
        this.commentPreview = commentPreview;
        this.createdAt = createdAt;
        this.isRead = isRead;
        this.actorFollowed = actorFollowed;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorProfilePictureUrl() {
        return actorProfilePictureUrl;
    }

    public Long getPostId() {
        return postId;
    }

    public String getPostImageUrl() {
        return postImageUrl;
    }

    public Long getCommentId() {
        return commentId;
    }

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public String getCommentPreview() {
        return commentPreview;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @JsonProperty("isRead")
    public boolean getIsRead() {
        return isRead;
    }

    @JsonProperty("actorFollowed")
    public boolean getActorFollowed() {
        return actorFollowed;
    }
}
