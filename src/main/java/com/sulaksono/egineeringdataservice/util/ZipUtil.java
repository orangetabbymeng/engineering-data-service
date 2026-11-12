package com.sulaksono.egineeringdataservice.util;

import com.sulaksono.egineeringdataservice.model.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Extracts ZIP archives in a secure (zip-slip safe) manner with basic guardrails.
 *
 * Guardrails (defaults):
 * - skips unsupported file types (FileType.UNKNOWN) -> logs "file skipped"
 * - skips entries larger than MAX_ENTRY_BYTES      -> logs "file skipped"
 * - stops after MAX_ENTRIES and/or MAX_TOTAL_BYTES
 *
 * Note: defaults are intentionally conservative to avoid embedding huge low-signal files.
 * Tune the constants if needed.
 */
public final class ZipUtil {

    private static final Logger log = LoggerFactory.getLogger(ZipUtil.class);

    /** Per-entry uncompressed byte limit (e.g., skip huge TXT). */
    public static final long MAX_ENTRY_BYTES = 512L * 1024; // 256 KB

    /** Total uncompressed bytes limit across extracted entries (zip bomb guardrail). */
    public static final long MAX_TOTAL_BYTES = 10L * 1024 * 1024; // 10 MB

    /** Max number of entries we will consider (zip bomb guardrail). */
    public static final int MAX_ENTRIES = 10_000;

    private ZipUtil() { }

    public static List<Path> unzip(Path zipPath, Path targetDir) throws IOException {
        return unzip(zipPath, targetDir, MAX_ENTRY_BYTES, MAX_TOTAL_BYTES, MAX_ENTRIES, true);
    }

    public static List<Path> unzip(MultipartFile zip, Path targetDir) throws IOException {
        try (InputStream is = zip.getInputStream()) {
            return unzip(is, targetDir);
        }
    }

