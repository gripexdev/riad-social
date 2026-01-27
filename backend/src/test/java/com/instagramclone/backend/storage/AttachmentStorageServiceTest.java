package com.instagramclone.backend.storage;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentStorageServiceTest {

    @TempDir
    Path tempDir;

    private AttachmentStorageService storageService;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setMessageAttachmentsLocation(tempDir.resolve("attachments").toString());
        properties.setMessageAttachmentsTempLocation(tempDir.resolve("tmp").toString());
        properties.setMessageAttachmentsQuarantineLocation(tempDir.resolve("quarantine").toString());
        properties.setMessageAttachmentsThumbnailLocation(tempDir.resolve("thumbs").toString());
        storageService = new AttachmentStorageService(properties);
        storageService.init();
    }

    @Test
    void storePermanent_andLoadResource() throws Exception {
        String key = "file.txt";
        storageService.storePermanent(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), key);

        var resource = storageService.loadAsResource(key);
        assertNotNull(resource);
        assertTrue(resource.exists());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), resource.getContentAsByteArray());
    }

    @Test
    void assembleChunks_combinesParts() throws Exception {
        String tempKey = storageService.createTempKey();
        storageService.writeChunk(tempKey, 0, new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        storageService.writeChunk(tempKey, 1, new ByteArrayInputStream("world".getBytes(StandardCharsets.UTF_8)));

        Path assembled = storageService.assembleChunks(tempKey, 2);

        assertArrayEquals("helloworld".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(assembled));
    }

    @Test
    void moveToQuarantine_movesFile() throws Exception {
        String key = "file.bin";
        storageService.storePermanent(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)), key);

        storageService.moveToQuarantine(key);

        assertFalse(Files.exists(tempDir.resolve("attachments").resolve(key)));
        assertTrue(Files.exists(tempDir.resolve("quarantine").resolve(key)));
    }

    @Test
    void deleteTemp_removesDirectory() throws Exception {
        String tempKey = storageService.createTempKey();
        storageService.ensureTempDirectory(tempKey);
        storageService.writeTempFile(tempKey, new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        Path tempPath = storageService.resolveTempPath(tempKey);
        assertTrue(Files.exists(tempPath));

        storageService.deleteTemp(tempKey);

        assertFalse(Files.exists(tempPath));
    }

    @Test
    void storeAndDeleteThumbnail() throws Exception {
        String key = storageService.storeThumbnail(
                new ByteArrayInputStream("thumb".getBytes(StandardCharsets.UTF_8)),
                "thumb.jpg"
        );

        var resource = storageService.loadThumbnailAsResource(key);
        assertNotNull(resource);
        assertTrue(resource.exists());

        storageService.deleteThumbnail(key);

        assertFalse(Files.exists(tempDir.resolve("thumbs").resolve(key)));
    }
}
