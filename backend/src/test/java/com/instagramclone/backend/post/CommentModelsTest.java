package com.instagramclone.backend.post;

import com.instagramclone.backend.user.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommentModelsTest {

    @Test
    void commentRequestAccessors() {
        CommentRequest request = new CommentRequest();
        request.setContent("hello");
        request.setParentCommentId(5L);

        assertEquals("hello", request.getContent());
        assertEquals(5L, request.getParentCommentId());
    }

    @Test
    void commentReactionRequestAccessors() {
        CommentReactionRequest request = new CommentReactionRequest();
        request.setEmoji("👍");

        assertEquals("👍", request.getEmoji());
    }

    @Test
    void commentReactionCountResponseAccessors() {
        CommentReactionCountResponse response = new CommentReactionCountResponse("🔥", 3);

        assertEquals("🔥", response.getEmoji());
        assertEquals(3, response.getCount());
    }

    @Test
    void commentReactionSummaryDefaultsToEmptyList() {
        CommentReactionSummaryResponse response = new CommentReactionSummaryResponse(10L, null, "🔥");

        assertEquals(10L, response.getCommentId());
        assertTrue(response.getReactions().isEmpty());
        assertEquals("🔥", response.getViewerReaction());
    }

    @Test
    void commentResponseDefaultsToEmptyCollections() {
        LocalDateTime now = LocalDateTime.now();
        CommentResponse response = new CommentResponse(1L, "hi", "alice", null, now, null, null);

        assertEquals(1L, response.getId());
        assertEquals("hi", response.getContent());
        assertEquals("alice", response.getUsername());
        assertEquals(now, response.getCreatedAt());
        assertTrue(response.getReplies().isEmpty());
        assertTrue(response.getReactions().isEmpty());

        response.setReactions(List.of(new CommentReactionCountResponse("👍", 2)));
        response.setViewerReaction("👍");
        assertEquals(1, response.getReactions().size());
        assertEquals("👍", response.getViewerReaction());
    }

    @Test
    void commentEntityAccessors() {
        User user = new User();
        Post post = new Post();
        Comment parent = new Comment("parent", user, post);
        Comment comment = new Comment("hello", user, post, parent);

        comment.setId(7L);
        comment.setContent("updated");
        comment.setUser(user);
        comment.setPost(post);
        comment.setParentComment(parent);

        assertEquals(7L, comment.getId());
        assertEquals("updated", comment.getContent());
        assertNotNull(comment.getCreatedAt());
        assertEquals(user, comment.getUser());
        assertEquals(post, comment.getPost());
        assertEquals(parent, comment.getParentComment());
        assertNotNull(comment.getReplies());
    }

    @Test
    void commentReactionEntityAccessors() {
        User user = new User();
        Post post = new Post();
        Comment comment = new Comment("hello", user, post);
        CommentReaction reaction = new CommentReaction(comment, user, "🔥");

        reaction.setEmoji("👍");
        reaction.setComment(comment);
        reaction.setUser(user);

        assertEquals("👍", reaction.getEmoji());
        assertEquals(comment, reaction.getComment());
        assertEquals(user, reaction.getUser());
        assertNotNull(reaction.getCreatedAt());
    }
}
