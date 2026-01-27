package com.instagramclone.backend.message;

import com.instagramclone.backend.storage.AttachmentStorageService;
import com.instagramclone.backend.storage.StorageProperties;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void createUploadSessions_rejectsNegativeExpiry() {
        MessageAttachmentValidationService realValidation = new MessageAttachmentValidationService(properties, attachmentRepository);
        when(attachmentRepository.countByMessageSenderUsernameAndStatus("alice", AttachmentStatus.UPLOADING)).thenReturn(0L);

        MessageAttachmentService service = new MessageAttachmentService(
                messageService,
                attachmentRepository,
                uploadSessionRepository,
                realValidation,
                org.mockito.Mockito.mock(AttachmentStorageService.class),
                processingService,
                userRepository,
                properties
        );

        CreateAttachmentUploadSessionRequest uploadRequest = new CreateAttachmentUploadSessionRequest();
        uploadRequest.setRecipientUsername("bob");
        uploadRequest.setExpiresInSeconds(-1L);
        AttachmentUploadRequest attachmentRequest = new AttachmentUploadRequest();
        attachmentRequest.setFileName("doc.pdf");
        attachmentRequest.setMimeType("application/pdf");
        attachmentRequest.setSizeBytes(100L);
        uploadRequest.setAttachments(List.of(attachmentRequest));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.createUploadSessions("alice", uploadRequest)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createUploadSessions_sanitizesAltTextAndFilename() {
        MessageAttachmentValidationService realValidation = new MessageAttachmentValidationService(properties, attachmentRepository);
        when(attachmentRepository.countByMessageSenderUsernameAndStatus("alice", AttachmentStatus.UPLOADING)).thenReturn(0L);
        AttachmentStorageService storageService = org.mockito.Mockito.mock(AttachmentStorageService.class);
        when(storageService.generateStorageKey(anyString())).thenReturn("x".repeat(300));
        when(storageService.createTempKey()).thenReturn("temp-key");
        when(uploadSessionRepository.save(any(AttachmentUploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User sender = buildUser(1L, "alice");
        User recipient = buildUser(2L, "bob");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(recipient));

        ArgumentCaptor<List<MessageAttachment>> captor = ArgumentCaptor.forClass(List.class);
        when(messageService.createMessageWithAttachments(eq(sender), eq(recipient), anyString(), captor.capture()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<MessageAttachment> attachments = (List<MessageAttachment>) invocation.getArgument(3);
                    Conversation conversation = new Conversation(sender, recipient);
                    Message message = new Message(conversation, sender, recipient, (String) invocation.getArgument(2));
                    attachments.forEach(attachment -> attachment.setMessage(message));
                    message.setAttachments(attachments);
                    return message;
                });
        when(messageService.toMessageResponse(any(Message.class), eq(sender)))
                .thenReturn(new MessageResponse(1L, 1L, "alice", "bob", "hi", List.of(), LocalDateTime.now(), false, null));
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

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        CreateAttachmentUploadSessionRequest uploadRequest = new CreateAttachmentUploadSessionRequest();
        uploadRequest.setRecipientUsername("bob");
        uploadRequest.setContent("hi");
        AttachmentUploadRequest attachmentRequest = new AttachmentUploadRequest();
        attachmentRequest.setFileName("  my doc  .pdf ");
        attachmentRequest.setMimeType("application/pdf");
        attachmentRequest.setSizeBytes(100L);
        attachmentRequest.setAltText("   ");
        uploadRequest.setAttachments(List.of(attachmentRequest));

        CreateAttachmentUploadSessionResponse response = service.createUploadSessions("alice", uploadRequest);

        assertNotNull(response);
        List<MessageAttachment> attachments = captor.getValue();
        assertEquals(1, attachments.size());
        assertNull(attachments.get(0).getAltText());
        assertTrue(attachments.get(0).getStorageFilename().length() <= 255);
    }

    @Test
    void uploadChunk_writesSingleChunkAndResetsFailedStatus() throws IOException {
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

        when(uploadSessionRepository.save(any(AttachmentUploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(messageService).notifyMessageUpdated(any(Message.class));

        User user = buildUser(1L, "alice");
        MessageAttachment attachment = new MessageAttachment();
        attachment.setStatus(AttachmentStatus.FAILED);
        attachment.setMessage(new Message());

        AttachmentUploadSession session = new AttachmentUploadSession();
        session.setOwner(user);
        session.setAttachment(attachment);
        session.setTempKey(storageService.createTempKey());
        session.setExpectedBytes(4L);

        when(uploadSessionRepository.findByIdAndOwnerUsername(session.getId(), "alice"))
                .thenReturn(Optional.of(session));

        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "data".getBytes());
        UploadChunkResponse response = service.uploadChunk(session.getId(), file, null, null, "alice");

        assertEquals(1, response.getUploadedChunks());
        assertEquals(AttachmentStatus.UPLOADING, attachment.getStatus());
        assertTrue(Files.exists(storageService.resolveTempPath(session.getTempKey()).resolve("upload.bin")));
    }

    @Test
    void uploadChunk_rejectsInvalidChunkMetadata() throws IOException {
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

        when(uploadSessionRepository.findByIdAndOwnerUsername("upload", "alice"))
                .thenReturn(Optional.of(new AttachmentUploadSession()));

        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "data".getBytes());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.uploadChunk("upload", file, 5, 1, "alice")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void finalizeUpload_checksumMismatchFails() {
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

        when(attachmentRepository.save(any(MessageAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadSessionRepository.save(any(AttachmentUploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(messageService).notifyMessageUpdated(any(Message.class));

        User user = buildUser(1L, "alice");
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        String tempKey = storageService.createTempKey();
        storageService.ensureTempDirectory(tempKey);
        storageService.writeTempFile(tempKey, new ByteArrayInputStream(data));

        MessageAttachment attachment = new MessageAttachment();
        attachment.setStatus(AttachmentStatus.UPLOADING);
        attachment.setType(AttachmentType.DOCUMENT);
        attachment.setChecksum("deadbeef");
        attachment.setStorageKey("attachment.bin");
        attachment.setOriginalFilename("doc.pdf");
        attachment.setMessage(new Message());

        AttachmentUploadSession session = new AttachmentUploadSession();
        session.setAttachment(attachment);
        session.setOwner(user);
        session.setExpectedBytes(data.length);
        session.setTotalChunks(1);
        session.setTempKey(tempKey);

        when(uploadSessionRepository.findByIdAndOwnerUsername(session.getId(), "alice"))
                .thenReturn(Optional.of(session));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.finalizeUpload(session.getId(), "alice")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(AttachmentStatus.FAILED, attachment.getStatus());
    }

    @Test
    void finalizeUpload_invalidMimeDeletesPermanent() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setMessageAttachmentsLocation(tempDir.resolve("attachments").toString());
        storageProperties.setMessageAttachmentsTempLocation(tempDir.resolve("tmp").toString());
        storageProperties.setMessageAttachmentsQuarantineLocation(tempDir.resolve("quarantine").toString());
        storageProperties.setMessageAttachmentsThumbnailLocation(tempDir.resolve("thumbs").toString());
        AttachmentStorageService storageService = new AttachmentStorageService(storageProperties);
        storageService.init();

        MessageAttachmentValidationService validation = Mockito.mock(MessageAttachmentValidationService.class);
        when(validation.isMimeAllowed(any(AttachmentType.class), anyString())).thenReturn(false);

        MessageAttachmentService service = new MessageAttachmentService(
                messageService,
                attachmentRepository,
                uploadSessionRepository,
                validation,
                storageService,
                processingService,
                userRepository,
                properties
        );

        when(attachmentRepository.save(any(MessageAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadSessionRepository.save(any(AttachmentUploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(messageService).notifyMessageUpdated(any(Message.class));

        User user = buildUser(1L, "alice");
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

        AttachmentUploadSession session = new AttachmentUploadSession();
        session.setAttachment(attachment);
        session.setOwner(user);
        session.setExpectedBytes(data.length);
        session.setTotalChunks(1);
        session.setTempKey(tempKey);

        when(uploadSessionRepository.findByIdAndOwnerUsername(session.getId(), "alice"))
                .thenReturn(Optional.of(session));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.finalizeUpload(session.getId(), "alice")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(AttachmentStatus.FAILED, attachment.getStatus());
        assertFalse(Files.exists(storageService.resolvePermanentPath("attachment.bin")));
    }

    @Test
    void finalizeUpload_setsImageDimensions() throws Exception {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setMessageAttachmentsLocation(tempDir.resolve("attachments").toString());
        storageProperties.setMessageAttachmentsTempLocation(tempDir.resolve("tmp").toString());
        storageProperties.setMessageAttachmentsQuarantineLocation(tempDir.resolve("quarantine").toString());
        storageProperties.setMessageAttachmentsThumbnailLocation(tempDir.resolve("thumbs").toString());
        AttachmentStorageService storageService = new AttachmentStorageService(storageProperties);
        storageService.init();

        MessageAttachmentValidationService validation = Mockito.mock(MessageAttachmentValidationService.class);
        when(validation.isMimeAllowed(any(AttachmentType.class), anyString())).thenReturn(true);

        MessageAttachmentService service = new MessageAttachmentService(
                messageService,
                attachmentRepository,
                uploadSessionRepository,
                validation,
                storageService,
                processingService,
                userRepository,
                properties
        );

        User user = buildUser(1L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(attachmentRepository.save(any(MessageAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadSessionRepository.save(any(AttachmentUploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageService.toAttachmentResponse(any(MessageAttachment.class), eq(user)))
                .thenReturn(new MessageAttachmentResponse(1L, AttachmentType.IMAGE, "image/png", 10L, null, 10, 8, null, null, null, null, AttachmentStatus.READY, null, "image.png"));
        doNothing().when(processingService).processAttachmentAsync(any(Long.class));

        byte[] data = createPngBytes(12, 8);
        String tempKey = storageService.createTempKey();
        storageService.ensureTempDirectory(tempKey);
        storageService.writeTempFile(tempKey, new ByteArrayInputStream(data));

        MessageAttachment attachment = new MessageAttachment();
        attachment.setStatus(AttachmentStatus.UPLOADING);
        attachment.setType(AttachmentType.IMAGE);
        attachment.setStorageKey("image.bin");
        attachment.setOriginalFilename("image.png");
        attachment.setMessage(new Message());
        setAttachmentId(attachment, 1L);

        AttachmentUploadSession session = new AttachmentUploadSession();
        session.setAttachment(attachment);
        session.setOwner(user);
        session.setExpectedBytes(data.length);
        session.setTotalChunks(1);
        session.setTempKey(tempKey);

        when(uploadSessionRepository.findByIdAndOwnerUsername(session.getId(), "alice"))
                .thenReturn(Optional.of(session));

        MessageAttachmentResponse response = service.finalizeUpload(session.getId(), "alice");

        assertNotNull(response);
        assertEquals(12, attachment.getWidth());
        assertEquals(8, attachment.getHeight());
        verify(processingService).processAttachmentAsync(1L);
    }

    @Test
    void cancelUpload_marksAttachmentFailedAndCleansTemp() throws Exception {
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

        when(uploadSessionRepository.save(any(AttachmentUploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(messageService).notifyMessageUpdated(any(Message.class));

        User user = buildUser(1L, "alice");
        MessageAttachment attachment = new MessageAttachment();
        attachment.setStatus(AttachmentStatus.UPLOADING);
        attachment.setMessage(new Message());

        String tempKey = storageService.createTempKey();
        storageService.ensureTempDirectory(tempKey);
        storageService.writeTempFile(tempKey, new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));

        AttachmentUploadSession session = new AttachmentUploadSession();
        session.setAttachment(attachment);
        session.setOwner(user);
        session.setTempKey(tempKey);

        when(uploadSessionRepository.findByIdAndOwnerUsername(session.getId(), "alice"))
                .thenReturn(Optional.of(session));

        service.cancelUpload(session.getId(), "alice");

        assertEquals(AttachmentStatus.FAILED, attachment.getStatus());
        assertFalse(Files.exists(storageService.resolveTempPath(tempKey)));
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

    private byte[] createPngBytes(int width, int height) throws IOException {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }
}
