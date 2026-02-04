DO $$
BEGIN
    IF to_regclass('public.notifications') IS NOT NULL THEN
        ALTER TABLE notifications
            DROP CONSTRAINT IF EXISTS notifications_type_check;

        ALTER TABLE notifications
            ADD CONSTRAINT notifications_type_check
                CHECK (type IN ('FOLLOW', 'LIKE', 'COMMENT', 'REPLY', 'MENTION'));
    END IF;
END
$$;
