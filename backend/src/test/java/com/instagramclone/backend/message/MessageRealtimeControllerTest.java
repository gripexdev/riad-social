package com.instagramclone.backend.message;

import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MessageRealtimeControllerTest {

    @Mock
    private MessageService messageService;

    private MessageRealtimeController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageRealtimeController(messageService);
    }

    @Test
    void handleTypingSkipsWhenUnauthenticated() {
        controller.handleTyping(new TypingEventRequest(), null);

        verifyNoInteractions(messageService);
    }

    @Test
    void handleTypingDelegatesToService() {
        TypingEventRequest request = new TypingEventRequest();
        request.setConversationId(10L);
        request.setTyping(true);
        Principal principal = () -> "alice";

        controller.handleTyping(request, principal);

        verify(messageService).sendTypingIndicator("alice", request);
    }
}
