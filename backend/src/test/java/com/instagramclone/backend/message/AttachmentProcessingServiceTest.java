package com.instagramclone.backend.message;

import com.instagramclone.backend.storage.AttachmentStorageService;
import com.instagramclone.backend.storage.VirusScanResult;
import com.instagramclone.backend.storage.VirusScanService;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    private MessageAttachment baseAttachment() {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setStatus(AttachmentStatus.UPLOADING);
        attachment.setType(AttachmentType.IMAGE);
        attachment.setStorageKey("file");
        attachment.setMessage(new Message());
        return attachment;
    }
}
