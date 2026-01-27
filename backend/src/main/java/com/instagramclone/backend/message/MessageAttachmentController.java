package com.instagramclone.backend.message;

import com.instagramclone.backend.storage.AttachmentStorageService;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/messages/attachments")
public class MessageAttachmentController {

    private static final long DEFAULT_CHUNK_SIZE = 1024 * 1024;

    private final MessageAttachmentService attachmentService;
    private final MessageAttachmentRepository attachmentRepository;
    private final AttachmentAccessService accessService;
    private final AttachmentStorageService storageService;

    public MessageAttachmentController(
            MessageAttachmentService attachmentService,
            MessageAttachmentRepository attachmentRepository,
            AttachmentAccessService accessService,
            AttachmentStorageService storageService
    ) {
        this.attachmentService = attachmentService;
        this.attachmentRepository = attachmentRepository;
        this.accessService = accessService;
        this.storageService = storageService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<CreateAttachmentUploadSessionResponse> createUploadSessions(
            @RequestBody CreateAttachmentUploadSessionRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(attachmentService.createUploadSessions(principal.getName(), request));
    }

    @PostMapping(value = "/uploads/{uploadId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadChunkResponse> uploadChunk(
            @PathVariable String uploadId,
            @RequestParam(value = "chunkIndex", required = false) Integer chunkIndex,
            @RequestParam(value = "totalChunks", required = false) Integer totalChunks,
            @RequestPart("file") MultipartFile file,
            Principal principal
    ) {
        return ResponseEntity.ok(attachmentService.uploadChunk(uploadId, file, chunkIndex, totalChunks, principal.getName()));
    }

    @PostMapping("/uploads/{uploadId}/finalize")
    public ResponseEntity<MessageAttachmentResponse> finalizeUpload(
            @PathVariable String uploadId,
            Principal principal
    ) {
        return ResponseEntity.ok(attachmentService.finalizeUpload(uploadId, principal.getName()));
    }

    @DeleteMapping("/uploads/{uploadId}")
    public ResponseEntity<Void> cancelUpload(
            @PathVariable String uploadId,
            Principal principal
    ) {
        attachmentService.cancelUpload(uploadId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<?> downloadAttachment(
            @PathVariable Long attachmentId,
            @RequestHeader HttpHeaders headers,
            @RequestParam(value = "token", required = false) String token,
            Principal principal
    ) throws IOException {
        MessageAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found."));

        AttachmentStatus status = attachment.getStatus();
        if (status == AttachmentStatus.EXPIRED || (attachment.getExpiresAt() != null && attachment.getExpiresAt().isBefore(java.time.LocalDateTime.now()))) {
            throw new ResponseStatusException(HttpStatus.GONE, "Attachment expired.");
        }
        if (status == AttachmentStatus.QUARANTINED || status == AttachmentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment unavailable.");
        }
        accessService.assertUserCanAccess(
                attachment,
                accessService.resolveUserForAttachment(principal == null ? null : principal.getName(), token, attachment.getId())
        );

        Resource resource = storageService.loadAsResource(attachment.getStorageKey());
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        String filename = attachment.getOriginalFilename() == null ? "attachment" : attachment.getOriginalFilename();
        if (headers.getRange().isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);
        }
        ResourceRegion region = resourceRegion(resource, headers.getRange(), DEFAULT_CHUNK_SIZE);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(region);
    }

    @GetMapping("/{attachmentId}/thumbnail")
    public ResponseEntity<Resource> downloadThumbnail(
            @PathVariable Long attachmentId,
            @RequestParam(value = "token", required = false) String token,
            Principal principal
    ) {
        MessageAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found."));
        if (attachment.getThumbnailKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thumbnail not available.");
        }
        if (attachment.getStatus() != AttachmentStatus.READY) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thumbnail not available.");
        }
        accessService.assertUserCanAccess(
                attachment,
                accessService.resolveUserForAttachment(principal == null ? null : principal.getName(), token, attachment.getId())
        );

        Resource resource = storageService.loadThumbnailAsResource(attachment.getThumbnailKey());
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.IMAGE_JPEG);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"thumbnail.jpg\"")
                .body(resource);
    }

    private ResourceRegion resourceRegion(Resource resource, List<org.springframework.http.HttpRange> ranges, long chunkSize) throws IOException {
        long contentLength = resource.contentLength();
        org.springframework.http.HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        long rangeLength = Math.min(chunkSize, end - start + 1);
        return new ResourceRegion(resource, start, rangeLength);
    }
}
