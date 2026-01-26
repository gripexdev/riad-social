package com.instagramclone.backend.message;

public class UploadChunkResponse {
    private String uploadId;
    private int uploadedChunks;
    private int totalChunks;

    public UploadChunkResponse(String uploadId, int uploadedChunks, int totalChunks) {
        this.uploadId = uploadId;
        this.uploadedChunks = uploadedChunks;
        this.totalChunks = totalChunks;
    }

    public String getUploadId() {
        return uploadId;
    }

    public int getUploadedChunks() {
        return uploadedChunks;
    }

    public int getTotalChunks() {
        return totalChunks;
    }
}
