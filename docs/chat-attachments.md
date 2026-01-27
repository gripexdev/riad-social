# Chat Attachments (Backend)

## Architecture overview
- **Upload sessions**: `MessageAttachmentService` creates a message with attachment placeholders (status `UPLOADING`) and a per-file `AttachmentUploadSession`.
- **Temp storage**: file data is written to `storage.message-attachments-temp-location`, chunked or single file.
- **Finalize**: chunks are assembled, size and checksum are validated, and the file is stored in `storage.message-attachments-location`.
- **MIME sniffing**: Apache Tika detects the real content type after upload.
- **Async processing**: `AttachmentProcessingService` scans with ClamAV and generates image thumbnails.
- **Expiration**: `AttachmentExpiryService` runs on a cron schedule to mark attachments `EXPIRED` and delete files.
- **Realtime updates**: the message is re-broadcast to both participants over `/user/queue/messages` after status changes.

## Data model
### `message_attachments`
Represents each file attached to a message.

Important fields:
- `id`: primary key
- `message_id`: parent message
- `type`: `IMAGE | VIDEO | DOCUMENT`
- `mime_type`: server-detected MIME type
- `size_bytes`: byte size of the final file
- `checksum`: SHA-256 checksum (optional but computed on finalize)
- `width`, `height`, `duration_seconds`: media metadata when available
- `alt_text`: image alt text
- `storage_key`: file key in permanent storage
- `thumbnail_key`: thumbnail key (images only)
- `status`: `UPLOADING | READY | FAILED | QUARANTINED | EXPIRED`
- `expires_at`: optional expiration timestamp
- `created_at`, `updated_at`

Compatibility fields (existing schema):
- `public_id`, `storage_filename` are populated to satisfy legacy constraints.

### `attachment_upload_sessions`
Tracks in-progress uploads.

Important fields:
- `id`: upload session id (UUID)
- `attachment_id`: linked attachment
- `owner_id`: user who initiated upload
- `expected_bytes`, `total_chunks`, `uploaded_chunks`
- `temp_key`: directory name under temp storage
- `completed`: upload complete flag
- `last_error`, `created_at`, `updated_at`

## API contract summary
Base path: `/api/messages/attachments`

### Create upload session
`POST /api/messages/attachments/sessions` (auth required)

Request (example):
```json
{
  "recipientUsername": "Test",
  "content": "Here is the PDF",
  "expiresInSeconds": 86400,
  "attachments": [
    {
      "fileName": "specs.pdf",
      "mimeType": "application/pdf",
      "sizeBytes": 1048576
    }
  ]
}
```

Response (example):
```json
{
  "message": {
    "id": 123,
    "conversationId": 45,
    "senderUsername": "admin@admin.com",
    "recipientUsername": "Test",
    "content": "Here is the PDF",
    "attachments": [
      {
        "id": 999,
        "type": "DOCUMENT",
        "mimeType": "application/pdf",
        "sizeBytes": 1048576,
        "checksum": null,
        "width": null,
        "height": null,
        "durationSeconds": null,
        "altText": null,
        "url": null,
        "thumbnailUrl": null,
        "status": "UPLOADING",
        "expiresAt": "2026-01-27T22:00:00",
        "originalFilename": "specs.pdf"
      }
    ],
    "createdAt": "2026-01-26T22:00:00",
    "isRead": false,
    "readAt": null
  },
  "uploads": [
    {
      "uploadId": "f8a983a1-d41c-4898-aa5f-df573278c743",
      "attachmentId": 999,
      "uploadUrl": "http://localhost:8080/api/messages/attachments/uploads/f8a983a1-d41c-4898-aa5f-df573278c743",
      "finalizeUrl": "http://localhost:8080/api/messages/attachments/uploads/f8a983a1-d41c-4898-aa5f-df573278c743/finalize",
      "chunkSizeBytes": 5242880
    }
  ]
}
```

### Upload chunk
`POST /api/messages/attachments/uploads/{uploadId}?chunkIndex=0&totalChunks=1` (auth required)

- Content-Type: `multipart/form-data`
- Form field: `file`

Response:
```json
{
  "uploadId": "f8a983a1-d41c-4898-aa5f-df573278c743",
  "uploadedChunks": 1,
  "totalChunks": 1
}
```

### Finalize upload
`POST /api/messages/attachments/uploads/{uploadId}/finalize` (auth required)

Response: `MessageAttachmentResponse` with status `UPLOADING` until async processing marks it `READY`.

### Cancel upload
`DELETE /api/messages/attachments/uploads/{uploadId}` (auth required)

Response: `204 No Content`.

### Download attachment
`GET /api/messages/attachments/{attachmentId}?token=...` (token or auth required)

- Supports HTTP Range for streaming
- Returns `200 OK` or `206 Partial Content`

### Download thumbnail
`GET /api/messages/attachments/{attachmentId}/thumbnail?token=...` (token or auth required)

## Status codes and errors
- `400 Bad Request`: validation failure (size mismatch, unsupported type, invalid metadata)
- `401 Unauthorized`: missing token or invalid user
- `403 Forbidden`: user not a participant in the conversation
- `404 Not Found`: missing attachment or quarantined/failed attachments
- `410 Gone`: expired attachment
- `429 Too Many Requests`: too many pending uploads for a user

## Security considerations
- **Authorization**: only conversation participants can access attachments.
- **Signed download tokens**: time-limited tokens generated per user.
- **MIME sniffing**: server verifies content with Apache Tika after upload.
- **Path traversal protection**: storage service normalizes paths and blocks escaping the storage root.
- **Upload limits**: enforced by backend validation and servlet multipart limits.

## Background jobs
- **Virus scan worker**: `AttachmentProcessingService` runs async, scans with ClamAV, and updates status.
- **Expiration cleanup**: `AttachmentExpiryService` runs on `message.attachments.expiry-cron` and deletes files.

## Failure modes and user impact
- **Scanner unavailable**:
  - `virus.scan.fail-closed=true` -> attachment becomes `FAILED` and is removed.
  - `virus.scan.fail-closed=false` -> scan is skipped and attachment becomes `READY`.
- **Upload failed or size mismatch**: status becomes `FAILED`, attachment is unavailable.
- **Quarantined**: status becomes `QUARANTINED`, download is blocked.
- **Expired**: status becomes `EXPIRED`, download returns `410 Gone`.