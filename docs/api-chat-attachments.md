# Chat Attachments API

Base path: `/api/messages/attachments`

## Authentication
- Create/upload/finalize/cancel require JWT (`Authorization: Bearer <token>`)
- Download endpoints accept either JWT or a signed `token` query parameter

## Endpoints
### POST /sessions
Create upload sessions and a message with attachment placeholders.

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
        "status": "UPLOADING",
        "url": null,
        "thumbnailUrl": null,
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

### POST /uploads/{uploadId}
Upload a file chunk.

- Query params: `chunkIndex`, `totalChunks`
- Body: `multipart/form-data` with `file` field

Response:
```json
{
  "uploadId": "f8a983a1-d41c-4898-aa5f-df573278c743",
  "uploadedChunks": 1,
  "totalChunks": 1
}
```

### POST /uploads/{uploadId}/finalize
Finalize the upload. Returns the updated attachment.

### DELETE /uploads/{uploadId}
Cancel the upload. Returns `204 No Content`.

### GET /{attachmentId}
Download or stream an attachment.

- Query: `token` (optional if using JWT)
- Supports HTTP Range for video streaming

### GET /{attachmentId}/thumbnail
Download the image thumbnail.

## Common status codes
- `200 OK`, `204 No Content`, `206 Partial Content`
- `400 Bad Request` (validation failure)
- `401 Unauthorized` (missing token)
- `403 Forbidden` (not a participant)
- `404 Not Found` (missing, failed, or quarantined)
- `410 Gone` (expired)
- `429 Too Many Requests` (pending upload limit)