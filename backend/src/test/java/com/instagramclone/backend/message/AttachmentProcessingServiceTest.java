package com.instagramclone.backend.message;

import com.instagramclone.backend.storage.AttachmentStorageService;
import com.instagramclone.backend.storage.StorageProperties;
import com.instagramclone.backend.storage.VirusScanResult;
import com.instagramclone.backend.storage.VirusScanService;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentProcessingServiceTest {

    @Mock
    private MessageAttachmentRepository attachmentRepository;

    @Mock
    private AttachmentStorageService storageService;

    @Mock
    private VirusScanService virusScanService;

    @Mock
    private MessageService messageService;

    private AttachmentProcessingService processingService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        processingService = new AttachmentProcessingService(
                attachmentRepository,
                storageService,
                virusScanService,
                messageService
        );
    }

    @Test
    void processAttachmentAsync_quarantinesInfectedFiles() {
        MessageAttachment attachment = baseAttachment();
        when(attachmentRepository.findById(99L)).thenReturn(Optional.of(attachment));
        when(storageService.resolvePermanentPath("file")).thenReturn(Path.of("file"));
        when(virusScanService.scan(any(Path.class))).thenReturn(new VirusScanResult(VirusScanStatus.INFECTED, "virus"));

        processingService.processAttachmentAsync(99L);

        assertEquals(AttachmentStatus.QUARANTINED, attachment.getStatus());
        verify(storageService).moveToQuarantine("file");
        verify(messageService).notifyMessageUpdated(attachment.getMessage());
    }

    @Test
    void processAttachmentAsync_marksCleanDocumentsReady() {
        MessageAttachment attachment = baseAttachment();
        attachment.setType(AttachmentType.DOCUMENT);
        when(attachmentRepository.findById(100L)).thenReturn(Optional.of(attachment));
        when(storageService.resolvePermanentPath("file")).thenReturn(Path.of("file"));
        when(virusScanService.scan(any(Path.class))).thenReturn(new VirusScanResult(VirusScanStatus.CLEAN, "clean"));

        processingService.processAttachmentAsync(100L);

        assertEquals(AttachmentStatus.READY, attachment.getStatus());
        verify(messageService).notifyMessageUpdated(attachment.getMessage());
    }

    @Test
    void processAttachmentAsync_marksFailedWhenScanFails() {
        MessageAttachment attachment = baseAttachment();
        attachment.setType(AttachmentType.DOCUMENT);
        when(attachmentRepository.findById(101L)).thenReturn(Optional.of(attachment));
        when(storageService.resolvePermanentPath("file")).thenReturn(Path.of("file"));
        when(virusScanService.scan(any(Path.class))).thenReturn(new VirusScanResult(VirusScanStatus.FAILED, "fail"));

        processingService.processAttachmentAsync(101L);

        assertEquals(AttachmentStatus.FAILED, attachment.getStatus());
        verify(storageService).deletePermanent("file");
        verify(messageService).notifyMessageUpdated(attachment.getMessage());
    }

    @Test
    void processAttachmentAsync_expiresAttachments() {
        MessageAttachment attachment = baseAttachment();
        attachment.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        when(attachmentRepository.findById(102L)).thenReturn(Optional.of(attachment));

        processingService.processAttachmentAsync(102L);

        assertEquals(AttachmentStatus.EXPIRED, attachment.getStatus());
        verify(storageService).deletePermanent("file");
        verify(messageService).notifyMessageUpdated(attachment.getMessage());
    }

    @Test
    void processAttachmentAsync_returnsWhenMissingAttachment() {
        when(attachmentRepository.findById(150L)).thenReturn(Optional.empty());

        processingService.processAttachmentAsync(150L);

        verifyNoInteractions(storageService, virusScanService, messageService);
    }

    @Test
    void processAttachmentAsync_returnsWhenNotUploading() {
        MessageAttachment attachment = baseAttachment();
        attachment.setStatus(AttachmentStatus.READY);
        when(attachmentRepository.findById(151L)).thenReturn(Optional.of(attachment));

        processingService.processAttachmentAsync(151L);

        verifyNoInteractions(storageService, virusScanService, messageService);
    }

    @Test
    void processAttachmentAsync_handlesNotificationFailure() {
        MessageAttachment attachment = baseAttachment();
        attachment.setType(AttachmentType.DOCUMENT);
        when(attachmentRepository.findById(152L)).thenReturn(Optional.of(attachment));
        when(storageService.resolvePermanentPath("file")).thenReturn(Path.of("file"));
        when(virusScanService.scan(any(Path.class))).thenReturn(new VirusScanResult(VirusScanStatus.CLEAN, "clean"));
        doThrow(new RuntimeException("fail")).when(messageService).notifyMessageUpdated(any(Message.class));

        processingService.processAttachmentAsync(152L);

        assertEquals(AttachmentStatus.READY, attachment.getStatus());
    }

    @Test
    void processAttachmentAsync_generatesThumbnailForImages() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setMessageAttachmentsLocation(tempDir.resolve("attachments").toString());
        properties.setMessageAttachmentsTempLocation(tempDir.resolve("tmp").toString());
        properties.setMessageAttachmentsQuarantineLocation(tempDir.resolve("quarantine").toString());
        properties.setMessageAttachmentsThumbnailLocation(tempDir.resolve("thumbs").toString());
        AttachmentStorageService realStorage = new AttachmentStorageService(properties);
        realStorage.init();

        AttachmentProcessingService service = new AttachmentProcessingService(
                attachmentRepository,
                realStorage,
                virusScanService,
                messageService
        );

        byte[] image = createPngBytes(32, 24);
        realStorage.storePermanent(new ByteArrayInputStream(image), "image.png");

        MessageAttachment attachment = baseAttachment();
        attachment.setStorageKey("image.png");
        attachment.setType(AttachmentType.IMAGE);
        when(attachmentRepository.findById(160L)).thenReturn(Optional.of(attachment));
        when(virusScanService.scan(any(Path.class))).thenReturn(new VirusScanResult(VirusScanStatus.SKIPPED, "skip"));

        service.processAttachmentAsync(160L);

        assertEquals(AttachmentStatus.READY, attachment.getStatus());
        assertNotNull(attachment.getThumbnailKey());
        assertTrue(attachment.getWidth() > 0);
        assertTrue(attachment.getHeight() > 0);
    }

    private MessageAttachment baseAttachment() {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setStatus(AttachmentStatus.UPLOADING);
        attachment.setType(AttachmentType.IMAGE);
        attachment.setStorageKey("file");
        attachment.setMessage(new Message());
        return attachment;
    }

    private byte[] createPngBytes(int width, int height) throws java.io.IOException {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }
}
