package com.instagramclone.backend.post;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CommentMapper {

    private CommentMapper() {
    }

    public static List<CommentResponse> toThreadedResponses(
            Set<Comment> comments,
            CommentReactionService.CommentReactionLookup reactionLookup
    ) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Comment>> repliesByParent = new HashMap<>();
        List<Comment> topLevel = new ArrayList<>();

        for (Comment comment : comments) {
            Comment parent = comment.getParentComment();
            if (parent == null || parent.getId() == null) {
                topLevel.add(comment);
                continue;
            }
            repliesByParent.computeIfAbsent(parent.getId(), ignored -> new ArrayList<>()).add(comment);
        }

        topLevel.sort(createdAtComparator());
        List<CommentResponse> response = new ArrayList<>();
        for (Comment comment : topLevel) {
            List<CommentResponse> replies = mapReplies(repliesByParent.get(comment.getId()));
            response.add(toResponse(comment, null, replies));
        }
        if (reactionLookup != null) {
            for (CommentResponse commentResponse : response) {
                applyReactions(commentResponse, reactionLookup);
            }
        }
        return response;
    }

    private static List<CommentResponse> mapReplies(List<Comment> replies) {
        if (replies == null || replies.isEmpty()) {
            return List.of();
        }
        replies.sort(createdAtComparator());
        List<CommentResponse> response = new ArrayList<>();
        for (Comment reply : replies) {
            Long parentId = reply.getParentComment() == null ? null : reply.getParentComment().getId();
            response.add(toResponse(reply, parentId, List.of()));
        }
        return response;
    }

    private static CommentResponse toResponse(Comment comment, Long parentId, List<CommentResponse> replies) {
        String username = comment.getUser() == null ? null : comment.getUser().getUsername();
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                username,
                comment.getCreatedAt(),
                parentId,
                replies
        );
    }

    private static void applyReactions(CommentResponse response, CommentReactionService.CommentReactionLookup lookup) {
        if (response == null || response.getId() == null || lookup == null) {
            return;
        }
        response.setReactions(lookup.reactionsByComment().getOrDefault(response.getId(), List.of()));
        response.setViewerReaction(lookup.viewerReactions().get(response.getId()));
        if (response.getReplies() != null) {
            for (CommentResponse reply : response.getReplies()) {
                applyReactions(reply, lookup);
            }
        }
    }

    private static Comparator<Comment> createdAtComparator() {
        return Comparator.comparing(Comment::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
    }
}
