package com.instagramclone.backend.storage;

import com.instagramclone.backend.message.VirusScanStatus;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ClamAvVirusScanService implements VirusScanService {

    private static final Logger logger = LoggerFactory.getLogger(ClamAvVirusScanService.class);
    private static final int CHUNK_SIZE = 2048;

    private final boolean scanEnabled;
    private final String host;
    private final int port;
    private final Duration timeout;
    private final boolean failClosed;

    public ClamAvVirusScanService(
            @Value("${virus.scan.enabled:true}") boolean scanEnabled,
            @Value("${virus.scan.host:clamav}") String host,
            @Value("${virus.scan.port:3310}") int port,
            @Value("${virus.scan.timeout-seconds:30}") long timeoutSeconds,
            @Value("${virus.scan.fail-closed:true}") boolean failClosed
    ) {
        this.scanEnabled = scanEnabled;
        this.host = host;
        this.port = port;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.failClosed = failClosed;
    }

    @Override
    public VirusScanResult scan(Path filePath) {
        if (!scanEnabled) {
            return new VirusScanResult(VirusScanStatus.SKIPPED, "Virus scan disabled.");
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
            socket.setSoTimeout((int) timeout.toMillis());
            try (OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream();
                 InputStream fileStream = new BufferedInputStream(new FileInputStream(filePath.toFile()))) {

                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                byte[] buffer = new byte[CHUNK_SIZE];
                int read;
                while ((read = fileStream.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    writeLength(out, read);
                    out.write(buffer, 0, read);
                }
                writeLength(out, 0);
                out.flush();

                String response = readResponse(in);
                if (response.contains("OK")) {
                    return new VirusScanResult(VirusScanStatus.CLEAN, response.trim());
                }
                if (response.contains("FOUND")) {
                    return new VirusScanResult(VirusScanStatus.INFECTED, response.trim());
                }
                return new VirusScanResult(VirusScanStatus.FAILED, response.trim());
            }
        } catch (IOException ex) {
            logger.warn("Virus scan failed: {}", ex.getMessage());
            if (failClosed) {
                return new VirusScanResult(VirusScanStatus.FAILED, ex.getMessage());
            }
            return new VirusScanResult(VirusScanStatus.SKIPPED, "Scanner unavailable.");
        }
    }

    private void writeLength(OutputStream out, int length) throws IOException {
        out.write(new byte[] {
                (byte) ((length >> 24) & 0xFF),
                (byte) ((length >> 16) & 0xFF),
                (byte) ((length >> 8) & 0xFF),
                (byte) (length & 0xFF)
        });
    }

    private String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int read;
        byte[] data = new byte[256];
        while ((read = in.read(data)) != -1) {
            buffer.write(data, 0, read);
            String content = buffer.toString(StandardCharsets.UTF_8);
            if (content.contains("\n") || content.contains("\0")) {
                return content;
            }
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
