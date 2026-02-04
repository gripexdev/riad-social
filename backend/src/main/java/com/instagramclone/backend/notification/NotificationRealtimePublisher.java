package com.instagramclone.backend.notification;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onNotificationCountChanged(NotificationCountChangedEvent event) {
        if (event == null || event.username() == null || event.username().isBlank()) {
            return;
        }
        messagingTemplate.convertAndSendToUser(
                event.username(),
                "/queue/notification-count",
                new NotificationCountResponse(event.count())
        );
    }
}
