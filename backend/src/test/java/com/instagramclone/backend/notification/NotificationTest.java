package com.instagramclone.backend.notification;

import com.instagramclone.backend.user.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationTest {

    @Test
    void defaultConstructorInitializesCreatedAt() {
        Notification notification = new Notification();

        assertNotNull(notification.getCreatedAt());
        assertFalse(notification.isRead());
    }

    @Test
    void settersAndGettersWork() {
        User recipient = buildUser("bob");
        User actor = buildUser("alice");
        Notification notification = new Notification(recipient, actor, NotificationType.LIKE);

        notification.setPostId(4L);
        notification.setPostImageUrl("post");
        notification.setCommentId(9L);
        notification.setParentCommentId(3L);
        notification.setCommentPreview("preview");
        notification.setRead(true);
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        notification.setCreatedAt(createdAt);

        assertEquals(recipient, notification.getRecipient());
        assertEquals(actor, notification.getActor());
        assertEquals(NotificationType.LIKE, notification.getType());
        assertEquals(4L, notification.getPostId());
        assertEquals("post", notification.getPostImageUrl());
        assertEquals(9L, notification.getCommentId());
        assertEquals(3L, notification.getParentCommentId());
        assertEquals("preview", notification.getCommentPreview());
        assertEquals(createdAt, notification.getCreatedAt());
        assertTrue(notification.isRead());
    }

    private User buildUser(String username) {
        User user = new User();
        user.setUsername(username);
        return user;
    }
}
