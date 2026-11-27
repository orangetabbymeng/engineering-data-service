package com.sulaksono.egineeringdataservice.service;

import com.sulaksono.egineeringdataservice.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores raw uploads on the local file system so processing can be retried independently.
 */
@Getter
@Service
public class FileStorageService {

    private static final Logger log =
            LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "kt", "kts", "xml", "yaml", "yml", "json", "properties",
            "gradle", "md", "txt", "sql", "zip");

    private final Path rootDir;

    public FileStorageService(StorageProperties props) {
        this.rootDir =
                Paths.get(props.location())
                        .toAbsolutePath()
                        .normalize();

        log.debug("event=constructor rootDir={}", this.rootDir);
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(rootDir);

        log.info("event=storage_init rootDir={}", rootDir);
    }

    public Path save(MultipartFile file) throws IOException {

        String requestId = MDC.get("requestId");
        String original = file.getOriginalFilename();

        if (original == null || original.isBlank()) {
            throw new IOException("File name is required");
        }

        log.debug(
                "event=file_save_start requestId={} size={}",
                requestId,
                file.getSize());

        String ext = safeExtension(original);
        String generated = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path destination = rootDir.resolve(generated).normalize();

        if (!destination.startsWith(rootDir)) {
            throw new IOException("Invalid file path");
        }

        Files.copy(file.getInputStream(), destination,
                StandardCopyOption.REPLACE_EXISTING);

        log.info("event=file_saved requestId={}", requestId);

        return destination;
    }

    private static String safeExtension(String original) {
        int separator = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        int dot = original.lastIndexOf('.');
        if (dot <= separator || dot == original.length() - 1) {
            return "";
        }

        String extension = original.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(extension) ? extension : "";
    }
}