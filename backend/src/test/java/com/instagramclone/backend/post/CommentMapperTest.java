package com.instagramclone.backend.post;

import com.instagramclone.backend.user.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommentMapperTest {

    @Test
    void toThreadedResponses_includesProfilePicture() {
        User user = new User();
        user.setUsername("alice");
        user.setProfilePictureUrl("pic");

        Post post = new Post("image", "caption", user);
        post.setId(1L);

        Comment parent = new Comment("hi", user, post);
        parent.setId(10L);

        Comment reply = new Comment("reply", user, post, parent);
        reply.setId(11L);

        Set<Comment> comments = Set.of(parent, reply);

        CommentReactionService.CommentReactionLookup lookup =
                new CommentReactionService.CommentReactionLookup(Map.of(), Map.of());

        List<CommentResponse> responses = CommentMapper.toThreadedResponses(comments, lookup);

        assertEquals(1, responses.size());
        CommentResponse response = responses.get(0);
        assertEquals("pic", response.getProfilePictureUrl());
        assertNotNull(response.getReplies());
        assertEquals(1, response.getReplies().size());
        assertEquals("pic", response.getReplies().get(0).getProfilePictureUrl());
    }
}
