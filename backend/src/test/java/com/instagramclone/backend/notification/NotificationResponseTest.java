package com.instagramclone.backend.notification;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationResponseTest {

    @Test
    void gettersExposeValues() {
        LocalDateTime createdAt = LocalDateTime.now();
        NotificationResponse response = new NotificationResponse(
                7L,
                "FOLLOW",
                "alice",
                "profile",
                15L,
                "post-image",
                "preview",
                createdAt,
                true,
                true
        );

        assertEquals(7L, response.getId());
        assertEquals("FOLLOW", response.getType());
        assertEquals("alice", response.getActorUsername());
        assertEquals("profile", response.getActorProfilePictureUrl());
        assertEquals(15L, response.getPostId());
        assertEquals("post-image", response.getPostImageUrl());
        assertEquals("preview", response.getCommentPreview());
        assertEquals(createdAt, response.getCreatedAt());
        assertTrue(response.getIsRead());
        assertTrue(response.getActorFollowed());
    }
}
