package com.instagramclone.backend.post;

import com.instagramclone.backend.storage.FileSystemStorageService;
import com.instagramclone.backend.user.User;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    private PostService postService;

    @Mock
    private FileSystemStorageService storageService;

    @Mock
    private CommentReactionService reactionService;

    @Test
    void getExplorePosts_mapsPostResponse() {
        PostController controller = new PostController(postService, storageService, reactionService);
        Principal principal = () -> "viewer";

        User owner = new User();
        owner.setUsername("owner");
        owner.setProfilePictureUrl("owner-pic");

        User liker = new User();
        liker.setUsername("viewer");

        Post post = new Post("image", "caption", owner);
        post.setId(3L);
        post.getLikedBy().add(liker);

        Comment comment = new Comment("hi", liker, post);
        comment.setId(7L);
        post.getComments().add(comment);

        Comment reply = new Comment("reply", owner, post, comment);
        reply.setId(8L);
        post.getComments().add(reply);

        when(postService.getExplorePosts()).thenReturn(List.of(post));
        when(reactionService.buildLookup(post.getComments(), "viewer"))
                .thenReturn(new CommentReactionService.CommentReactionLookup(java.util.Map.of(), java.util.Map.of()));

        ResponseEntity<List<PostResponse>> response = controller.getExplorePosts(principal);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        PostResponse postResponse = response.getBody().get(0);
        assertTrue(postResponse.isLikedByCurrentUser());
        assertEquals(1, postResponse.getComments().size());
        assertEquals(1, postResponse.getComments().get(0).getReplies().size());
    }

    @Test
    void deleteReply_delegatesToService() {
        PostController controller = new PostController(postService, storageService, reactionService);
        Principal principal = () -> "alice";

        ResponseEntity<Void> response = controller.deleteReply(5L, 9L, principal);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(postService).deleteComment(5L, 9L, "alice");
    }
}
