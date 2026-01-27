package com.instagramclone.backend.message;

import com.instagramclone.backend.storage.AttachmentStorageService;
import com.instagramclone.backend.storage.StorageProperties;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageAttachmentServiceTest {

    @Mock
    private MessageService messageService;

    @Mock
    private MessageAttachmentRepository attachmentRepository;

    @Mock
    private AttachmentUploadSessionRepository uploadSessionRepository;

    @Mock
    private AttachmentProcessingService processingService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageAttachmentValidationService validationService;

    @TempDir
    Path tempDir;

    private MessageAttachmentProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MessageAttachmentProperties();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void createUploadSessions_buildsSessions() {
        MessageAttachmentValidationService realValidation = new MessageAttachmentValidationService(properties, attachmentRepository);
        when(attachmentRepository.countByMessageSenderUsernameAndStatus("alice", AttachmentStatus.UPLOADING)).thenReturn(0L);
        AttachmentStorageService storageService = org.mockito.Mockito.mock(AttachmentStorageService.class);
        when(storageService.generateStorageKey(anyString())).thenReturn("storage-key");
        when(storageService.createTempKey()).thenReturn("temp-key");
        when(uploadSessionRepository.save(any(AttachmentUploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User sender = buildUser(1L, "alice");
        User recipient = buildUser(2L, "bob");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(recipient));

        when(messageService.createMessageWithAttachments(eq(sender), eq(recipient), anyString(), anyList()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<MessageAttachment> attachments = (List<MessageAttachment>) invocation.getArgument(3);
                    Conversation conversation = new Conversation(sender, recipient);
                    Message message = new Message(conversation, sender, recipient, (String) invocation.getArgument(2));
                    long idCounter = 1L;
                    for (MessageAttachment attachment : attachments) {
                        attachment.setMessage(message);
                        setAttachmentId(attachment, idCounter++);
                    }
                    message.setAttachments(attachments);
                    return message;
                });
        when(messageService.toMessageResponse(any(Message.class), eq(sender)))
                .thenReturn(new MessageResponse(1L, 1L, "alice", "bob", "hi", List.of(), java.time.LocalDateTime.now(), false, null));
        doNothing().when(messageService).notifyMessageCreated(any(Message.class));

        MessageAttachmentService service = new MessageAttachmentService(
                messageService,
                attachmentRepository,
                uploadSessionRepository,
                realValidation,
                storageService,
                processingService,
                userRepository,
                properties
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        CreateAttachmentUploadSessionRequest uploadRequest = new CreateAttachmentUploadSessionRequest();
        uploadRequest.setRecipientUsername("bob");
        uploadRequest.setContent("hi");
        AttachmentUploadRequest attachmentRequest = new AttachmentUploadRequest();
        attachmentRequest.setFileName("photo.jpg");
        attachmentRequest.setMimeType("image/jpeg");
        attachmentRequest.setSizeBytes(1024);
        uploadRequest.setAttachments(List.of(attachmentRequest));

        CreateAttachmentUploadSessionResponse response = service.createUploadSessions("alice", uploadRequest);

        assertNotNull(response);
        assertEquals(1, response.getUploads().size());
        verify(uploadSessionRepository).save(any(AttachmentUploadSession.class));
        verify(messageService).notifyMessageCreated(any(Message.class));
    }

    @Test
    void finalizeUpload_storesAndValidatesAttachment() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setMessageAttachmentsLocation(tempDir.resolve("attachments").toString());
        storageProperties.setMessageAttachmentsTempLocation(tempDir.resolve("tmp").toString());
        storageProperties.setMessageAttachmentsQuarantineLocation(tempDir.resolve("quarantine").toString());
        storageProperties.setMessageAttachmentsThumbnailLocation(tempDir.resolve("thumbs").toString());
        AttachmentStorageService storageService = new AttachmentStorageService(storageProperties);
        storageService.init();

        MessageAttachmentService service = new MessageAttachmentService(
                messageService,
                attachmentRepository,
                uploadSessionRepository,
                validationService,
                storageService,
                processingService,
                userRepository,
                properties
        );

        User user = buildUser(1L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(validationService.isMimeAllowed(any(AttachmentType.class), anyString())).thenReturn(true);
        when(attachmentRepository.save(any(MessageAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadSessionRepository.save(any(AttachmentUploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageService.toAttachmentResponse(any(MessageAttachment.class), eq(user)))
                .thenReturn(new MessageAttachmentResponse(1L, AttachmentType.DOCUMENT, "application/pdf", 4L, null, null, null, null, null, null, null, AttachmentStatus.UPLOADING, null, "doc.pdf"));
        doNothing().when(processingService).processAttachmentAsync(any(Long.class));

        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        String tempKey = storageService.createTempKey();
        storageService.ensureTempDirectory(tempKey);
        storageService.writeTempFile(tempKey, new ByteArrayInputStream(data));

        MessageAttachment attachment = new MessageAttachment();
        attachment.setStatus(AttachmentStatus.UPLOADING);
        attachment.setType(AttachmentType.DOCUMENT);
        attachment.setStorageKey("attachment.bin");
        attachment.setOriginalFilename("doc.pdf");
        attachment.setMessage(new Message());
        setAttachmentId(attachment, 1L);

        AttachmentUploadSession session = new AttachmentUploadSession();
        session.setAttachment(attachment);
        session.setOwner(user);
        session.setExpectedBytes(data.length);
        session.setTotalChunks(1);
        session.setUploadedChunks(1);
        session.setTempKey(tempKey);

        when(uploadSessionRepository.findByIdAndOwnerUsername(session.getId(), "alice"))
                .thenReturn(Optional.of(session));

        MessageAttachmentResponse response = service.finalizeUpload(session.getId(), "alice");

        assertNotNull(response);
        assertEquals(AttachmentStatus.UPLOADING, attachment.getStatus());
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
}
