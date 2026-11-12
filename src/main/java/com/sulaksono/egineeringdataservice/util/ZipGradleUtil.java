package com.sulaksono.egineeringdataservice.util;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipGradleUtil {

    private static final int MAX_ENTRY_BYTES = 1024 * 1024;

    // Fixed: Added ['\"] to match opening quote, and ([^'\"]+) to capture the value
    private static final Pattern ROOT_PROJECT_NAME =
            Pattern.compile("(?m)^\\s*rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]\\s*$");

    private static final Pattern VERSION_EQUALS =
            Pattern.compile("(?m)^\\s*version\\s*=\\s*['\"]([^'\"]+)['\"]\\s*$");

    private static final Pattern VERSION_GROOVY =
            Pattern.compile("(?m)^\\s*version\\s+['\"]([^'\"]+)['\"]\\s*$");

    private ZipGradleUtil() {
    }

    public static @Nullable String extractRootProjectName(@Nullable InputStream zipStream) {
        return extractFromEntry(
                zipStream,
                entryBase -> entryBase.equals("settings.gradle")
                        || entryBase.equals("settings.gradle.kts"),
                content -> firstMatch(ROOT_PROJECT_NAME, content)
        );
    }

    public static @Nullable String extractVersion(@Nullable InputStream zipStream) {
        return extractFromEntry(
                zipStream,
                entryBase -> entryBase.equals("build.gradle")
                        || entryBase.equals("build.gradle.kts"),
                content -> firstNonBlank(
                        firstMatch(VERSION_EQUALS, content),
                        firstMatch(VERSION_GROOVY, content)
                )
        );
    }

    private interface EntryNamePredicate {
        boolean test(String baseName);
    }

    private interface ContentMapper {
        @Nullable String map(String content);
    }

    private static @Nullable String extractFromEntry(
            @Nullable InputStream zipStream,
            EntryNamePredicate predicate,
            ContentMapper mapper) {

        if (zipStream == null) {
            return null;
        }

        try (ZipInputStream zin = new ZipInputStream(zipStream)) {
            ZipEntry entry;

            while ((entry = zin.getNextEntry()) != null) {
                String result = readMatchingEntry(zin, entry, predicate, mapper);
                zin.closeEntry();
                if (result != null && !result.isBlank()) {
                    return result;
                }
            }
        } catch (IOException ignored) {
            return null;
        }

        return null;
    }

    private static @Nullable String readMatchingEntry(ZipInputStream input, ZipEntry entry,
                                            EntryNamePredicate predicate,
                                            ContentMapper mapper) throws IOException {
        if (entry.isDirectory() || !predicate.test(baseName(entry.getName()))) {
            return null;
        }
        return mapper.map(readEntryAsString(input, MAX_ENTRY_BYTES));
    }

    private static String baseName(@Nullable String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return "";
        }

        String normalized = entryName.replace('\\', '/');

        return Paths.get(normalized)
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);
    }

    private static String readEntryAsString(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];
        int total = 0;
        int read;

        while (total < maxBytes
                && (read = in.read(buffer, 0, Math.min(buffer.length, maxBytes - total))) != -1) {
            out.write(buffer, 0, read);
            total += read;
        }

        return out.toString(StandardCharsets.UTF_8);
    }

    private static @Nullable String firstMatch(Pattern pattern, @Nullable String content) {
        if (content == null) {
            return null;
        }

        Matcher matcher = pattern.matcher(content);

        return matcher.find() ? matcher.group(1) : null;
    }

    private static @Nullable String firstNonBlank(
            @Nullable String first, @Nullable String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}