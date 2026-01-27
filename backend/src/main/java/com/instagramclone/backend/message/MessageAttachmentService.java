package com.instagramclone.backend.message;

import com.instagramclone.backend.storage.AttachmentStorageService;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserRepository;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class MessageAttachmentService {

    private static final Logger logger = LoggerFactory.getLogger(MessageAttachmentService.class);
    private static final int MESSAGE_MAX_LENGTH = 2000;

    private final MessageService messageService;
    private final MessageAttachmentRepository attachmentRepository;
    private final AttachmentUploadSessionRepository uploadSessionRepository;
    private final MessageAttachmentValidationService validationService;
    private final AttachmentStorageService storageService;
    private final AttachmentProcessingService processingService;
    private final UserRepository userRepository;
    private final MessageAttachmentProperties properties;
    private final Tika tika = new Tika();

    public MessageAttachmentService(
            MessageService messageService,
            MessageAttachmentRepository attachmentRepository,
            AttachmentUploadSessionRepository uploadSessionRepository,
            MessageAttachmentValidationService validationService,
            AttachmentStorageService storageService,
            AttachmentProcessingService processingService,
            UserRepository userRepository,
            MessageAttachmentProperties properties
    ) {
        this.messageService = messageService;
        this.attachmentRepository = attachmentRepository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.validationService = validationService;
        this.storageService = storageService;
        this.processingService = processingService;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Transactional
    public CreateAttachmentUploadSessionResponse createUploadSessions(String senderUsername, CreateAttachmentUploadSessionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload request is required.");
        }
        String recipientUsername = normalize(request.getRecipientUsername());
        if (recipientUsername.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient username is required.");
        }
        if (recipientUsername.equalsIgnoreCase(senderUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot message yourself.");
        }
        List<AttachmentUploadRequest> attachmentRequests = request.getAttachments();
        validationService.validateNewAttachments(senderUsername, attachmentRequests);
        attachmentRequests = java.util.Objects.requireNonNull(attachmentRequests, "attachments");
        long expiresInSeconds = request.getExpiresInSeconds() == null ? 0 : request.getExpiresInSeconds();
        if (expiresInSeconds < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiration must be positive.");
        }
        if (properties.getMaxExpiryHours() > 0 && expiresInSeconds > 0) {
            long maxSeconds = properties.getMaxExpiryHours() * 3600;
            if (expiresInSeconds > maxSeconds) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Expiration exceeds " + properties.getMaxExpiryHours() + " hours."
                );
            }
        }

        User sender = loadUser(senderUsername);
        User recipient = loadUser(recipientUsername);
        List<MessageAttachment> attachments = new ArrayList<>();
        LocalDateTime expiresAt = expiresInSeconds > 0 ? LocalDateTime.now().plusSeconds(expiresInSeconds) : null;
        for (AttachmentUploadRequest attachmentRequest : attachmentRequests) {
            validationService.validateAttachmentMetadata(attachmentRequest);
            AttachmentType type = validationService.resolveType(attachmentRequest.getMimeType());
            MessageAttachment attachment = new MessageAttachment();
            attachment.setType(type);
            attachment.setMimeType(normalize(attachmentRequest.getMimeType()));
            attachment.setSizeBytes(attachmentRequest.getSizeBytes());
            attachment.setChecksum(attachmentRequest.getChecksum());
            attachment.setWidth(attachmentRequest.getWidth());
            attachment.setHeight(attachmentRequest.getHeight());
            attachment.setDurationSeconds(attachmentRequest.getDurationSeconds());
            attachment.setAltText(sanitizeAltText(attachmentRequest.getAltText()));
            attachment.setOriginalFilename(attachmentRequest.getFileName());
            attachment.setStatus(AttachmentStatus.UPLOADING);
            attachment.setExpiresAt(expiresAt);
            attachment.setStorageKey(storageService.generateStorageKey(attachmentRequest.getFileName()));
            String publicId = java.util.UUID.randomUUID().toString();
            attachment.setPublicId(publicId);
            attachment.setStorageFilename(resolveStorageFilename(attachmentRequest.getFileName(), attachment.getStorageKey(), publicId));
            attachments.add(attachment);
        }

        String normalizedContent = normalize(request.getContent());
        if (!normalizedContent.isEmpty() && normalizedContent.length() > MESSAGE_MAX_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message content exceeds " + MESSAGE_MAX_LENGTH + " characters."
            );
        }
        Message message = messageService.createMessageWithAttachments(sender, recipient, normalizedContent, attachments);

        List<AttachmentUploadSessionResponse> uploads = new ArrayList<>();
        for (MessageAttachment attachment : message.getAttachments()) {
            AttachmentUploadSession session = new AttachmentUploadSession();
            session.setAttachment(attachment);
            session.setOwner(sender);
            session.setExpectedBytes(attachment.getSizeBytes());
            session.setTotalChunks(1);
            session.setUploadedChunks(0);
            session.setTempKey(storageService.createTempKey());
            uploadSessionRepository.save(session);

            String uploadUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/messages/attachments/uploads/")
                    .path(session.getId())
                    .toUriString();
            String finalizeUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/messages/attachments/uploads/")
                    .path(session.getId())
                    .path("/finalize")
                    .toUriString();
            uploads.add(new AttachmentUploadSessionResponse(
                    session.getId(),
                    attachment.getId(),
                    uploadUrl,
                    finalizeUrl,
                    properties.getChunkSizeBytes()
            ));
        }

        messageService.notifyMessageCreated(message);
        MessageResponse response = messageService.toMessageResponse(message, sender);
        return new CreateAttachmentUploadSessionResponse(response, uploads);
    }

    @Transactional
    public UploadChunkResponse uploadChunk(String uploadId, MultipartFile file, Integer chunkIndex, Integer totalChunks, String username) {
        AttachmentUploadSession session = uploadSessionRepository.findByIdAndOwnerUsername(uploadId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found."));
        if (session.isCompleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload session already completed.");
        }
        MessageAttachment attachment = session.getAttachment();
        if (attachment != null && attachment.getStatus() == AttachmentStatus.FAILED) {
            attachment.setStatus(AttachmentStatus.UPLOADING);
            attachmentRepository.save(attachment);
            messageService.notifyMessageUpdated(attachment.getMessage());
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload file is required.");
        }
        int resolvedTotalChunks = totalChunks == null ? 1 : totalChunks;
        int resolvedChunkIndex = chunkIndex == null ? 0 : chunkIndex;
        if (resolvedTotalChunks <= 0 || resolvedChunkIndex < 0 || resolvedChunkIndex >= resolvedTotalChunks) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid chunk metadata.");
        }
        session.setTotalChunks(resolvedTotalChunks);
        storageService.ensureTempDirectory(session.getTempKey());
        try (InputStream inputStream = file.getInputStream()) {
            if (resolvedTotalChunks > 1) {
                storageService.writeChunk(session.getTempKey(), resolvedChunkIndex, inputStream);
            } else {
                storageService.writeTempFile(session.getTempKey(), inputStream);
            }
        } catch (Exception e) {
            session.setLastError(e.getMessage());
            uploadSessionRepository.save(session);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store upload chunk.");
        }
        session.setUploadedChunks(Math.min(resolvedTotalChunks, Math.max(session.getUploadedChunks(), resolvedChunkIndex + 1)));
        uploadSessionRepository.save(session);
        return new UploadChunkResponse(session.getId(), session.getUploadedChunks(), session.getTotalChunks());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public MessageAttachmentResponse finalizeUpload(String uploadId, String username) {
        AttachmentUploadSession session = uploadSessionRepository.findByIdAndOwnerUsername(uploadId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found."));
        if (session.isCompleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload session already completed.");
        }
        MessageAttachment attachment = session.getAttachment();
        if (attachment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found.");
        }
        java.nio.file.Path assembledPath = storageService.resolveTempPath(session.getTempKey()).resolve("upload.bin");
        try {
            if (session.getTotalChunks() > 1) {
                assembledPath = storageService.assembleChunks(session.getTempKey(), session.getTotalChunks());
            }
            long actualSize = java.nio.file.Files.size(assembledPath);
            if (actualSize != session.getExpectedBytes()) {
                attachment.setStatus(AttachmentStatus.FAILED);
                attachmentRepository.save(attachment);
                messageService.notifyMessageUpdated(attachment.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment size mismatch.");
            }
            String checksum = calculateChecksum(assembledPath);
            if (attachment.getChecksum() != null && !attachment.getChecksum().isBlank()) {
                if (!attachment.getChecksum().equalsIgnoreCase(checksum)) {
                    attachment.setStatus(AttachmentStatus.FAILED);
                    attachmentRepository.save(attachment);
                    messageService.notifyMessageUpdated(attachment.getMessage());
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment checksum mismatch.");
                }
            }
            attachment.setChecksum(checksum);
            attachment.setSizeBytes(actualSize);
            try (InputStream inputStream = java.nio.file.Files.newInputStream(assembledPath)) {
                storageService.storePermanent(inputStream, attachment.getStorageKey());
            }
        } catch (ResponseStatusException ex) {
            session.setLastError(ex.getReason());
            uploadSessionRepository.save(session);
            storageService.deleteTemp(session.getTempKey());
            throw ex;
        } catch (Exception ex) {
            logger.error("Finalize upload failed: {}", ex.getMessage());
            session.setLastError(ex.getMessage());
            uploadSessionRepository.save(session);
            attachment.setStatus(AttachmentStatus.FAILED);
            attachmentRepository.save(attachment);
            messageService.notifyMessageUpdated(attachment.getMessage());
            storageService.deletePermanent(attachment.getStorageKey());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to finalize upload.");
        }

        session.setCompleted(true);
        uploadSessionRepository.save(session);
        storageService.deleteTemp(session.getTempKey());

        try (InputStream resourceStream = storageService.loadAsResource(attachment.getStorageKey()).getInputStream()) {
            String detected = tika.detect(resourceStream, attachment.getOriginalFilename());
            if (!validationService.isMimeAllowed(attachment.getType(), detected)) {
                attachment.setStatus(AttachmentStatus.FAILED);
                attachmentRepository.save(attachment);
                messageService.notifyMessageUpdated(attachment.getMessage());
                storageService.deletePermanent(attachment.getStorageKey());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment type is not supported.");
            }
            attachment.setMimeType(detected);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            attachment.setStatus(AttachmentStatus.FAILED);
            attachmentRepository.save(attachment);
            messageService.notifyMessageUpdated(attachment.getMessage());
            storageService.deletePermanent(attachment.getStorageKey());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to validate attachment content.");
        }

        if (attachment.getType() == AttachmentType.IMAGE) {
            try {
                var bufferedImage = javax.imageio.ImageIO.read(storageService.resolvePermanentPath(attachment.getStorageKey()).toFile());
                if (bufferedImage != null) {
                    attachment.setWidth(bufferedImage.getWidth());
                    attachment.setHeight(bufferedImage.getHeight());
                }
            } catch (Exception ex) {
                logger.warn("Failed to read image dimensions: {}", ex.getMessage());
            }
        }

        attachmentRepository.save(attachment);
        processingService.processAttachmentAsync(attachment.getId());
        return messageService.toAttachmentResponse(attachment, loadUser(username));
    }

    @Transactional
    public void cancelUpload(String uploadId, String username) {
        AttachmentUploadSession session = uploadSessionRepository.findByIdAndOwnerUsername(uploadId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found."));
        MessageAttachment attachment = session.getAttachment();
        session.setCompleted(true);
        session.setLastError("Cancelled");
        uploadSessionRepository.save(session);
        storageService.deleteTemp(session.getTempKey());
        if (attachment != null) {
            attachment.setStatus(AttachmentStatus.FAILED);
            attachmentRepository.save(attachment);
            messageService.notifyMessageUpdated(attachment.getMessage());
        }
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String sanitizeAltText(String altText) {
        if (altText == null) {
            return null;
        }
        String trimmed = altText.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveStorageFilename(String originalFilename, String storageKey, String publicId) {
        String sanitized = sanitizeFilename(originalFilename);
        if (sanitized.isBlank()) {
            sanitized = "attachment-" + publicId;
        }
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        if (storageKey != null && storageKey.length() <= 255) {
            return storageKey;
        }
        return sanitized;
    }

    private String sanitizeFilename(String originalFilename) {
        String value = originalFilename == null ? "" : originalFilename.trim();
        if (value.isEmpty()) {
            return "";
        }
        return value
                .replaceAll("\\s+", "_")
                .replaceAll("[\\\\/]+", "_");
    }

    private String calculateChecksum(java.nio.file.Path filePath) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = java.nio.file.Files.newInputStream(filePath)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to compute checksum.");
        }
    }
}
