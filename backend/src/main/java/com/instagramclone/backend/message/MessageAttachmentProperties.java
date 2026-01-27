package com.instagramclone.backend.message;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "message.attachments")
public class MessageAttachmentProperties {
    private int maxFiles = 6;
    private long maxImageBytes = 10485760;
    private long maxVideoBytes = 52428800;
    private long maxDocumentBytes = 20971520;
    private int maxPendingPerUser = 12;
    private long maxExpiryHours = 168;
    private long chunkSizeBytes = 5242880;
    private long downloadTokenTtlSeconds = 900;
    private String expiryCron = "0 */15 * * * *";

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxVideoBytes() {
        return maxVideoBytes;
    }

    public void setMaxVideoBytes(long maxVideoBytes) {
        this.maxVideoBytes = maxVideoBytes;
    }

    public long getMaxDocumentBytes() {
        return maxDocumentBytes;
    }

    public void setMaxDocumentBytes(long maxDocumentBytes) {
        this.maxDocumentBytes = maxDocumentBytes;
    }

    public int getMaxPendingPerUser() {
        return maxPendingPerUser;
    }

    public void setMaxPendingPerUser(int maxPendingPerUser) {
        this.maxPendingPerUser = maxPendingPerUser;
    }

    public long getMaxExpiryHours() {
        return maxExpiryHours;
    }

    public void setMaxExpiryHours(long maxExpiryHours) {
        this.maxExpiryHours = maxExpiryHours;
    }

    public long getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(long chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public long getDownloadTokenTtlSeconds() {
        return downloadTokenTtlSeconds;
    }

    public void setDownloadTokenTtlSeconds(long downloadTokenTtlSeconds) {
        this.downloadTokenTtlSeconds = downloadTokenTtlSeconds;
    }

    public String getExpiryCron() {
        return expiryCron;
    }

    public void setExpiryCron(String expiryCron) {
        this.expiryCron = expiryCron;
    }
}
