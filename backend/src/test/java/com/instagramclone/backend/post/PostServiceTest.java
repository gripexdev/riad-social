package com.instagramclone.backend.post;

import com.instagramclone.backend.notification.NotificationService;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import com.instagramclone.backend.user.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private NotificationService notificationService;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, userRepository, userService, commentRepository, notificationService);
    }

    @Test
    void getExplorePosts_returnsRepositoryResults() {
        Post post = new Post("image", "caption", buildUser("owner"));
        when(postRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(post));

        List<Post> result = postService.getExplorePosts();

        assertEquals(1, result.size());
    }

    @Test
    void toggleLike_addsLikeAndNotifies() {
        User owner = buildUser("owner");
        User liker = buildUser("liker");
        Post post = new Post("image", "caption", owner);
        post.setId(1L);

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("liker")).thenReturn(Optional.of(liker));
        when(postRepository.save(post)).thenReturn(post);

        Post updated = postService.toggleLike(1L, "liker");

        assertTrue(updated.getLikedBy().contains(liker));
        assertEquals(1, updated.getLikes());
        verify(notificationService).createLikeNotification(liker, post);
    }

    @Test
    void addComment_savesAndNotifies() {
        User owner = buildUser("owner");
        User commenter = buildUser("commenter");
        Post post = new Post("image", "caption", owner);
        post.setId(2L);

        when(postRepository.findById(2L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("commenter")).thenReturn(Optional.of(commenter));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Comment saved = postService.addComment(2L, "hello", "commenter", null);

        assertEquals("hello", saved.getContent());
        assertEquals(commenter, saved.getUser());
        verify(notificationService).createCommentNotification(commenter, post, saved);
    }

    @Test
    void addReply_savesAndNotifiesParent() {
        User owner = buildUser("owner");
        User commenter = buildUser("commenter");
        User parentAuthor = buildUser("parent");
        Post post = new Post("image", "caption", owner);
        post.setId(9L);

        Comment parent = new Comment("parent", parentAuthor, post);
        parent.setId(5L);

        when(postRepository.findById(9L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("commenter")).thenReturn(Optional.of(commenter));
        when(commentRepository.findById(5L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Comment saved = postService.addComment(9L, "reply", "commenter", 5L);

        assertEquals(parent, saved.getParentComment());
        verify(notificationService).createCommentNotification(commenter, post, saved, parentAuthor);
    }

    private User buildUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setProfilePictureUrl("profile");
        return user;
    }
}
