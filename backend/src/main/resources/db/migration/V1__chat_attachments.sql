CREATE TABLE IF NOT EXISTS message_attachments (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    mime_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(128),
    width INTEGER,
    height INTEGER,
    duration_seconds INTEGER,
    alt_text TEXT,
    storage_key VARCHAR(400) NOT NULL,
    thumbnail_key VARCHAR(400),
    original_filename VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_message_attachments_message_id ON message_attachments(message_id);
CREATE INDEX IF NOT EXISTS idx_message_attachments_status ON message_attachments(status);
CREATE INDEX IF NOT EXISTS idx_message_attachments_expires_at ON message_attachments(expires_at);

CREATE TABLE IF NOT EXISTS attachment_upload_sessions (
    id VARCHAR(36) PRIMARY KEY,
    attachment_id BIGINT NOT NULL UNIQUE REFERENCES message_attachments(id) ON DELETE CASCADE,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expected_bytes BIGINT NOT NULL,
    total_chunks INTEGER NOT NULL,
    uploaded_chunks INTEGER NOT NULL,
    temp_key VARCHAR(400) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    last_error TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_attachment_upload_sessions_owner ON attachment_upload_sessions(owner_id);
