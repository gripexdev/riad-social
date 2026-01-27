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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        User user = buildUser(1L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        User resolved = accessService.resolveUserForAttachment("alice", null, 12L);

        assertEquals(user, resolved);
    }

    @Test
    void resolveUserForAttachment_usesTokenWhenNoPrincipal() {
        User user = buildUser(7L, "bob");
        when(tokenService.parseToken("token")).thenReturn(new AttachmentTokenPayload(42L, 7L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        User resolved = accessService.resolveUserForAttachment(null, "token", 42L);

        assertEquals(user, resolved);
    }

    @Test
    void resolveUserForAttachment_rejectsMismatchedToken() {
        when(tokenService.parseToken("token")).thenReturn(new AttachmentTokenPayload(1L, 2L));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                accessService.resolveUserForAttachment(null, "token", 99L)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void assertUserCanAccess_allowsParticipants() {
        User sender = buildUser(1L, "alice");
        User recipient = buildUser(2L, "bob");
        Message message = buildMessage(sender, recipient);
        MessageAttachment attachment = new MessageAttachment();
        attachment.setMessage(message);

        assertDoesNotThrow(() -> accessService.assertUserCanAccess(attachment, sender));
        assertDoesNotThrow(() -> accessService.assertUserCanAccess(attachment, recipient));
    }

    @Test
    void assertUserCanAccess_rejectsOtherUsers() {
        User sender = buildUser(1L, "alice");
        User recipient = buildUser(2L, "bob");
        User intruder = buildUser(3L, "mallory");
        Message message = buildMessage(sender, recipient);
        MessageAttachment attachment = new MessageAttachment();
        attachment.setMessage(message);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                accessService.assertUserCanAccess(attachment, intruder)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Message buildMessage(User sender, User recipient) {
        Conversation conversation = new Conversation(sender, recipient);
        Message message = new Message(conversation, sender, recipient, "hello");
        return message;
    }
}
