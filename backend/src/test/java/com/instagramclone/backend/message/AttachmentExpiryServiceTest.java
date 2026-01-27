package com.instagramclone.backend.message;

import com.instagramclone.backend.storage.AttachmentStorageService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentExpiryServiceTest {

    @Mock
    private MessageAttachmentRepository attachmentRepository;

    @Mock
    private AttachmentStorageService storageService;

    @Mock
    private MessageService messageService;

    private AttachmentExpiryService expiryService;

    @BeforeEach
    void setUp() {
        expiryService = new AttachmentExpiryService(
                attachmentRepository,
                storageService,
                messageService,
                new MessageAttachmentProperties()
        );
    }

    @Test
    void expireAttachments_marksExpiredAndDeletesFiles() {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setStatus(AttachmentStatus.READY);
        attachment.setStorageKey("file");
        attachment.setThumbnailKey("thumb");
        attachment.setExpiresAt(LocalDateTime.now().minusMinutes(10));
        attachment.setMessage(new Message());

        when(attachmentRepository.findByStatusInAndExpiresAtBefore(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(attachment));
        when(attachmentRepository.save(any(MessageAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        expiryService.expireAttachments();

        assertEquals(AttachmentStatus.EXPIRED, attachment.getStatus());
        verify(storageService).deletePermanent("file");
        verify(storageService).deleteThumbnail("thumb");
        verify(messageService).notifyMessageUpdated(attachment.getMessage());
    }
}
