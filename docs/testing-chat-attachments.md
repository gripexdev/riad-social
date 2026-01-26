# Testing Chat Attachments

## Manual test checklist
1) Start the stack
   - `docker compose up --build`
2) Login and open a conversation
3) Image upload
   - Add an image and alt text, send message
   - Ensure progress overlay updates and message shows placeholder
   - After scan, message renders thumbnail and alt text caption
4) Video upload + streaming
   - Send a small MP4/WEBM
   - Click "Play video" and confirm playback
   - Confirm HTTP Range responses (status 206) in the network tab
5) Document upload
   - Send a PDF or DOCX
   - Ensure file card shows name/size and download works
6) Cancel + retry
   - Start an upload, cancel it, and verify UI clears
   - Retry a failed upload and confirm it completes
7) Expiring media
   - Toggle "Expire media in 24h" and send
   - Verify `expiresAt` is set in the message payload
8) Quarantined file
   - Use an EICAR test file to trigger ClamAV
   - Expect status `QUARANTINED` and download blocked
9) Unauthorized download
   - Try downloading an attachment with a non-participant account
   - Expect `403 Forbidden`
10) Missing token
   - Access `/api/messages/attachments/{id}` without token/auth
   - Expect `401 Unauthorized`

## Automated tests
Backend unit tests:
```
cd backend
mvn test
```

Current tests cover:
- `AttachmentTokenServiceTest`
- `MessageAttachmentValidationServiceTest`

## Troubleshooting
- **ClamAV not reachable**: check `virus.scan.host` and `virus.scan.port`. If `virus.scan.fail-closed=true`, uploads will fail when clamd is down.
- **Permission denied / file errors**: verify the `uploads` volume is mounted and writable (`uploads_data` in `docker-compose.yml`).
- **Large uploads fail**: adjust `spring.servlet.multipart.max-file-size` and `spring.servlet.multipart.max-request-size` to match your limits.
- **Uploads stuck at UPLOADING**: check backend logs for finalize errors or missing temp files.