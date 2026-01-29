package com.instagramclone.backend.post;

import java.util.Collections;
import java.util.List;

public class CommentReactionSummaryResponse {
    private final Long commentId;
    private final List<CommentReactionCountResponse> reactions;
    private final String viewerReaction;

    public CommentReactionSummaryResponse(Long commentId, List<CommentReactionCountResponse> reactions, String viewerReaction) {
        this.commentId = commentId;
        this.reactions = reactions == null ? Collections.emptyList() : reactions;
        this.viewerReaction = viewerReaction;
    }

    public Long getCommentId() {
        return commentId;
    }

    public List<CommentReactionCountResponse> getReactions() {
        return reactions;
    }

    public String getViewerReaction() {
        return viewerReaction;
    }
}
