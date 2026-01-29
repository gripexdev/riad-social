package com.instagramclone.backend.notification;

import com.instagramclone.backend.post.Comment;
import com.instagramclone.backend.post.Post;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int COMMENT_PREVIEW_LIMIT = 120;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @CacheEvict(cacheNames = "notificationUnreadCount", key = "#recipient.username", condition = "#recipient != null")
    public void createFollowNotification(User actor, User recipient) {
        if (actor == null || recipient == null) {
            return;
        }
        if (actor.getUsername().equals(recipient.getUsername())) {
            return;
        }
        Notification notification = new Notification(recipient, actor, NotificationType.FOLLOW);
        notificationRepository.save(notification);
        notifyUnreadCount(recipient);
    }

    @CacheEvict(
            cacheNames = "notificationUnreadCount",
            key = "#post.user.username",
            condition = "#post != null && #post.user != null"
    )
    public void createLikeNotification(User actor, Post post) {
        if (actor == null || post == null || post.getUser() == null) {
            return;
        }
        User recipient = post.getUser();
        if (recipient.getUsername().equals(actor.getUsername())) {
            return;
        }
        Notification notification = new Notification(recipient, actor, NotificationType.LIKE);
        notification.setPostId(post.getId());
        notification.setPostImageUrl(post.getImageUrl());
        notificationRepository.save(notification);
        notifyUnreadCount(recipient);
    }

    @CacheEvict(
            cacheNames = "notificationUnreadCount",
            key = "#post.user.username",
            condition = "#post != null && #post.user != null"
    )
    public void createCommentNotification(User actor, Post post, Comment comment) {
        if (actor == null || post == null || post.getUser() == null || comment == null) {
            return;
        }
        User recipient = post.getUser();
        if (recipient.getUsername().equals(actor.getUsername())) {
            return;
        }
        Notification notification = new Notification(recipient, actor, NotificationType.COMMENT);
        notification.setPostId(post.getId());
        notification.setPostImageUrl(post.getImageUrl());
        notification.setCommentId(comment.getId());
        if (comment.getParentComment() != null) {
            notification.setParentCommentId(comment.getParentComment().getId());
        }
        notification.setCommentPreview(buildCommentPreview(comment.getContent()));
        notificationRepository.save(notification);
        notifyUnreadCount(recipient);
    }

    @CacheEvict(
            cacheNames = "notificationUnreadCount",
            key = "#recipient.username",
            condition = "#recipient != null"
    )
    public void createCommentNotification(User actor, Post post, Comment comment, User recipient) {
        if (actor == null || post == null || comment == null || recipient == null) {
            return;
        }
        if (recipient.getUsername().equals(actor.getUsername())) {
            return;
        }
        Notification notification = new Notification(recipient, actor, NotificationType.REPLY);
        notification.setPostId(post.getId());
        notification.setPostImageUrl(post.getImageUrl());
        notification.setCommentId(comment.getId());
        if (comment.getParentComment() != null) {
            notification.setParentCommentId(comment.getParentComment().getId());
        }
        notification.setCommentPreview(buildCommentPreview(comment.getContent()));
        notificationRepository.save(notification);
        notifyUnreadCount(recipient);
    }

    @CacheEvict(
            cacheNames = "notificationUnreadCount",
            key = "#recipient.username",
            condition = "#recipient != null"
    )
    public void createMentionNotification(User actor, Post post, Comment comment, User recipient) {
        if (actor == null || post == null || comment == null || recipient == null) {
            return;
        }
        if (recipient.getUsername().equals(actor.getUsername())) {
            return;
        }
        Notification notification = new Notification(recipient, actor, NotificationType.MENTION);
        notification.setPostId(post.getId());
        notification.setPostImageUrl(post.getImageUrl());
        notification.setCommentId(comment.getId());
        if (comment.getParentComment() != null) {
            notification.setParentCommentId(comment.getParentComment().getId());
        }
        notification.setCommentPreview(buildCommentPreview(comment.getContent()));
        notificationRepository.save(notification);
        notifyUnreadCount(recipient);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsForUser(String username) {
        User recipient = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        Set<String> followingUsernames = recipient.getFollowing().stream()
                .map(User::getUsername)
                .collect(Collectors.toSet());
        return notificationRepository.findByRecipientWithActor(recipient).stream()
                .map(notification -> toResponse(notification, followingUsernames))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "notificationUnreadCount", key = "#username", condition = "#username != null")
    public long getUnreadCount(String username) {
        User recipient = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        return notificationRepository.countByRecipientAndReadIsFalse(recipient);
    }

    @Transactional
    @CacheEvict(cacheNames = "notificationUnreadCount", key = "#username", condition = "#username != null")
    public void markAllRead(String username) {
        User recipient = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        notificationRepository.markAllRead(recipient);
        notifyUnreadCount(recipient);
    }

    @Transactional
    @CacheEvict(cacheNames = "notificationUnreadCount", key = "#username", condition = "#username != null")
    public void markRead(String username, Long notificationId) {
        User recipient = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        Notification notification = notificationRepository.findByIdAndRecipient(notificationId, recipient)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        notification.setRead(true);
        notificationRepository.save(notification);
        notifyUnreadCount(recipient);
    }

    private NotificationResponse toResponse(Notification notification, Set<String> followingUsernames) {
        User actor = notification.getActor();
        boolean actorFollowed = followingUsernames.contains(actor.getUsername());
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                actor.getUsername(),
                actor.getProfilePictureUrl(),
                notification.getPostId(),
                notification.getPostImageUrl(),
                notification.getCommentId(),
                notification.getParentCommentId(),
                notification.getCommentPreview(),
                notification.getCreatedAt(),
                notification.isRead(),
                actorFollowed
        );
    }

    private String buildCommentPreview(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= COMMENT_PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, COMMENT_PREVIEW_LIMIT).trim() + "...";
    }

    private void notifyUnreadCount(User recipient) {
        if (recipient == null || recipient.getUsername() == null) {
            return;
        }
        long count = notificationRepository.countByRecipientAndReadIsFalse(recipient);
        eventPublisher.publishEvent(new NotificationCountChangedEvent(recipient.getUsername(), count));
    }
}
