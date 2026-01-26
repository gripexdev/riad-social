# Instagram Clone

## Chat Attachments
This build adds production-grade attachments to private messaging:
- Images, videos, and documents per message (up to 6 files).
- Upload sessions with chunked uploads + per-file progress.
- Server-side MIME validation, size limits, and per-user pending limits.
- Virus scanning via ClamAV (clamd) with fail-closed support.
- Expiring media with scheduled cleanup.
- Secure, tokenized download/streaming endpoints (HTTP Range for video).
- Alt text editing for images (validated and stored).

### Configuration
Key properties (see `backend/src/main/resources/application.properties`):
- `message.attachments.*` limits (size, max files, chunk size, expiry).
- `virus.scan.*` (ClamAV host/port, timeout, fail-closed).
- `storage.message-attachments-*` (storage locations).

### Manual Test Checklist
1. Start the stack: `docker compose up --build` (ClamAV runs as `clamav`).
2. Open the app, authenticate, and open a conversation.
3. Send an image + alt text:
   - preview strip shows thumbnail + progress overlay
   - message shows placeholder → READY update via realtime
   - click image to open full-screen viewer.
4. Send a video:
   - placeholder while uploading
   - click “Play video” to stream (Range supported).
5. Send a document:
   - file card shows name + size
   - download works for both participants.
6. Toggle “Expire media in 24h” and verify `expiresAt` is set (lower cron for faster checks if needed).
7. Upload an unsupported type or oversized file and confirm client/server validation errors.
8. Stop ClamAV and confirm uploads fail when `virus.scan.fail-closed=true`.
