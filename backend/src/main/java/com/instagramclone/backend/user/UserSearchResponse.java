package com.instagramclone.backend.user;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserSearchResponse {
    // Lightweight response model for search results.
    private String username;
    private String fullName;
    private String profilePictureUrl;
    @JsonProperty("isFollowing")
    private boolean isFollowing;

    public UserSearchResponse(String username, String fullName, String profilePictureUrl, boolean isFollowing) {
        this.username = username;
        this.fullName = fullName;
        this.profilePictureUrl = profilePictureUrl;
        this.isFollowing = isFollowing;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    @JsonProperty("isFollowing")
    public boolean getIsFollowing() {
        return isFollowing;
    }
}
