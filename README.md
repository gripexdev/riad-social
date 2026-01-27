# Instagram Clone

## Chat Attachments
Production-grade attachments for private messaging with realtime updates.

### Supported file types
- Images: JPEG, PNG, WEBP, GIF
- Videos: MP4, WEBM, MOV
- Documents: PDF, TXT, DOC/DOCX, XLS/XLSX, PPT/PPTX

### Key features
- Multiple attachments per message (default max: 6)
- Upload sessions with chunked uploads and per-file progress
- Client-side image compression (max 1080px, quality ~0.8) with EXIF-safe orientation
- Server-side MIME sniffing and size enforcement
- Virus scanning via ClamAV (clamd) with fail-closed option
- Expiring media with scheduled cleanup
- Secure download + streaming endpoints with signed tokens and HTTP Range support
- Image alt text editing (validated and stored)

### Lifecycle states
UPLOADING -> READY -> (EXPIRED | QUARANTINED | FAILED)

### High-level flow
1) Client creates an upload session (message + attachment metadata)
2) Server creates attachment placeholders (UPLOADING) and returns upload IDs
3) Client uploads file data (single or chunked) and finalizes
4) Server assembles temp file, scans, and generates thumbnails when needed
5) Attachment becomes READY and a websocket event updates both users

### Configuration overview
See `backend/src/main/resources/application.properties` for defaults.

Limits and behavior:
- `message.attachments.max-files`
- `message.attachments.max-image-bytes`
- `message.attachments.max-video-bytes`
- `message.attachments.max-document-bytes`
- `message.attachments.max-pending-per-user`
- `message.attachments.chunk-size-bytes`
- `message.attachments.max-expiry-hours`
- `message.attachments.expiry-cron`
- `message.attachments.download-token-ttl-seconds`
- `spring.servlet.multipart.max-file-size`
- `spring.servlet.multipart.max-request-size`

Virus scanning:
- `virus.scan.enabled`
- `virus.scan.host`
- `virus.scan.port`
- `virus.scan.timeout-seconds`
- `virus.scan.fail-closed`

Storage:
- `storage.message-attachments-location`
- `storage.message-attachments-temp-location`
- `storage.message-attachments-quarantine-location`
- `storage.message-attachments-thumbnail-location`

URL generation:
- `backend.base-url` (used when async processing generates links)
- `frontend.base-url`

### How to run locally (Docker Compose)
ClamAV runs as a `clamav` service in `docker-compose.yml`.
```
docker compose up --build
```
If you are offline or have DNS issues, the Dockerfiles are set up to use locally
available base images. Run:
```
docker-compose build --pull=false
docker-compose up -d
```

### Docs
- `docs/chat-attachments.md` (backend)
- `docs/chat-attachments-ui.md` (frontend)
- `docs/testing-chat-attachments.md`
- `docs/api-chat-attachments.md`