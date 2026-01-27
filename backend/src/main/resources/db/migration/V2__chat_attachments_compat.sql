ALTER TABLE message_attachments
    ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);

ALTER TABLE message_attachments
    ADD COLUMN IF NOT EXISTS storage_filename VARCHAR(255);

UPDATE message_attachments
SET public_id = COALESCE(public_id, LEFT('legacy-' || id::text, 36));

UPDATE message_attachments
SET storage_filename = COALESCE(
    storage_filename,
    LEFT(COALESCE(storage_key, original_filename, 'attachment-' || id::text), 255)
);

ALTER TABLE message_attachments
    ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE message_attachments
    ALTER COLUMN storage_filename SET NOT NULL;
