package com.sulaksono.egineeringdataservice.util;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Utility for extracting information from a {@code pom.xml} contained in a ZIP file.
 */
public final class ZipPomUtil {

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
            while ((entry = zin.getNextEntry()) != null) {
                if (isPom(entry)) {
                    return readField(zin, field);
                }
            }
        } catch (IOException | XmlPullParserException | InvalidPathException ignored) {
            return null;
        }
        return null;
    }

    private static boolean isPom(ZipEntry entry) {
        if (entry.isDirectory()) {
            return false;
        }
        String name = Path.of(entry.getName()).getFileName().toString().toLowerCase();
        return name.equals("pom.xml") || name.endsWith(".pom");
    }

    private static String readField(ZipInputStream input, Field field)
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
}