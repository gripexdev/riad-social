package com.instagramclone.backend.message;

public class AttachmentUploadSessionResponse {
    private String uploadId;
    private Long attachmentId;
    private String uploadUrl;
    private String finalizeUrl;
    private long chunkSizeBytes;

    public AttachmentUploadSessionResponse(
            String uploadId,
            Long attachmentId,
            String uploadUrl,
            String finalizeUrl,
            long chunkSizeBytes
    ) {
        this.uploadId = uploadId;
        this.attachmentId = attachmentId;
        this.uploadUrl = uploadUrl;
        this.finalizeUrl = finalizeUrl;
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public String getUploadId() {
        return uploadId;
    }

    public Long getAttachmentId() {
        return attachmentId;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public String getFinalizeUrl() {
        return finalizeUrl;
    }

    public long getChunkSizeBytes() {
        return chunkSizeBytes;
    }
}
