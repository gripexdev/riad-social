package com.instagramclone.backend.notification;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationControllerTest {

    @Test
    void getNotifications_returnsServiceResponses() {
        NotificationService notificationService = Mockito.mock(NotificationService.class);
        NotificationController controller = new NotificationController(notificationService);
        Principal principal = () -> "bob";

        NotificationResponse response = new NotificationResponse(
                1L,
                "FOLLOW",
                "alice",
                "profile",
                2L,
                "post-image",
                "preview",
                LocalDateTime.now(),
                false,
                true
        );
        when(notificationService.getNotificationsForUser("bob")).thenReturn(List.of(response));

        ResponseEntity<List<NotificationResponse>> result = controller.getNotifications(principal);

        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals("alice", result.getBody().get(0).getActorUsername());
    }

    @Test
    void getUnreadCount_wrapsResponse() {
        NotificationService notificationService = Mockito.mock(NotificationService.class);
        NotificationController controller = new NotificationController(notificationService);
        Principal principal = () -> "bob";

        when(notificationService.getUnreadCount("bob")).thenReturn(4L);

        ResponseEntity<NotificationCountResponse> result = controller.getUnreadCount(principal);

        assertNotNull(result.getBody());
        assertEquals(4L, result.getBody().getCount());
    }

    @Test
    void markAllRead_delegatesToService() {
        NotificationService notificationService = Mockito.mock(NotificationService.class);
        NotificationController controller = new NotificationController(notificationService);
        Principal principal = () -> "bob";

        ResponseEntity<Void> result = controller.markAllRead(principal);

        assertEquals(204, result.getStatusCode().value());
        verify(notificationService).markAllRead("bob");
    }

    @Test
    void markRead_delegatesToService() {
        NotificationService notificationService = Mockito.mock(NotificationService.class);
        NotificationController controller = new NotificationController(notificationService);
        Principal principal = () -> "bob";

        ResponseEntity<Void> result = controller.markRead(9L, principal);

        assertEquals(204, result.getStatusCode().value());
        verify(notificationService).markRead("bob", 9L);
    }
}
