package com.instagramclone.backend.message;

import com.instagramclone.backend.storage.AttachmentStorageService;
import com.instagramclone.backend.user.User;
import java.io.IOException;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageAttachmentControllerTest {

    @Mock
    private MessageAttachmentService attachmentService;

    @Mock
    private MessageAttachmentRepository attachmentRepository;

    @Mock
    private AttachmentAccessService accessService;

    @Mock
    private AttachmentStorageService storageService;

    private MessageAttachmentController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageAttachmentController(
                attachmentService,
                attachmentRepository,
                accessService,
                storageService
        );
    }

    @Test
    void createUploadSessions_requiresAuthentication() {
        CreateAttachmentUploadSessionRequest request = new CreateAttachmentUploadSessionRequest();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.createUploadSessions(request, null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void createUploadSessions_returnsResponse() {
        CreateAttachmentUploadSessionRequest request = new CreateAttachmentUploadSessionRequest();
        CreateAttachmentUploadSessionResponse response = new CreateAttachmentUploadSessionResponse(null, java.util.List.of());
        when(attachmentService.createUploadSessions("alice", request)).thenReturn(response);

        ResponseEntity<CreateAttachmentUploadSessionResponse> result =
                controller.createUploadSessions(request, () -> "alice");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
    }

    @Test
    void downloadAttachment_returnsResource() throws IOException {
        MessageAttachment attachment = new MessageAttachment();
        setAttachmentId(attachment, 1L);
        attachment.setStatus(AttachmentStatus.READY);
        attachment.setStorageKey("file.bin");
        attachment.setOriginalFilename("report.pdf");
        when(attachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));
        User user = new User();
        user.setId(1L);
        when(accessService.resolveUserForAttachment("alice", "token", 1L)).thenReturn(user);
        doNothing().when(accessService).assertUserCanAccess(attachment, user);
        when(storageService.loadAsResource("file.bin")).thenReturn(new ByteArrayResource("data".getBytes()));

        ResponseEntity<?> response = controller.downloadAttachment(1L, new HttpHeaders(), "token", () -> "alice");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void downloadAttachment_rejectsExpired() throws IOException {
        MessageAttachment attachment = new MessageAttachment();
        setAttachmentId(attachment, 2L);
        attachment.setStatus(AttachmentStatus.EXPIRED);
        attachment.setStorageKey("file.bin");
        when(attachmentRepository.findById(2L)).thenReturn(Optional.of(attachment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.downloadAttachment(2L, new HttpHeaders(), null, () -> "alice")
        );

        assertEquals(HttpStatus.GONE, ex.getStatusCode());
    }

    @Test
    void downloadAttachment_rejectsQuarantined() throws IOException {
        MessageAttachment attachment = new MessageAttachment();
        setAttachmentId(attachment, 3L);
        attachment.setStatus(AttachmentStatus.QUARANTINED);
        attachment.setStorageKey("file.bin");
        when(attachmentRepository.findById(3L)).thenReturn(Optional.of(attachment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.downloadAttachment(3L, new HttpHeaders(), null, () -> "alice")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
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
