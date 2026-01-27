# Chat Attachments (UI)

## UX overview
- Attachment picker allows multiple files and shows a preview strip above the composer.
- Images and videos show thumbnails; documents render as file chips.
- Each upload shows a progress overlay and supports cancel/retry.
- Users can enter alt text for images before sending.
- Once the server marks attachments READY, the chat view updates via websocket.

## Component map
- `frontend/src/app/messages/messages.component.ts`
- `frontend/src/app/messages/messages.component.html`
- `frontend/src/app/messages/messages.component.scss`
- `frontend/src/app/messages/message.service.ts`
- `frontend/src/app/messages/message-realtime.service.ts`

## State model
### Composer attachment states (client-side)
- `DRAFT`: selected and editable (alt text)
- `UPLOADING`: in-flight upload with progress
- `FINALIZING`: waiting on server finalize
- `FAILED`: upload or finalize error (retry or remove)
- `COMPLETE`: upload finished (removed from composer after a short delay)

### Server attachment statuses (message payload)
- `UPLOADING`: placeholder inside the message bubble
- `READY`: file is available; UI renders media or doc card
- `FAILED`: attachment unavailable
- `QUARANTINED`: blocked after virus scan
- `EXPIRED`: expired and removed from storage

## Event flow
1) User selects files -> preview strip created (image compression happens here).
2) User clicks Send -> UI calls `createAttachmentUploadSessions`.
3) Backend returns a message with `UPLOADING` attachments and per-file upload IDs.
4) Client uploads each file in chunks using `HttpClient` progress events.
5) Client calls finalize -> backend scans and processes in the background.
6) Websocket `/user/queue/messages` updates the message to `READY`.

## Accessibility notes
- Image `alt` text is stored and used for `<img>` alt attributes and captions.
- Inputs and buttons in the composer use standard HTML controls.
- Alt text input has a label/placeholder and a hard max length.