package com.instagramclone.backend.profile;

import com.instagramclone.backend.post.CommentReactionService;
import com.instagramclone.backend.post.PostService;
import com.instagramclone.backend.storage.FileSystemStorageService;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserService;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private PostService postService;

    @Mock
    private FileSystemStorageService storageService;

    @Mock
    private CommentReactionService reactionService;

    @Test
    void searchUsers_returnsEmptyWhenQueryTooShort() {
        ProfileController controller = new ProfileController(userService, postService, storageService, reactionService);
        Principal principal = () -> "alice";

        ResponseEntity<List<com.instagramclone.backend.user.UserSearchResponse>> response =
                controller.searchUsers("a", 10, principal);

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().size());
    }

    @Test
    void mentionSuggestions_returnsFollowing() {
        ProfileController controller = new ProfileController(userService, postService, storageService, reactionService);
        Principal principal = () -> "alice";

        User alice = new User();
        alice.setUsername("alice");
        User bob = new User();
        bob.setUsername("bob");
        bob.setFullName("Bob");
        alice.getFollowing().add(bob);

        when(userService.findByUsername("alice")).thenReturn(Optional.of(alice));

        ResponseEntity<List<com.instagramclone.backend.user.UserSearchResponse>> response =
                controller.mentionSuggestions(6, principal);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("bob", response.getBody().get(0).getUsername());
    }
}
