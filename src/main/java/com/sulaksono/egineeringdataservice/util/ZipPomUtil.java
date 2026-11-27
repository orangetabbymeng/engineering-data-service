package com.sulaksono.egineeringdataservice.util;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Utility for extracting information from a {@code pom.xml} contained in a ZIP file.
 */
public final class ZipPomUtil {

    private static final long MAX_POM_BYTES = 1024L * 1024;
    private static final int MAX_ENTRIES = 1_000; // Bound metadata scans as well as extraction.

    private ZipPomUtil() {}

    public static String extractArtifactId(InputStream zipStream) {
        return extractPomField(zipStream, Field.ARTIFACT_ID);
    }

    public static String extractVersion(InputStream zipStream) {
        return extractPomField(zipStream, Field.VERSION);
    }

    private enum Field { ARTIFACT_ID, VERSION }

    private static String extractPomField(InputStream zipStream, Field field) {
        try (ZipInputStream zin = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            int entriesRead = 0;
            while (entriesRead++ < MAX_ENTRIES && (entry = zin.getNextEntry()) != null) {
                if (isPom(entry) && entry.getSize() <= MAX_POM_BYTES) {
                    return readField(new BoundedInputStream(zin, MAX_POM_BYTES), field);
                }
            }
        } catch (IOException | XmlPullParserException ignored) {
            return null;
        }
        return null;
    }

    private static boolean isPom(ZipEntry entry) {
        if (entry.isDirectory()) {
            return false;
        }
        String entryName = entry.getName();
        if (entryName == null || entryName.isBlank()) {
            return false;
        }
        String normalized = entryName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1)
                .toLowerCase(Locale.ROOT);
        return name.equals("pom.xml") || name.endsWith(".pom");
    }

    private static String readField(InputStream input, Field field)
            throws IOException, XmlPullParserException {
        Model model = new MavenXpp3Reader().read(input, false);
        return switch (field) {
            case ARTIFACT_ID -> firstNonNull(model.getArtifactId(),
                    model.getParent() != null ? model.getParent().getArtifactId() : null);
            case VERSION -> firstNonNull(model.getVersion(),
                    model.getParent() != null ? model.getParent().getVersion() : null);
        };
    }

    private static String firstNonNull(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        private BoundedInputStream(InputStream input, long limit) {
            super(input);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                throw new IOException("POM entry exceeds extraction limit");
            }
            int value = super.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining == 0) {
                throw new IOException("POM entry exceeds extraction limit");
            }
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() {
            // The owning ZipInputStream is closed by extractPomField.
        }
    }
}