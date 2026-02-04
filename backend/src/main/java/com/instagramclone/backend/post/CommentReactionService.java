package com.instagramclone.backend.post;

import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommentReactionService {
    private static final String EMOJI_HEART = "\u2764\uFE0F";
    private static final String EMOJI_LAUGH = "\uD83D\uDE02";
    private static final String EMOJI_WOW = "\uD83D\uDE2E";
    private static final String EMOJI_SAD = "\uD83D\uDE22";
    private static final String EMOJI_ANGRY = "\uD83D\uDE21";
    private static final String EMOJI_THUMBS_UP = "\uD83D\uDC4D";
    private static final Set<String> ALLOWED_EMOJIS = Set.of(
            EMOJI_HEART,
            EMOJI_LAUGH,
            EMOJI_WOW,
            EMOJI_SAD,
            EMOJI_ANGRY,
            EMOJI_THUMBS_UP
    );
private final CommentReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public CommentReactionService(
            CommentReactionRepository reactionRepository,
            CommentRepository commentRepository,
            UserRepository userRepository
    ) {
        this.reactionRepository = reactionRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CommentReactionSummaryResponse toggleReaction(Long postId, Long commentId, String emoji, String username) {
        String normalizedEmoji = normalizeEmoji(emoji);
        if (!ALLOWED_EMOJIS.contains(normalizedEmoji)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported reaction.");
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found."));
        if (comment.getPost() == null || !comment.getPost().getId().equals(postId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment does not belong to this post.");
        }
        if (comment.getParentComment() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reactions are only supported on replies.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));

        reactionRepository.findByCommentIdAndUserUsername(commentId, username)
                .ifPresentOrElse(existing -> {
                    if (normalizedEmoji.equals(existing.getEmoji())) {
                        reactionRepository.delete(existing);
                    } else {
                        existing.setEmoji(normalizedEmoji);
                        reactionRepository.save(existing);
                    }
                }, () -> reactionRepository.save(new CommentReaction(comment, user, normalizedEmoji)));

        return buildSummary(commentId, username);
    }

    @Transactional(readOnly = true)
    public CommentReactionSummaryResponse buildSummary(Long commentId, String username) {
        List<CommentReactionCountResponse> reactions = mapCounts(reactionRepository.findReactionCountsByCommentId(commentId));
        String viewerReaction = null;
        if (username != null && !username.isBlank()) {
            viewerReaction = reactionRepository.findByCommentIdAndUserUsername(commentId, username)
                    .map(CommentReaction::getEmoji)
                    .orElse(null);
        }
        return new CommentReactionSummaryResponse(commentId, reactions, viewerReaction);
    }

    @Transactional(readOnly = true)
    public CommentReactionLookup buildLookup(Set<Comment> comments, String username) {
        if (comments == null || comments.isEmpty()) {
            return new CommentReactionLookup(Map.of(), Map.of());
        }
        Set<Long> commentIds = new HashSet<>();
        for (Comment comment : comments) {
            if (comment != null && comment.getId() != null) {
                commentIds.add(comment.getId());
            }
        }
        if (commentIds.isEmpty()) {
            return new CommentReactionLookup(Map.of(), Map.of());
        }
        Map<Long, List<CommentReactionCountResponse>> reactionsByComment = new HashMap<>();
        for (CommentReactionCountProjection projection : reactionRepository.findReactionCountsByCommentIds(commentIds)) {
            reactionsByComment
                    .computeIfAbsent(projection.getCommentId(), ignored -> new ArrayList<>())
                    .add(new CommentReactionCountResponse(projection.getEmoji(), projection.getCount()));
        }
        Map<Long, String> viewerReactions = new HashMap<>();
        if (username != null && !username.isBlank()) {
            for (CommentReaction reaction : reactionRepository.findByCommentIdInAndUserUsername(commentIds, username)) {
                if (reaction.getComment() != null && reaction.getComment().getId() != null) {
                    viewerReactions.put(reaction.getComment().getId(), reaction.getEmoji());
                }
            }
        }
        return new CommentReactionLookup(reactionsByComment, viewerReactions);
    }

    public void attachReactions(List<CommentResponse> responses, CommentReactionLookup lookup) {
        if (responses == null || responses.isEmpty() || lookup == null) {
            return;
        }
        for (CommentResponse response : responses) {
            applyReaction(response, lookup);
        }
    }

    private void applyReaction(CommentResponse response, CommentReactionLookup lookup) {
        if (response == null || response.getId() == null) {
            return;
        }
        response.setReactions(lookup.reactionsByComment().getOrDefault(response.getId(), List.of()));
        response.setViewerReaction(lookup.viewerReactions().get(response.getId()));
        if (response.getReplies() != null) {
            for (CommentResponse reply : response.getReplies()) {
                applyReaction(reply, lookup);
            }
        }
    }

    private List<CommentReactionCountResponse> mapCounts(Collection<CommentReactionCountProjection> projections) {
        if (projections == null || projections.isEmpty()) {
            return List.of();
        }
        List<CommentReactionCountResponse> results = new ArrayList<>();
        for (CommentReactionCountProjection projection : projections) {
            results.add(new CommentReactionCountResponse(projection.getEmoji(), projection.getCount()));
        }
        return results;
    }

    private String normalizeEmoji(String emoji) {
        if (emoji == null) {
            return "";
        }
        return emoji.trim();
    }

    public record CommentReactionLookup(
            Map<Long, List<CommentReactionCountResponse>> reactionsByComment,
            Map<Long, String> viewerReactions
    ) {
    }
}
