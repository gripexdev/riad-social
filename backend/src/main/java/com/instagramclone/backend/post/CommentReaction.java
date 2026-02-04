package com.instagramclone.backend.post;

import com.instagramclone.backend.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "comment_reactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_comment_reaction_user", columnNames = {"comment_id", "user_id"})
)
public class CommentReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 16)
    private String emoji;

    private LocalDateTime createdAt;

    public CommentReaction() {
        this.createdAt = LocalDateTime.now();
    }

    public CommentReaction(Comment comment, User user, String emoji) {
        this();
        this.comment = comment;
        this.user = user;
        this.emoji = emoji;
    }

    public Long getId() {
        return id;
    }

    public Comment getComment() {
        return comment;
    }

    public void setComment(Comment comment) {
        this.comment = comment;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
