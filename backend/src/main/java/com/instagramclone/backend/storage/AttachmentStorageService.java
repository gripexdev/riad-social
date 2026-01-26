package com.instagramclone.backend.storage;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
public class AttachmentStorageService {

    private final Path rootLocation;
    private final Path tempLocation;
    private final Path quarantineLocation;
    private final Path thumbnailLocation;

    public AttachmentStorageService(StorageProperties properties) {
        this.rootLocation = Paths.get(properties.getMessageAttachmentsLocation());
        this.tempLocation = Paths.get(properties.getMessageAttachmentsTempLocation());
        this.quarantineLocation = Paths.get(properties.getMessageAttachmentsQuarantineLocation());
        this.thumbnailLocation = Paths.get(properties.getMessageAttachmentsThumbnailLocation());
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            Files.createDirectories(tempLocation);
            Files.createDirectories(quarantineLocation);
            Files.createDirectories(thumbnailLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize attachment storage", e);
        }
    }

    public String createTempKey() {
        return UUID.randomUUID().toString();
    }

    public Path resolveTempPath(String tempKey) {
        return tempLocation.resolve(tempKey);
    }

    public Path resolveChunkPath(String tempKey, int chunkIndex) {
        return tempLocation.resolve(tempKey).resolve("chunk-" + chunkIndex);
    }

    public void ensureTempDirectory(String tempKey) {
        try {
            Files.createDirectories(resolveTempPath(tempKey));
        } catch (IOException e) {
            throw new RuntimeException("Could not create temp directory", e);
        }
    }

    public void writeTempFile(String tempKey, InputStream inputStream) {
        Path destination = resolveTempPath(tempKey).resolve("upload.bin");
        writeStream(destination, inputStream, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void writeChunk(String tempKey, int chunkIndex, InputStream inputStream) {
        ensureTempDirectory(tempKey);
        Path destination = resolveChunkPath(tempKey, chunkIndex);
        writeStream(destination, inputStream, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public Path assembleChunks(String tempKey, int totalChunks) {
        Path target = resolveTempPath(tempKey).resolve("upload.bin");
        try {
            Files.deleteIfExists(target);
            for (int i = 0; i < totalChunks; i++) {
                Path chunk = resolveChunkPath(tempKey, i);
                if (!Files.exists(chunk)) {
                    throw new RuntimeException("Missing chunk " + i);
                }
                Files.write(target, Files.readAllBytes(chunk), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            return target;
        } catch (IOException e) {
            throw new RuntimeException("Failed to assemble chunks", e);
        }
    }

    public String generateStorageKey(String originalFilename) {
        return UUID.randomUUID().toString() + "-" + sanitizeFilename(originalFilename);
    }

    public void storePermanent(InputStream inputStream, String storageKey) {
        Path destination = rootLocation.resolve(storageKey).normalize().toAbsolutePath();
        if (!destination.getParent().equals(rootLocation.toAbsolutePath())) {
            throw new IllegalArgumentException("Cannot store file outside current directory.");
        }
        writeStream(destination, inputStream, StandardOpenOption.CREATE_NEW);
    }

    public String storeThumbnail(InputStream inputStream, String originalFilename) {
        String key = UUID.randomUUID().toString() + "-" + sanitizeFilename(originalFilename);
        Path destination = thumbnailLocation.resolve(key).normalize().toAbsolutePath();
        if (!destination.getParent().equals(thumbnailLocation.toAbsolutePath())) {
            throw new IllegalArgumentException("Cannot store thumbnail outside current directory.");
        }
        writeStream(destination, inputStream, StandardOpenOption.CREATE_NEW);
        return key;
    }

    public void moveToQuarantine(String storageKey) {
        try {
            Path source = rootLocation.resolve(storageKey);
            if (!Files.exists(source)) {
                return;
            }
            Path destination = quarantineLocation.resolve(storageKey);
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to quarantine attachment", e);
        }
    }

    public Resource loadAsResource(String storageKey) {
        try {
            Path file = rootLocation.resolve(storageKey);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            }
            throw new RuntimeException("Could not read file: " + storageKey);
        } catch (IOException e) {
            throw new RuntimeException("Could not read file: " + storageKey, e);
        }
    }

    public Path resolvePermanentPath(String storageKey) {
        return rootLocation.resolve(storageKey);
    }

    public Path resolveThumbnailPath(String storageKey) {
        return thumbnailLocation.resolve(storageKey);
    }

    public Resource loadThumbnailAsResource(String storageKey) {
        try {
            Path file = thumbnailLocation.resolve(storageKey);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            }
            throw new RuntimeException("Could not read thumbnail: " + storageKey);
        } catch (IOException e) {
            throw new RuntimeException("Could not read thumbnail: " + storageKey, e);
        }
    }

    public void deletePermanent(String storageKey) {
        try {
            Files.deleteIfExists(rootLocation.resolve(storageKey));
        } catch (IOException e) {
            throw new RuntimeException("Could not delete attachment", e);
        }
    }

    public void deleteThumbnail(String storageKey) {
        try {
            Files.deleteIfExists(thumbnailLocation.resolve(storageKey));
        } catch (IOException e) {
            throw new RuntimeException("Could not delete thumbnail", e);
        }
    }

    public void deleteTemp(String tempKey) {
        Path tempDir = resolveTempPath(tempKey);
        if (!Files.exists(tempDir)) {
            return;
        }
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // ignore cleanup
                        }
                    });
        } catch (IOException e) {
            // ignore cleanup
        }
    }

    private void writeStream(Path destination, InputStream inputStream, OpenOption... options) {
        try (InputStream stream = inputStream) {
            Files.write(destination, stream.readAllBytes(), options);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file.", e);
        }
    }

    private String sanitizeFilename(String originalFilename) {
        return Objects.requireNonNullElse(originalFilename, "attachment")
                .replaceAll("\\s+", "_")
                .replaceAll("[\\\\/]+", "_");
    }
}
