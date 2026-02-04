DO $$
BEGIN
    IF to_regclass('public.notifications') IS NOT NULL THEN
        ALTER TABLE notifications
            ADD COLUMN IF NOT EXISTS comment_id BIGINT,
            ADD COLUMN IF NOT EXISTS parent_comment_id BIGINT;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_notifications_comment_id'
        ) THEN
            ALTER TABLE notifications
                ADD CONSTRAINT fk_notifications_comment_id
                    FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE SET NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_notifications_parent_comment_id'
        ) THEN
            ALTER TABLE notifications
                ADD CONSTRAINT fk_notifications_parent_comment_id
                    FOREIGN KEY (parent_comment_id) REFERENCES comments(id) ON DELETE SET NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_indexes
            WHERE indexname = 'idx_notifications_comment_id'
        ) THEN
            CREATE INDEX idx_notifications_comment_id ON notifications(comment_id);
        END IF;
    END IF;
END
$$;
