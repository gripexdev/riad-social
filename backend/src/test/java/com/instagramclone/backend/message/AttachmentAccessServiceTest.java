package com.instagramclone.backend.message;

import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentAccessServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AttachmentTokenService tokenService;

    private AttachmentAccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new AttachmentAccessService(userRepository, tokenService);
    }

    @Test
    void resolveUserForAttachment_prefersPrincipal() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        User resolved = accessService.resolveUserForAttachment("alice", null, 1L);

        assertEquals(user, resolved);
    }

    @Test
    void resolveUserForAttachment_requiresTokenWhenNoPrincipal() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                accessService.resolveUserForAttachment(null, null, 1L)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void resolveUserForAttachment_rejectsTokenMismatch() {
        when(tokenService.parseToken("token")).thenReturn(new AttachmentTokenPayload(1L, 2L));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                accessService.resolveUserForAttachment(null, "token", 99L)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void assertUserCanAccess_rejectsNonParticipants() {
        User viewer = new User();
        viewer.setId(1L);
        User sender = new User();
        sender.setId(2L);
        User recipient = new User();
        recipient.setId(3L);
        Message message = new Message();
        message.setSender(sender);
        message.setRecipient(recipient);

        MessageAttachment attachment = new MessageAttachment();
        attachment.setMessage(message);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                accessService.assertUserCanAccess(attachment, viewer)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
