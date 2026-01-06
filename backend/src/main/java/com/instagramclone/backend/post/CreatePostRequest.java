package com.instagramclone.backend.post;

import org.springframework.web.multipart.MultipartFile;

public class CreatePostRequest {
    private String caption;
    private MultipartFile file;

    // Getters and Setters
    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
