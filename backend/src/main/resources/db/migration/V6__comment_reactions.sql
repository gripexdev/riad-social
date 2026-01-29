CREATE TABLE IF NOT EXISTS comment_reactions (
    id BIGSERIAL PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    emoji VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_comment_reactions_comment
        FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_reactions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_comment_reaction_user UNIQUE (comment_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_comment_reactions_comment_id ON comment_reactions(comment_id);
