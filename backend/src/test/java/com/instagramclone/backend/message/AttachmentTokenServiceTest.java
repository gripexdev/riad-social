package com.instagramclone.backend.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentTokenServiceTest {

    @Test
    void generateAndParseToken_roundTripsIds() {
        MessageAttachmentProperties properties = new MessageAttachmentProperties();
        properties.setDownloadTokenTtlSeconds(600);
        AttachmentTokenService tokenService = new AttachmentTokenService("test-secret-attachment-token", properties);

        String token = tokenService.generateToken(44L, 7L);
        AttachmentTokenPayload payload = tokenService.parseToken(token);

        assertEquals(44L, payload.attachmentId());
        assertEquals(7L, payload.userId());
    }
}
