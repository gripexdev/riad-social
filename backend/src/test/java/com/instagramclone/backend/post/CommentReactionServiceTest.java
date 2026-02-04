package com.instagramclone.backend.post;

import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentReactionServiceTest {

    @Mock
    private CommentReactionRepository reactionRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void toggleReaction_rejectsUnsupportedEmoji() {
        CommentReactionService service = new CommentReactionService(reactionRepository, commentRepository, userRepository);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.toggleReaction(1L, 2L, "bad", "user"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(commentRepository, never()).findById(any());
    }

    @Test
    void toggleReaction_requiresReply() {
        CommentReactionService service = new CommentReactionService(reactionRepository, commentRepository, userRepository);
        Comment reply = buildReply(null);

        when(commentRepository.findById(2L)).thenReturn(Optional.of(reply));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.toggleReaction(1L, 2L, "ðŸ‘", "user"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void toggleReaction_deletesWhenSameEmoji() {
        CommentReactionService service = new CommentReactionService(reactionRepository, commentRepository, userRepository);
        Comment reply = buildReply(1L);
        User user = new User();
        user.setUsername("user");
        CommentReaction existing = new CommentReaction(reply, user, "ðŸ‘");

        when(commentRepository.findById(2L)).thenReturn(Optional.of(reply));
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(reactionRepository.findByCommentIdAndUserUsername(2L, "user")).thenReturn(Optional.of(existing));

        service.toggleReaction(1L, 2L, "ðŸ‘", "user");

        verify(reactionRepository).delete(existing);
        verify(reactionRepository, never()).save(existing);
    }

    @Test
    void buildLookup_mapsReactionsAndViewer() {
        CommentReactionService service = new CommentReactionService(reactionRepository, commentRepository, userRepository);
        Comment reply = buildReply(1L);

        when(reactionRepository.findReactionCountsByCommentIds(Set.of(2L)))
                .thenReturn(List.of(new StubProjection(2L, "ðŸ‘", 2)));
        when(reactionRepository.findByCommentIdInAndUserUsername(Set.of(2L), "user"))
                .thenReturn(List.of(new CommentReaction(reply, new User(), "ðŸ‘")));

        CommentReactionService.CommentReactionLookup lookup = service.buildLookup(Set.of(reply), "user");

        assertEquals(1, lookup.reactionsByComment().get(2L).size());
        assertEquals("ðŸ‘", lookup.viewerReactions().get(2L));
    }

    @Test
    void buildSummary_handlesEmptyViewer() {
        CommentReactionService service = new CommentReactionService(reactionRepository, commentRepository, userRepository);

        when(reactionRepository.findReactionCountsByCommentId(2L))
                .thenReturn(List.of(new StubProjection(2L, "ðŸ˜‚", 1)));

        CommentReactionSummaryResponse summary = service.buildSummary(2L, "");

        assertNotNull(summary);
        assertNull(summary.getViewerReaction());
        assertEquals(1, summary.getReactions().size());
    }

    private Comment buildReply(Long postId) {
        User owner = new User();
        owner.setUsername("owner");
        Post post = new Post("image", "caption", owner);
        post.setId(postId);
        Comment parent = new Comment("parent", owner, post);
        parent.setId(1L);
        Comment reply = new Comment("reply", owner, post, parent);
        reply.setId(2L);
        return reply;
    }

    private static final class StubProjection implements CommentReactionCountProjection {
        private final Long commentId;
        private final String emoji;
        private final long count;

        private StubProjection(Long commentId, String emoji, long count) {
            this.commentId = commentId;
            this.emoji = emoji;
            this.count = count;
        }

        @Override
        public Long getCommentId() {
            return commentId;
        }

        @Override
        public String getEmoji() {
            return emoji;
        }

        @Override
        public long getCount() {
            return count;
        }
    }
}
