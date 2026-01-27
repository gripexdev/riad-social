package com.instagramclone.backend.message;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageAttachmentValidationServiceTest {

    @Mock
    private MessageAttachmentRepository attachmentRepository;

    private MessageAttachmentProperties properties;
    private MessageAttachmentValidationService service;

    @BeforeEach
    void setUp() {
        properties = new MessageAttachmentProperties();
        properties.setMaxFiles(2);
        properties.setMaxPendingPerUser(2);
        properties.setMaxImageBytes(1000L);
        properties.setMaxVideoBytes(2000L);
        properties.setMaxDocumentBytes(3000L);
        service = new MessageAttachmentValidationService(properties, attachmentRepository);
    }

    @Test
    void validateNewAttachments_requiresAttachments() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.validateNewAttachments("alice", null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        ResponseStatusException emptyEx = assertThrows(ResponseStatusException.class, () ->
                service.validateNewAttachments("alice", List.of())
        );
        assertEquals(HttpStatus.BAD_REQUEST, emptyEx.getStatusCode());
    }

    @Test
    void validateNewAttachments_enforcesMaxFiles() {
        properties.setMaxFiles(1);
        List<AttachmentUploadRequest> requests = List.of(baseRequest(), baseRequest());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.validateNewAttachments("alice", requests)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void validateNewAttachments_enforcesPendingLimit() {
        when(attachmentRepository.countByMessageSenderUsernameAndStatus("alice", AttachmentStatus.UPLOADING))
                .thenReturn(2L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.validateNewAttachments("alice", List.of(baseRequest()))
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    void validateAttachmentMetadata_rejectsMissingFields() {
        AttachmentUploadRequest request = new AttachmentUploadRequest();
        request.setFileName(" ");
        request.setMimeType("image/jpeg");
        request.setSizeBytes(10L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.validateAttachmentMetadata(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void validateAttachmentMetadata_rejectsUnsupportedMime() {
        AttachmentUploadRequest request = baseRequest();
        request.setMimeType("application/zip");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.validateAttachmentMetadata(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void validateAttachmentMetadata_rejectsOversize() {
        AttachmentUploadRequest request = baseRequest();
        request.setMimeType("image/jpeg");
        request.setSizeBytes(5000L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.validateAttachmentMetadata(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void validateAltText_rejectsHtmlAndLength() {
        ResponseStatusException htmlEx = assertThrows(ResponseStatusException.class, () ->
                service.validateAltText("<b>bad</b>")
        );
        assertEquals(HttpStatus.BAD_REQUEST, htmlEx.getStatusCode());

        String longText = "a".repeat(201);
        ResponseStatusException lengthEx = assertThrows(ResponseStatusException.class, () ->
                service.validateAltText(longText)
        );
        assertEquals(HttpStatus.BAD_REQUEST, lengthEx.getStatusCode());
    }

    @Test
    void resolveTypeAndMimeChecks() {
        assertEquals(AttachmentType.IMAGE, service.resolveType("image/png"));
        assertEquals(AttachmentType.VIDEO, service.resolveType("video/mp4"));
        assertEquals(AttachmentType.DOCUMENT, service.resolveType("application/pdf"));

        assertTrue(service.isMimeAllowed(AttachmentType.DOCUMENT, "application/pdf"));
        assertEquals(1000L, service.maxSizeForType(AttachmentType.IMAGE));
    }

    private AttachmentUploadRequest baseRequest() {
        AttachmentUploadRequest request = new AttachmentUploadRequest();
        request.setFileName("file.jpg");
        request.setMimeType("image/jpeg");
        request.setSizeBytes(200L);
        return request;
    }
}
