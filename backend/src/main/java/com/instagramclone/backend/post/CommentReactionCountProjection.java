package com.instagramclone.backend.post;

public interface CommentReactionCountProjection {
    Long getCommentId();
    String getEmoji();
    long getCount();
}
