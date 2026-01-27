package com.instagramclone.backend.storage;

import com.instagramclone.backend.message.VirusScanStatus;

public record VirusScanResult(VirusScanStatus status, String message) {
}
