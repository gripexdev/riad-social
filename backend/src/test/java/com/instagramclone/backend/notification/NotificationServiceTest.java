package com.instagramclone.backend.notification;

import com.instagramclone.backend.post.Comment;
import com.instagramclone.backend.post.Post;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userRepository);
    }

    @Test
    void createFollowNotification_skipsSelf() {
        User user = buildUser("sam");

        notificationService.createFollowNotification(user, user);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void createFollowNotification_savesForDifferentUsers() {
        User actor = buildUser("alice");
        User recipient = buildUser("bob");

        notificationService.createFollowNotification(actor, recipient);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(NotificationType.FOLLOW, saved.getType());
        assertEquals(actor, saved.getActor());
        assertEquals(recipient, saved.getRecipient());
    }

    @Test
    void createLikeNotification_savesPostMetadata() {
        User actor = buildUser("alice");
        User recipient = buildUser("bob");
        Post post = new Post("image-url", "caption", recipient);
        post.setId(10L);

        notificationService.createLikeNotification(actor, post);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(NotificationType.LIKE, saved.getType());
        assertEquals(post.getId(), saved.getPostId());
        assertEquals(post.getImageUrl(), saved.getPostImageUrl());
    }

    @Test
    void createCommentNotification_trimsAndShortensPreview() {
        User actor = buildUser("alice");
        User recipient = buildUser("bob");
        Post post = new Post("image-url", "caption", recipient);
        post.setId(20L);
        Comment comment = new Comment("  hello    world  ", actor, post);

        notificationService.createCommentNotification(actor, post, comment);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals("hello world", saved.getCommentPreview());
    }

    @Test
    void getNotificationsForUser_mapsFollowedActors() {
        User actor = buildUser("alice");
        User recipient = buildUser("bob");
        recipient.getFollowing().add(actor);

        Notification notification = new Notification(recipient, actor, NotificationType.FOLLOW);
        notification.setPostId(99L);
        notification.setPostImageUrl("post-image");

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(recipient));
        when(notificationRepository.findByRecipientWithActor(recipient)).thenReturn(List.of(notification));

        List<NotificationResponse> responses = notificationService.getNotificationsForUser("bob");

        assertEquals(1, responses.size());
        NotificationResponse response = responses.get(0);
        assertEquals("FOLLOW", response.getType());
        assertEquals(actor.getUsername(), response.getActorUsername());
        assertEquals(notification.getPostId(), response.getPostId());
        assertTrue(response.getActorFollowed());
    }

    @Test
    void getUnreadCount_returnsRepositoryValue() {
        User recipient = buildUser("bob");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(recipient));
        when(notificationRepository.countByRecipientAndReadIsFalse(recipient)).thenReturn(3L);

        long count = notificationService.getUnreadCount("bob");

        assertEquals(3L, count);
    }

    @Test
    void markRead_updatesNotification() {
        User actor = buildUser("alice");
        User recipient = buildUser("bob");
        Notification notification = new Notification(recipient, actor, NotificationType.LIKE);
        notification.setRead(false);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(recipient));
        when(notificationRepository.findByIdAndRecipient(1L, recipient)).thenReturn(Optional.of(notification));

        notificationService.markRead("bob", 1L);

        assertTrue(notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAllRead_marksUnread() {
        User recipient = buildUser("bob");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(recipient));

        notificationService.markAllRead("bob");

        verify(notificationRepository).markAllRead(recipient);
    }

    private User buildUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setProfilePictureUrl("profile-url");
        return user;
    }
}
