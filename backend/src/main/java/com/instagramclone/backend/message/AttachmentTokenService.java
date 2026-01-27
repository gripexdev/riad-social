package com.instagramclone.backend.message;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AttachmentTokenService {

    private final Key key;
    private final long ttlSeconds;

    public AttachmentTokenService(
            @Value("${jwt.secret}") String secret,
            MessageAttachmentProperties properties
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(encodeSecret(secret));
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.ttlSeconds = properties.getDownloadTokenTtlSeconds();
    }

    public String generateToken(Long attachmentId, Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .setSubject("attachment")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .claim("attachmentId", attachmentId)
                .claim("userId", userId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public AttachmentTokenPayload parseToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        Long attachmentId = claims.get("attachmentId", Long.class);
        Long userId = claims.get("userId", Long.class);
        return new AttachmentTokenPayload(attachmentId, userId);
    }

    private String encodeSecret(String secret) {
        if (secret == null) {
            return "";
        }
        // Ensure key length is enough for HS256.
        String normalized = secret.trim();
        if (normalized.length() < 32) {
            normalized = String.format("%-32s", normalized).replace(' ', '0');
        }
        return java.util.Base64.getEncoder().encodeToString(normalized.getBytes());
    }
}
