package com.instagramclone.backend.notification;

import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(Principal principal) {
        String username = principal.getName();
        return ResponseEntity.ok(notificationService.getNotificationsForUser(username));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<NotificationCountResponse> getUnreadCount(Principal principal) {
        String username = principal.getName();
        long count = notificationService.getUnreadCount(username);
        return ResponseEntity.ok(new NotificationCountResponse(count));
    }

    @PutMapping("/read")
    public ResponseEntity<Void> markAllRead(Principal principal) {
        notificationService.markAllRead(principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, Principal principal) {
        notificationService.markRead(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
