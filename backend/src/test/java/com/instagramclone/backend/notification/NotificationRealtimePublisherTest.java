package com.instagramclone.backend.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void onNotificationCountChanged_sendsWhenValid() {
        NotificationRealtimePublisher publisher = new NotificationRealtimePublisher(messagingTemplate);

        publisher.onNotificationCountChanged(new NotificationCountChangedEvent("alice", 3));

        verify(messagingTemplate).convertAndSendToUser(
                eq("alice"),
                eq("/queue/notification-count"),
                argThat(payload -> payload instanceof NotificationCountResponse response && response.getCount() == 3)
        );
    }

    @Test
    void onNotificationCountChanged_ignoresInvalid() {
        NotificationRealtimePublisher publisher = new NotificationRealtimePublisher(messagingTemplate);

        publisher.onNotificationCountChanged(new NotificationCountChangedEvent(" ", 2));

        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
