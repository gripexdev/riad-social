# ADR 0001: Chat Attachments

## Context
We need production-grade chat attachments (images, videos, documents) with safe uploads, realtime updates, and a clear lifecycle (uploading, ready, expired/quarantined/failed). Attachments must be accessible only to conversation participants and should not be publicly exposed.

## Decision
- Use a dedicated upload-session flow (create session -> upload chunks -> finalize) to support progress and retries.
- Perform client-side image compression to reduce bandwidth and improve UX without server load.
- Scan files asynchronously with ClamAV (clamd) after finalize, with configurable fail-closed behavior.
- Serve downloads via a secure endpoint that checks membership and supports signed tokens.
- Expire media using a scheduled job that updates status and deletes files.

## Alternatives considered
- **Server-side compression only**: simpler client, but higher server CPU and slower UX feedback.
- **No virus scan**: reduced infrastructure but unacceptable security risk.
- **Public object storage URLs**: faster, but harder to enforce membership checks and expiration.
- **On-demand expiration checks**: simpler, but leaves stale files on disk.

## Consequences
- Additional services (ClamAV) are required in Docker Compose.
- Upload flow is more complex but enables progress, retries, and robust validation.
- Secure download endpoint centralizes access control and token handling.
- Scheduled expiration adds background load but keeps storage clean and enforces privacy.