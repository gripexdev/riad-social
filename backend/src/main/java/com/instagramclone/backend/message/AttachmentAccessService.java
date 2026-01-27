package com.instagramclone.backend.message;

import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttachmentAccessService {

    private final UserRepository userRepository;
    private final AttachmentTokenService tokenService;

    public AttachmentAccessService(UserRepository userRepository, AttachmentTokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    public User resolveUserForAttachment(String principalUsername, String token, Long attachmentId) {
        if (principalUsername != null) {
            return userRepository.findByUsername(principalUsername)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
        }
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Attachment token required.");
        }
        AttachmentTokenPayload payload = tokenService.parseToken(token);
        if (attachmentId != null && !attachmentId.equals(payload.attachmentId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid attachment token.");
        }
        return userRepository.findById(payload.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
    }

    public void assertUserCanAccess(MessageAttachment attachment, User user) {
        if (attachment == null || user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access denied.");
        }
        Message message = attachment.getMessage();
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found.");
        }
        Long userId = user.getId();
        boolean allowed = Objects.equals(message.getSender().getId(), userId)
                || Objects.equals(message.getRecipient().getId(), userId);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }
    }
}
