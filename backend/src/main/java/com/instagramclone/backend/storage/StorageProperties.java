package com.instagramclone.backend.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storage")
public class StorageProperties {
    private String location = "uploads";
    private String messageAttachmentsLocation = "uploads/message-attachments";
    private String messageAttachmentsTempLocation = "uploads/message-attachments/tmp";
    private String messageAttachmentsQuarantineLocation = "uploads/message-attachments/quarantine";
    private String messageAttachmentsThumbnailLocation = "uploads/message-attachments/thumbs";

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getMessageAttachmentsLocation() {
        return messageAttachmentsLocation;
    }

    public void setMessageAttachmentsLocation(String messageAttachmentsLocation) {
        this.messageAttachmentsLocation = messageAttachmentsLocation;
    }

    public String getMessageAttachmentsTempLocation() {
        return messageAttachmentsTempLocation;
    }

    public void setMessageAttachmentsTempLocation(String messageAttachmentsTempLocation) {
        this.messageAttachmentsTempLocation = messageAttachmentsTempLocation;
    }

    public String getMessageAttachmentsQuarantineLocation() {
        return messageAttachmentsQuarantineLocation;
    }

    public void setMessageAttachmentsQuarantineLocation(String messageAttachmentsQuarantineLocation) {
        this.messageAttachmentsQuarantineLocation = messageAttachmentsQuarantineLocation;
    }

    public String getMessageAttachmentsThumbnailLocation() {
        return messageAttachmentsThumbnailLocation;
    }

    public void setMessageAttachmentsThumbnailLocation(String messageAttachmentsThumbnailLocation) {
        this.messageAttachmentsThumbnailLocation = messageAttachmentsThumbnailLocation;
    }
}
