package com.instagramclone.backend.message;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private MessageService messageService;

    private MessageController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageController(messageService);
    }

    @Test
    void getConversationsDelegatesToService() {
        Principal principal = () -> "alice";
        ConversationResponse response = new ConversationResponse(
                1L,
                "bob",
                "Bob",
                null,
                "Hi",
                LocalDateTime.now(),
                "alice",
                0
        );
        when(messageService.getConversations("alice")).thenReturn(List.of(response));

        ResponseEntity<List<ConversationResponse>> result = controller.getConversations(principal);

        assertEquals(1, result.getBody().size());
        assertEquals("bob", result.getBody().get(0).getParticipantUsername());
    }

    @Test
    void getConversationsRequiresAuthentication() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                controller.getConversations(null)
        );
    }

    @Test
    void getMessagesDelegatesToService() {
        Principal principal = () -> "alice";
        MessageResponse response = new MessageResponse(
                2L,
                3L,
                "alice",
                "bob",
                "Hi",
                List.of(),
                LocalDateTime.now(),
                false,
                null
        );
        when(messageService.getMessages(3L, "alice")).thenReturn(List.of(response));

        ResponseEntity<List<MessageResponse>> result = controller.getMessages(3L, principal);

        assertEquals(1, result.getBody().size());
        assertEquals("Hi", result.getBody().get(0).getContent());
    }

    @Test
    void markConversationReadDelegatesToService() {
        Principal principal = () -> "alice";

        ResponseEntity<Void> response = controller.markConversationRead(5L, principal);

        verify(messageService).markConversationRead(5L, "alice");
        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void sendMessageDelegatesToService() {
        Principal principal = () -> "alice";
        SendMessageRequest request = new SendMessageRequest();
        request.setRecipientUsername("bob");
        request.setContent("Hello");
        MessageResponse response = new MessageResponse(
                4L,
                10L,
                "alice",
                "bob",
                "Hello",
                List.of(),
                LocalDateTime.now(),
                false,
                null
        );
        when(messageService.sendMessage("alice", request)).thenReturn(response);

        ResponseEntity<MessageResponse> result = controller.sendMessage(request, principal);

        assertEquals("Hello", result.getBody().getContent());
    }
}
