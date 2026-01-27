package com.instagramclone.backend.message;

import com.instagramclone.backend.user.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageModelsTest {

    @Test
    void conversationResponseExposesFields() {
        LocalDateTime now = LocalDateTime.now();
        ConversationResponse response = new ConversationResponse(
                1L,
                "bob",
                "Bob",
                "avatar",
                "Hello",
                now,
                "alice",
                2
        );

        assertEquals(1L, response.getId());
        assertEquals("bob", response.getParticipantUsername());
        assertEquals("Bob", response.getParticipantFullName());
        assertEquals("avatar", response.getParticipantProfilePictureUrl());
        assertEquals("Hello", response.getLastMessagePreview());
        assertEquals(now, response.getLastMessageAt());
        assertEquals("alice", response.getLastMessageSenderUsername());
        assertEquals(2, response.getUnreadCount());
    }

    @Test
    void requestAndResponseDtosExposeFields() {
        SendMessageRequest sendRequest = new SendMessageRequest();
        sendRequest.setRecipientUsername("bob");
        sendRequest.setContent("hi");

        assertEquals("bob", sendRequest.getRecipientUsername());
        assertEquals("hi", sendRequest.getContent());

        TypingEventRequest typingRequest = new TypingEventRequest();
        typingRequest.setConversationId(2L);
        typingRequest.setTyping(true);

        assertEquals(2L, typingRequest.getConversationId());
        assertTrue(typingRequest.isTyping());

        TypingEventResponse typingResponse = new TypingEventResponse(3L, "alice", false);
        assertEquals(3L, typingResponse.getConversationId());
        assertEquals("alice", typingResponse.getSenderUsername());

        UploadChunkResponse uploadResponse = new UploadChunkResponse("upload", 1, 2);
        assertEquals("upload", uploadResponse.getUploadId());
        assertEquals(1, uploadResponse.getUploadedChunks());
        assertEquals(2, uploadResponse.getTotalChunks());
    }

    @Test
    void attachmentDtosExposeFields() {
        AttachmentUploadRequest uploadRequest = new AttachmentUploadRequest();
        uploadRequest.setFileName("photo.jpg");
        uploadRequest.setMimeType("image/jpeg");
        uploadRequest.setSizeBytes(100L);
        uploadRequest.setChecksum("sum");
        uploadRequest.setWidth(10);
        uploadRequest.setHeight(20);
        uploadRequest.setDurationSeconds(5);
        uploadRequest.setAltText("alt");

        assertEquals("photo.jpg", uploadRequest.getFileName());
        assertEquals("image/jpeg", uploadRequest.getMimeType());
        assertEquals(100L, uploadRequest.getSizeBytes());
        assertEquals("sum", uploadRequest.getChecksum());
        assertEquals(10, uploadRequest.getWidth());
        assertEquals(20, uploadRequest.getHeight());
        assertEquals(5, uploadRequest.getDurationSeconds());
        assertEquals("alt", uploadRequest.getAltText());

        AttachmentUploadSessionResponse sessionResponse = new AttachmentUploadSessionResponse(
                "upload",
                9L,
                "uploadUrl",
                "finalizeUrl",
                2048
        );
        assertEquals("upload", sessionResponse.getUploadId());
        assertEquals(9L, sessionResponse.getAttachmentId());
        assertEquals("uploadUrl", sessionResponse.getUploadUrl());
        assertEquals("finalizeUrl", sessionResponse.getFinalizeUrl());
        assertEquals(2048, sessionResponse.getChunkSizeBytes());

        MessageAttachmentResponse attachmentResponse = new MessageAttachmentResponse(
                1L,
                AttachmentType.IMAGE,
                "image/jpeg",
                100L,
                "sum",
                10,
                20,
                null,
                "alt",
                "url",
                "thumb",
                AttachmentStatus.READY,
                LocalDateTime.now(),
                "photo.jpg"
        );
        assertEquals(AttachmentType.IMAGE, attachmentResponse.getType());
        assertEquals("image/jpeg", attachmentResponse.getMimeType());
        assertEquals("url", attachmentResponse.getUrl());
        assertEquals("thumb", attachmentResponse.getThumbnailUrl());
        assertEquals("photo.jpg", attachmentResponse.getOriginalFilename());
        assertEquals("alt", attachmentResponse.getAltText());
    }

    @Test
    void messageEntitiesInitializeDefaults() {
        User alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        User bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");

        Conversation conversation = new Conversation(alice, bob);
        assertNotNull(conversation.getCreatedAt());

        Message message = new Message(conversation, alice, bob, "hello");
        assertNotNull(message.getCreatedAt());
        assertEquals("hello", message.getContent());
        message.setRead(true);
        assertTrue(message.isRead());

        MessageResponse response = new MessageResponse(
                1L,
                2L,
                "alice",
                "bob",
                "hello",
                List.of(),
                LocalDateTime.now(),
                true,
                LocalDateTime.now()
        );
        assertEquals("alice", response.getSenderUsername());
        assertTrue(response.getIsRead());
        assertEquals(2L, response.getConversationId());
        assertEquals("bob", response.getRecipientUsername());
    }

    @Test
    void messageAndConversationExposeSetters() {
        User alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        User bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");

        Conversation conversation = new Conversation();
        conversation.setUserOne(alice);
        conversation.setUserTwo(bob);
        conversation.setLastMessageSender(alice);
        conversation.setLastMessagePreview("preview");
        LocalDateTime now = LocalDateTime.now();
        conversation.setCreatedAt(now);
        conversation.setLastMessageAt(now);

        assertEquals("preview", conversation.getLastMessagePreview());
        assertEquals(alice, conversation.getLastMessageSender());
        assertEquals(bob, conversation.getUserTwo());

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(alice);
        message.setRecipient(bob);
        message.setContent("content");
        message.setRead(false);
        message.setReadAt(now);

        assertEquals("content", message.getContent());
        assertEquals(bob, message.getRecipient());
        assertEquals(now, message.getReadAt());

        MessageAttachment attachment = new MessageAttachment();
        attachment.setMessage(message);
        attachment.setType(AttachmentType.DOCUMENT);
        attachment.setMimeType("application/pdf");
        attachment.setSizeBytes(10L);
        attachment.setChecksum("sum");
        attachment.setAltText("alt");
        attachment.setStorageKey("key");
        attachment.setStorageFilename("file.pdf");
        attachment.setPublicId("public");
        attachment.setThumbnailKey("thumb");
        attachment.setOriginalFilename("file.pdf");
        attachment.setStatus(AttachmentStatus.READY);
        attachment.setExpiresAt(now);

        assertEquals("application/pdf", attachment.getMimeType());
        assertEquals("file.pdf", attachment.getOriginalFilename());
        assertEquals(AttachmentStatus.READY, attachment.getStatus());
    }

    @Test
    void attachmentPropertiesExposeValues() {
        MessageAttachmentProperties properties = new MessageAttachmentProperties();
        properties.setMaxFiles(4);
        properties.setMaxImageBytes(1000L);
        properties.setMaxVideoBytes(2000L);
        properties.setMaxDocumentBytes(3000L);
        properties.setMaxPendingPerUser(5);
        properties.setMaxExpiryHours(12L);
        properties.setChunkSizeBytes(2048L);
        properties.setDownloadTokenTtlSeconds(900L);
        properties.setExpiryCron("0 * * * * *");

        assertEquals(4, properties.getMaxFiles());
        assertEquals(1000L, properties.getMaxImageBytes());
        assertEquals(2000L, properties.getMaxVideoBytes());
        assertEquals(3000L, properties.getMaxDocumentBytes());
        assertEquals(5, properties.getMaxPendingPerUser());
        assertEquals(12L, properties.getMaxExpiryHours());
        assertEquals(2048L, properties.getChunkSizeBytes());
        assertEquals(900L, properties.getDownloadTokenTtlSeconds());
        assertEquals("0 * * * * *", properties.getExpiryCron());
    }

    @Test
    void attachmentUploadSessionLifecycleUpdatesTimestamps() {
        AttachmentUploadSession session = new AttachmentUploadSession();
        session.onCreate();
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getUpdatedAt());

        session.onUpdate();
        assertNotNull(session.getUpdatedAt());
    }
}
