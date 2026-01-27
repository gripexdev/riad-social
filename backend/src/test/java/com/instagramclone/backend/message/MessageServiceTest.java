package com.instagramclone.backend.message;

import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AttachmentTokenService attachmentTokenService;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                conversationRepository,
                messageRepository,
                userRepository,
                messagingTemplate,
                attachmentTokenService,
                "http://example.com"
        );
    }

    @Test
    void getConversationsBuildsResponses() {
        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");
        Conversation conversation = new Conversation(alice, bob);
        conversation.setLastMessagePreview("Hello");
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setLastMessageSender(bob);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findByParticipant(alice)).thenReturn(List.of(conversation));
        when(messageRepository.countByConversationAndRecipientAndReadIsFalse(conversation, alice)).thenReturn(3L);

        List<ConversationResponse> responses = messageService.getConversations("alice");

        assertEquals(1, responses.size());
        assertEquals("bob", responses.get(0).getParticipantUsername());
        assertEquals(3L, responses.get(0).getUnreadCount());
    }

    @Test
    void getMessagesReturnsMessageResponses() {
        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");
        Conversation conversation = new Conversation(alice, bob);

        MessageAttachment attachment = new MessageAttachment();
        attachment.setType(AttachmentType.IMAGE);
        attachment.setStatus(AttachmentStatus.READY);
        attachment.setStorageKey("file");
        setAttachmentId(attachment, 10L);

        Message message = new Message(conversation, alice, bob, "hi");
        message.setAttachments(List.of(attachment));
        setMessageId(message, 5L);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(List.of(message));
        when(attachmentTokenService.generateToken(10L, 1L)).thenReturn("token");

        List<MessageResponse> responses = messageService.getMessages(1L, "alice");

        assertEquals(1, responses.size());
        assertEquals("hi", responses.get(0).getContent());
        assertEquals(1, responses.get(0).getAttachments().size());
    }

    @Test
    void sendMessageValidatesInput() {
        assertThrows(ResponseStatusException.class, () -> messageService.sendMessage("alice", null));

        SendMessageRequest request = new SendMessageRequest();
        assertThrows(ResponseStatusException.class, () -> messageService.sendMessage("alice", request));

        request.setRecipientUsername("alice");
        request.setContent("hi");
        assertThrows(ResponseStatusException.class, () -> messageService.sendMessage("alice", request));

        request.setRecipientUsername("bob");
        request.setContent("");
        assertThrows(ResponseStatusException.class, () -> messageService.sendMessage("alice", request));

        request.setContent("a".repeat(2001));
        assertThrows(ResponseStatusException.class, () -> messageService.sendMessage("alice", request));
    }

    @Test
    void sendMessagePersistsConversationAndNotifies() {
        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(conversationRepository.findBetweenUsers(alice, bob)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendMessageRequest request = new SendMessageRequest();
        request.setRecipientUsername("bob");
        request.setContent("hello");

        MessageResponse response = messageService.sendMessage("alice", request);

        assertEquals("hello", response.getContent());
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/messages"), any(MessageResponse.class));
    }

    @Test
    void createMessageWithAttachmentsBuildsPreviewFromAttachments() {
        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");
        Conversation conversation = new Conversation(alice, bob);
        when(conversationRepository.findBetweenUsers(alice, bob)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageAttachment attachment = new MessageAttachment();
        attachment.setType(AttachmentType.IMAGE);

        Message message = messageService.createMessageWithAttachments(alice, bob, "", List.of(attachment));

        assertNotNull(message);
        assertEquals("Photo", conversation.getLastMessagePreview());
    }

    @Test
    void markConversationReadDelegatesToRepository() {
        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");
        Conversation conversation = new Conversation(alice, bob);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        messageService.markConversationRead(1L, "alice");

        verify(messageRepository).markConversationRead(eq(conversation), eq(alice), any(LocalDateTime.class));
    }

    @Test
    void sendTypingIndicatorPublishesToRecipient() {
        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");
        Conversation conversation = new Conversation(alice, bob);

        TypingEventRequest request = new TypingEventRequest();
        request.setConversationId(1L);
        request.setTyping(true);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        messageService.sendTypingIndicator("alice", request);

        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/typing"), any(TypingEventResponse.class));
    }

    @Test
    void getMessagesRejectsNonParticipants() {
        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");
        User charlie = buildUser(3L, "charlie");
        Conversation conversation = new Conversation(alice, bob);

        when(userRepository.findByUsername("charlie")).thenReturn(Optional.of(charlie));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                messageService.getMessages(1L, "charlie")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void createMessageWithAttachments_buildsPreviewForMultipleAttachments() {
        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");
        Conversation conversation = new Conversation(alice, bob);
        when(conversationRepository.findBetweenUsers(alice, bob)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageAttachment first = new MessageAttachment();
        first.setType(AttachmentType.IMAGE);
        MessageAttachment second = new MessageAttachment();
        second.setType(AttachmentType.DOCUMENT);

        Message message = messageService.createMessageWithAttachments(alice, bob, "", List.of(first, second));

        assertNotNull(message);
        assertEquals("2 attachments", conversation.getLastMessagePreview());
    }

    @Test
    void createMessageWithAttachments_trimsLongContentPreview() {
        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");
        Conversation conversation = new Conversation(alice, bob);
        when(conversationRepository.findBetweenUsers(alice, bob)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String content = "a".repeat(200);
        messageService.createMessageWithAttachments(alice, bob, content, List.of());

        assertTrue(conversation.getLastMessagePreview().endsWith("..."));
    }

    @Test
    void toAttachmentResponse_resolvesExpiredStatusAndOmitsUrls() {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setStatus(AttachmentStatus.READY);
        attachment.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        attachment.setType(AttachmentType.DOCUMENT);
        attachment.setMimeType("application/pdf");
        attachment.setMessage(new Message());

        MessageAttachmentResponse response = messageService.toAttachmentResponse(attachment, buildUser(9L, "viewer"));

        assertEquals(AttachmentStatus.EXPIRED, response.getStatus());
        assertNull(response.getUrl());
        verify(attachmentTokenService, never()).generateToken(any(Long.class), any(Long.class));
    }

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private void setAttachmentId(MessageAttachment attachment, Long id) {
        try {
            var field = MessageAttachment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(attachment, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void setMessageId(Message message, Long id) {
        try {
            var field = Message.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(message, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
