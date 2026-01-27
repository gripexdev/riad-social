package com.instagramclone.backend.storage;

import com.instagramclone.backend.message.VirusScanStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClamAvVirusScanServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void scanDisabledReturnsSkipped() throws Exception {
        Path file = Files.writeString(tempDir.resolve("sample.txt"), "hello");
        ClamAvVirusScanService service = new ClamAvVirusScanService(false, "localhost", 3310, 1, true);

        VirusScanResult result = service.scan(file);

        assertEquals(VirusScanStatus.SKIPPED, result.status());
    }

    @Test
    void scanFailureReturnsFailedWhenFailClosed() throws Exception {
        Path file = Files.writeString(tempDir.resolve("sample.txt"), "hello");
        ClamAvVirusScanService service = new ClamAvVirusScanService(true, "invalid-host", 3310, 1, true);

        VirusScanResult result = service.scan(file);

        assertEquals(VirusScanStatus.FAILED, result.status());
    }

    @Test
    void scanFailureReturnsSkippedWhenFailOpen() throws Exception {
        Path file = Files.writeString(tempDir.resolve("sample.txt"), "hello");
        ClamAvVirusScanService service = new ClamAvVirusScanService(true, "invalid-host", 3310, 1, false);

        VirusScanResult result = service.scan(file);

        assertEquals(VirusScanStatus.SKIPPED, result.status());
    }

    @Test
    void scanReturnsCleanWhenServerRespondsOk() throws Exception {
        Path file = Files.writeString(tempDir.resolve("clean.txt"), "clean");

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> serverTask = executor.submit(() -> handleStream(server, "stream: OK\0"));

            ClamAvVirusScanService service = new ClamAvVirusScanService(true, "localhost", port, 2, true);
            VirusScanResult result = service.scan(file);

            assertEquals(VirusScanStatus.CLEAN, result.status());

            serverTask.get(2, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void scanReturnsInfectedWhenServerRespondsFound() throws Exception {
        Path file = Files.writeString(tempDir.resolve("infected.txt"), "infected");

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> serverTask = executor.submit(() -> handleStream(server, "stream: Eicar FOUND\0"));

            ClamAvVirusScanService service = new ClamAvVirusScanService(true, "localhost", port, 2, true);
            VirusScanResult result = service.scan(file);

            assertEquals(VirusScanStatus.INFECTED, result.status());

            serverTask.get(2, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void scanReturnsFailedForUnexpectedResponse() throws Exception {
        Path file = Files.writeString(tempDir.resolve("unknown.txt"), "unknown");

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> serverTask = executor.submit(() -> handleStream(server, "stream: ERROR\0"));

            ClamAvVirusScanService service = new ClamAvVirusScanService(true, "localhost", port, 2, true);
            VirusScanResult result = service.scan(file);

            assertEquals(VirusScanStatus.FAILED, result.status());

            serverTask.get(2, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void readResponseAndWriteLengthHelpers() throws Exception {
        ClamAvVirusScanService service = new ClamAvVirusScanService(true, "localhost", 3310, 1, true);
        ByteArrayInputStream input = new ByteArrayInputStream("stream: OK\0".getBytes(StandardCharsets.UTF_8));

        String response = ReflectionTestUtils.invokeMethod(service, "readResponse", input);

        assertTrue(response.contains("OK"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ReflectionTestUtils.invokeMethod(service, "writeLength", out, 5);
        assertArrayEquals(new byte[] {0, 0, 0, 5}, out.toByteArray());
    }

    private void handleStream(ServerSocket server, String response) {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(2000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            readUntilNull(in);
            while (true) {
                int length = readLength(in);
                if (length <= 0) {
                    break;
                }
                in.readNBytes(length);
            }
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // ignore
        }
    }

    private void readUntilNull(InputStream in) throws IOException {
        int value;
        while ((value = in.read()) != -1) {
            if (value == 0) {
                return;
            }
        }
    }

    private int readLength(InputStream in) throws IOException {
        byte[] data = in.readNBytes(4);
        if (data.length < 4) {
            return -1;
        }
        return ((data[0] & 0xFF) << 24)
                | ((data[1] & 0xFF) << 16)
                | ((data[2] & 0xFF) << 8)
                | (data[3] & 0xFF);
    }
}
