package com.instagramclone.backend.message;

import java.util.List;

public class CreateAttachmentUploadSessionResponse {
    private MessageResponse message;
    private List<AttachmentUploadSessionResponse> uploads;

    public CreateAttachmentUploadSessionResponse(
            MessageResponse message,
            List<AttachmentUploadSessionResponse> uploads
    ) {
        this.message = message;
        this.uploads = uploads;
    }

    public MessageResponse getMessage() {
        return message;
    }

    public List<AttachmentUploadSessionResponse> getUploads() {
        return uploads;
    }
}