    public static List<Path> unzip(Path zipPath,
                                   Path targetDir,
                                   long maxEntryBytes,
                                   long maxTotalBytes,
                                   int maxEntries,
                                   boolean skipUnsupportedTypes) throws IOException {

        String requestId = MDC.get("requestId");
        Files.createDirectories(targetDir);

        ExtractionState state = new ExtractionState();
        ExtractionLimits limits = new ExtractionLimits(
                maxEntryBytes, maxTotalBytes, maxEntries, skipUnsupportedTypes);

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements() && state.canProcess(limits)) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    processFileEntry(zipFile, entry, zipPath, targetDir,
                            requestId, limits, state);
                }
            }
            if (entries.hasMoreElements() && state.reachedEntryLimit(limits)) {
                state.stop();
                log.warn("event=zip_extract_stopped requestId={} zip={} reason=max_entries limit={}",
                        requestId, zipPath.getFileName(), maxEntries);
            }
        }

        log.info("event=zip_extract_complete requestId={} zip={} extractedCount={} skippedCount={} totalExtractedBytes={} stoppedByLimit={}",
                requestId, zipPath.getFileName(), state.extractedCount, state.skippedCount,
                state.totalBytes, state.stopped);

        return state.extracted;
    }

    private static List<Path> unzip(InputStream is,
                                    Path targetDir) throws IOException {

        String requestId = MDC.get("requestId");
        Files.createDirectories(targetDir);

        ExtractionState state = new ExtractionState();
        ExtractionLimits limits = new ExtractionLimits(
                MAX_ENTRY_BYTES, MAX_TOTAL_BYTES, MAX_ENTRIES, true);

        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null && state.canProcess(limits)) {
                if (!entry.isDirectory()) {
                    processStreamEntry(zis, entry, targetDir, requestId, limits, state);
                }
                zis.closeEntry();
                entry = state.stopped ? null : zis.getNextEntry();
            }
            if (entry != null && state.reachedEntryLimit(limits)) {
                state.stop();
                log.warn("event=zip_extract_stopped requestId={} reason=max_entries limit={}",
                        requestId, MAX_ENTRIES);
            }
        }

        log.info("event=zip_extract_complete requestId={} extractedCount={} skippedCount={} totalExtractedBytes={} stoppedByLimit={}",
                requestId, state.extractedCount, state.skippedCount,
                state.totalBytes, state.stopped);

        return state.extracted;
    }

    private static void processFileEntry(ZipFile zipFile, ZipEntry entry, Path zipPath,
                                         Path targetDir, String requestId,
                                         ExtractionLimits limits, ExtractionState state) throws IOException {
        String entryName = sanitizeEntryName(entry.getName());
        Path resolved = resolveZipSlipSafe(targetDir, entryName);
        if (shouldSkip(entry, resolved, requestId, zipPath.getFileName(), limits, state)) {
            return;
        }

        long remaining = limits.maxTotalBytes() - state.totalBytes;
        if (remaining <= 0) {
            stopForTotalLimit(requestId, zipPath.getFileName(), limits, state);
            return;
        }

        Path temporary = temporaryPath(resolved);
        try (InputStream input = zipFile.getInputStream(entry)) {
            long written = copyWithLimits(input, temporary, limits.maxEntryBytes(), remaining);
            completeExtraction(temporary, resolved, written, state);
        } catch (EntryTooLargeException e) {
            state.skippedCount++;
            deleteQuietly(temporary);
            log.info("event=zip_entry_skipped requestId={} zip={} entry={} reason=too_large limit={}",
                    requestId, zipPath.getFileName(), entryName, limits.maxEntryBytes());
        } catch (TotalLimitReachedException e) {
            deleteQuietly(temporary);
            stopForTotalLimit(requestId, zipPath.getFileName(), limits, state);
        }
    }

    private static void processStreamEntry(ZipInputStream input, ZipEntry entry,
                                           Path targetDir, String requestId,
                                           ExtractionLimits limits, ExtractionState state) throws IOException {
        String entryName = sanitizeEntryName(entry.getName());
        Path resolved = resolveZipSlipSafe(targetDir, entryName);
        if (shouldSkip(entry, resolved, requestId, null, limits, state)) {
            drainEntry(input);
            return;
        }

        long remaining = limits.maxTotalBytes() - state.totalBytes;
        if (remaining <= 0) {
            stopForTotalLimit(requestId, null, limits, state);
            return;
        }

        Path temporary = temporaryPath(resolved);
        try {
            long written = copyWithLimits(input, temporary, limits.maxEntryBytes(), remaining);
            completeExtraction(temporary, resolved, written, state);
        } catch (EntryTooLargeException e) {
            state.skippedCount++;
            deleteQuietly(temporary);
            log.info("event=zip_entry_skipped requestId={} entry={} reason=too_large limit={}",
                    requestId, entryName, limits.maxEntryBytes());
            drainEntry(input);
        } catch (TotalLimitReachedException e) {
            deleteQuietly(temporary);
            stopForTotalLimit(requestId, null, limits, state);
        }
    }

    private static boolean shouldSkip(ZipEntry entry, Path resolved, String requestId,
                                      Path zipName, ExtractionLimits limits,
                                      ExtractionState state) {
        String entryName = sanitizeEntryName(entry.getName());
        if (limits.skipUnsupportedTypes()
                && FileTypeResolver.resolve(resolved.getFileName().toString()) == FileType.UNKNOWN) {
            state.skippedCount++;
            log.info("event=zip_entry_skipped requestId={} zip={} entry={} reason=unsupported_type",
                    requestId, zipName, entryName);
            return true;
        }
        if (entry.getSize() > limits.maxEntryBytes()) {
            state.skippedCount++;
            log.info("event=zip_entry_skipped requestId={} zip={} entry={} reason=too_large declaredSize={} limit={}",
                    requestId, zipName, entryName, entry.getSize(), limits.maxEntryBytes());
            return true;
        }
        return false;
    }

    private static Path temporaryPath(Path resolved) throws IOException {
        Files.createDirectories(resolved.getParent());
        return resolved.getParent().resolve(resolved.getFileName() + ".part-" + UUID.randomUUID());
    }

    private static void completeExtraction(Path temporary, Path resolved, long written,
                                           ExtractionState state) throws IOException {
        Files.move(temporary, resolved, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        state.extracted.add(resolved);
        state.extractedCount++;
        state.totalBytes += written;
    }

    private static void stopForTotalLimit(String requestId, Path zipName,
                                          ExtractionLimits limits, ExtractionState state) {
        state.stop();
        log.warn("event=zip_extract_stopped requestId={} zip={} reason=max_total_bytes limit={}",
                requestId, zipName, limits.maxTotalBytes());
    }

    private static String sanitizeEntryName(String name) {
        if (name == null) return "";
        String n = name.replace('\\', '/');
        while (n.startsWith("/")) n = n.substring(1);
        return n;
    }

    private static Path resolveZipSlipSafe(Path targetDir, String entryName) throws IOException {
        Path resolved = targetDir.resolve(entryName).normalize();
        if (!resolved.startsWith(targetDir.normalize())) {
            throw new IOException("Zip-Slip attack detected: " + entryName);
        }
        return resolved;
    }

    private static long copyWithLimits(InputStream in,
                                       Path outFile,
                                       long maxEntryBytes,
                                       long maxTotalBytesRemaining) throws IOException {
        long written = 0L;
        byte[] buf = new byte[8192];

        try (var out = Files.newOutputStream(outFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int r;
            while ((r = in.read(buf)) != -1) {
                if (written + r > maxEntryBytes) {
                    throw new EntryTooLargeException();
                }
                if (written + r > maxTotalBytesRemaining) {
                    throw new TotalLimitReachedException();
                }
                out.write(buf, 0, r);
                written += r;
            }
        }

        return written;
    }

    private static void drainEntry(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        while (in.read(buf) != -1) {
            log.debug("event=draining_buffer");
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // Best-effort cleanup; extraction result or original failure takes precedence.
        }
    }

    private record ExtractionLimits(long maxEntryBytes, long maxTotalBytes,
                                    int maxEntries, boolean skipUnsupportedTypes) { }

    private static final class ExtractionState {
        private final List<Path> extracted = new ArrayList<>();
        private long totalBytes;
        private int extractedCount;
        private int skippedCount;
        private boolean stopped;

        private boolean canProcess(ExtractionLimits limits) {
            return !stopped && !reachedEntryLimit(limits);
        }

        private boolean reachedEntryLimit(ExtractionLimits limits) {
            return extractedCount + skippedCount >= limits.maxEntries();
        }

        private void stop() {
            stopped = true;
        }
    }

    private static final class EntryTooLargeException extends RuntimeException { }
    private static final class TotalLimitReachedException extends RuntimeException { }
}