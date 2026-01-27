package com.instagramclone.backend.message;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageAttachmentValidationServiceTest {

    @Mock
    private MessageAttachmentRepository attachmentRepository;

    private MessageAttachmentProperties properties;
    private MessageAttachmentValidationService validationService;

    @BeforeEach
    void setUp() {
        properties = new MessageAttachmentProperties();
        validationService = new MessageAttachmentValidationService(properties, attachmentRepository);
    }

    @Test
    void validateAltText_rejectsHtml() {
        assertThrows(ResponseStatusException.class, () -> validationService.validateAltText("hello <b>world</b>"));
    }

    @Test
    void validateAltText_rejectsLongText() {
        String longText = "a".repeat(201);
        assertThrows(ResponseStatusException.class, () -> validationService.validateAltText(longText));
    }

    @Test
    void validateAttachmentMetadata_allowsSupportedImage() {
        AttachmentUploadRequest request = new AttachmentUploadRequest();
        request.setFileName("photo.jpg");
        request.setMimeType("image/jpeg");
        request.setSizeBytes(1024);

        assertDoesNotThrow(() -> validationService.validateAttachmentMetadata(request));
    }

    @Test
    void validateAttachmentMetadata_rejectsUnsupportedMime() {
        AttachmentUploadRequest request = new AttachmentUploadRequest();
        request.setFileName("archive.zip");
        request.setMimeType("application/zip");
        request.setSizeBytes(1024);

        assertThrows(ResponseStatusException.class, () -> validationService.validateAttachmentMetadata(request));
    }

    @Test
    void validateNewAttachments_rejectsPendingLimit() {
        AttachmentUploadRequest request = new AttachmentUploadRequest();
        request.setFileName("photo.jpg");
        request.setMimeType("image/jpeg");
        request.setSizeBytes(1024);

        when(attachmentRepository.countByMessageSenderUsernameAndStatus("alice", AttachmentStatus.UPLOADING))
                .thenReturn((long) properties.getMaxPendingPerUser());

        assertThrows(ResponseStatusException.class, () ->
                validationService.validateNewAttachments("alice", List.of(request))
        );
    }
}
