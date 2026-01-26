package com.instagramclone.backend.message;

import com.instagramclone.backend.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attachment_upload_sessions")
public class AttachmentUploadSession {

    @Id
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false, unique = true)
    private MessageAttachment attachment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private long expectedBytes;

    @Column(nullable = false)
    private int totalChunks;

    @Column(nullable = false)
    private int uploadedChunks;

    @Column(nullable = false, length = 400)
    private String tempKey;

    @Column(nullable = false)
    private boolean completed;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public AttachmentUploadSession() {
        this.id = UUID.randomUUID().toString();
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public MessageAttachment getAttachment() {
        return attachment;
    }

    public void setAttachment(MessageAttachment attachment) {
        this.attachment = attachment;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public long getExpectedBytes() {
        return expectedBytes;
    }

    public void setExpectedBytes(long expectedBytes) {
        this.expectedBytes = expectedBytes;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getUploadedChunks() {
        return uploadedChunks;
    }

    public void setUploadedChunks(int uploadedChunks) {
        this.uploadedChunks = uploadedChunks;
    }

    public String getTempKey() {
        return tempKey;
    }

    public void setTempKey(String tempKey) {
        this.tempKey = tempKey;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
