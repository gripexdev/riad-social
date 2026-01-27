ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS parent_comment_id BIGINT;

ALTER TABLE comments
    ADD CONSTRAINT IF NOT EXISTS fk_comments_parent_comment
        FOREIGN KEY (parent_comment_id) REFERENCES comments(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_comments_parent_comment_id ON comments(parent_comment_id);
