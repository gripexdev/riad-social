package com.instagramclone.backend.post;

import java.time.LocalDateTime;
import java.util.List;

public class PostResponse {
    private Long id;
    private String imageUrl;
    private String caption;
    private String username;
    private String profilePictureUrl;
    private LocalDateTime createdAt;
    private int likesCount;
    private boolean likedByCurrentUser;
    private List<CommentResponse> comments;

    public PostResponse(Long id, String imageUrl, String caption, String username, String profilePictureUrl, LocalDateTime createdAt, int likesCount, boolean likedByCurrentUser, List<CommentResponse> comments) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.caption = caption;
        this.username = username;
        this.profilePictureUrl = profilePictureUrl;
        this.createdAt = createdAt;
        this.likesCount = likesCount;
        this.likedByCurrentUser = likedByCurrentUser;
        this.comments = comments;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getCaption() {
        return caption;
    }

    public String getUsername() {
        return username;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getLikesCount() {
        return likesCount;
    }

    public boolean isLikedByCurrentUser() {
        return likedByCurrentUser;
    }

    public List<CommentResponse> getComments() {
        return comments;
    }
}
