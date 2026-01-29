package com.instagramclone.backend.post;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    Optional<CommentReaction> findByCommentIdAndUserUsername(Long commentId, String username);

    List<CommentReaction> findByCommentIdInAndUserUsername(Collection<Long> commentIds, String username);

    @Query("""
            select cr.comment.id as commentId, cr.emoji as emoji, count(cr.id) as count
            from CommentReaction cr
            where cr.comment.id in :commentIds
            group by cr.comment.id, cr.emoji
            """)
    List<CommentReactionCountProjection> findReactionCountsByCommentIds(@Param("commentIds") Collection<Long> commentIds);

    @Query("""
            select cr.comment.id as commentId, cr.emoji as emoji, count(cr.id) as count
            from CommentReaction cr
            where cr.comment.id = :commentId
            group by cr.comment.id, cr.emoji
            """)
    List<CommentReactionCountProjection> findReactionCountsByCommentId(@Param("commentId") Long commentId);
}
