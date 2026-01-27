DO $$
BEGIN
    IF to_regclass('public.comments') IS NOT NULL THEN
        ALTER TABLE comments
            ADD COLUMN IF NOT EXISTS parent_comment_id BIGINT;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_comments_parent_comment'
        ) THEN
            ALTER TABLE comments
                ADD CONSTRAINT fk_comments_parent_comment
                    FOREIGN KEY (parent_comment_id) REFERENCES comments(id) ON DELETE CASCADE;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_indexes
            WHERE indexname = 'idx_comments_parent_comment_id'
        ) THEN
            CREATE INDEX idx_comments_parent_comment_id ON comments(parent_comment_id);
        END IF;
    END IF;
END
$$;
