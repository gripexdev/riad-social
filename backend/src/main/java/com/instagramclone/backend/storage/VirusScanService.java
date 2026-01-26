package com.instagramclone.backend.storage;

import java.nio.file.Path;

public interface VirusScanService {
    VirusScanResult scan(Path filePath);
}
