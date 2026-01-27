package com.instagramclone.backend.message;

import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceAttachmentTest {

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
    void toAttachmentResponse_buildsUrlsForReadyAttachment() {
        when(attachmentTokenService.generateToken(5L, 9L)).thenReturn("token");
        User viewer = new User();
        viewer.setId(9L);

        MessageAttachment attachment = new MessageAttachment();
        setAttachmentId(attachment, 5L);
        attachment.setType(AttachmentType.IMAGE);
        attachment.setMimeType("image/jpeg");
        attachment.setSizeBytes(100L);
        attachment.setStatus(AttachmentStatus.READY);
        attachment.setThumbnailKey("thumb-key");

        MessageAttachmentResponse response = messageService.toAttachmentResponse(attachment, viewer);

        assertNotNull(response.getUrl());
        assertNotNull(response.getThumbnailUrl());
        assertEquals(AttachmentStatus.READY, response.getStatus());
    }

    @Test
    void toAttachmentResponse_hidesUrlsWhenExpired() {
        User viewer = new User();
        viewer.setId(9L);

        MessageAttachment attachment = new MessageAttachment();
        setAttachmentId(attachment, 5L);
        attachment.setType(AttachmentType.DOCUMENT);
        attachment.setMimeType("application/pdf");
        attachment.setSizeBytes(100L);
        attachment.setStatus(AttachmentStatus.READY);
        attachment.setExpiresAt(LocalDateTime.now().minusMinutes(5));

        MessageAttachmentResponse response = messageService.toAttachmentResponse(attachment, viewer);

        assertNull(response.getUrl());
        assertEquals(AttachmentStatus.EXPIRED, response.getStatus());
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
}
