package com.sulaksono.egineeringdataservice.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ZipPomUtilTest {

    @ParameterizedTest
    @MethodSource("artifactIdCases")
    void extractArtifactId_shouldReturnExpectedValue(
            String entryName, String pom, String expected) throws IOException {
        byte[] zip = zipWithEntry(entryName, pom);
        String result = ZipPomUtil.extractArtifactId(new ByteArrayInputStream(zip));
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("versionCases")
    void extractVersion_shouldReturnExpectedValue(String pom, String expected) throws IOException {
        byte[] zip = zipWithEntry("pom.xml", pom);
        String result = ZipPomUtil.extractVersion(new ByteArrayInputStream(zip));
        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> artifactIdCases() {
        return Stream.of(
                Arguments.of("pom.xml", pom("demo-service", "1.2.3", null, null), "demo-service"),
                Arguments.of("project/subproject/pom.xml",
                        pom("nested-service", "1.0.0", null, null), "nested-service"),
                Arguments.of("META-INF/maven/com.example/demo-service/pom.pom",
                        pom("dot-pom-service", "1.0.0", null, null), "dot-pom-service"),
                Arguments.of("pom.xml", pom(null, null, "parent-service", "9.9.9"), "parent-service"),
                Arguments.of("pom.xml",
                        pom("child-service", "1.0.0", "parent-service", "9.9.9"), "child-service")
        );
    }

    private static Stream<Arguments> versionCases() {
        return Stream.of(
                Arguments.of(pom("demo-service", "2.0.0", null, null), "2.0.0"),
                Arguments.of(pom("child-service", null, "parent-service", "9.9.9"), "9.9.9"),
                Arguments.of(pom("child-service", "1.0.0", "parent-service", "9.9.9"), "1.0.0")
        );
    }

    private static String pom(String artifactId, String version,
                              String parentArtifactId, String parentVersion) {
        String parent = parentArtifactId == null ? "" : """
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>%s</artifactId>
                        <version>%s</version>
                    </parent>
                """.formatted(parentArtifactId, parentVersion);
        String artifact = artifactId == null ? "" : "<artifactId>" + artifactId + "</artifactId>";
        String projectVersion = version == null ? "" : "<version>" + version + "</version>";
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    %s
                    %s
                    %s
                </project>
                """.formatted(parent, artifact, projectVersion);
    }

    @Test
    void extractArtifactId_whenNoPomInZip_shouldReturnNull() throws IOException {
        byte[] zip = zipWithEntry("README.md", "# demo");

        String result = ZipPomUtil.extractArtifactId(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractVersion_whenNoPomInZip_shouldReturnNull() throws IOException {
        byte[] zip = zipWithEntry("README.md", "# demo");

        String result = ZipPomUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractArtifactId_whenPomXmlIsMalformed_shouldReturnNull() throws IOException {
        byte[] zip = zipWithEntry("pom.xml", "<project><this-is-not-valid></project>");

        String result = ZipPomUtil.extractArtifactId(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractVersion_whenPomXmlIsMalformed_shouldReturnNull() throws IOException {
        byte[] zip = zipWithEntry("pom.xml", "not xml at all");

        String result = ZipPomUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractArtifactId_whenInputIsNotZip_shouldReturnNull() {
        ByteArrayInputStream input = new ByteArrayInputStream(
                "not a zip".getBytes(StandardCharsets.UTF_8)
        );

        String result = ZipPomUtil.extractArtifactId(input);

        assertThat(result).isNull();
    }

    @Test
    void extractVersion_whenInputIsNotZip_shouldReturnNull() {
        ByteArrayInputStream input = new ByteArrayInputStream(
                "not a zip".getBytes(StandardCharsets.UTF_8)
        );

        String result = ZipPomUtil.extractVersion(input);

        assertThat(result).isNull();
    }

    @Test
    void constructor_shouldBePrivate() throws ReflectiveOperationException {
        Constructor<ZipPomUtil> constructor =
                ZipPomUtil.class.getDeclaredConstructor();

        assertThat(constructor.canAccess(null)).isFalse();

        constructor.setAccessible(true);
        ZipPomUtil instance = constructor.newInstance();

        assertThat(instance).isNotNull();
    }

    private static byte[] zipWithEntry(String entryName, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            ZipEntry entry = new ZipEntry(entryName);
            zip.putNextEntry(entry);
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        return bytes.toByteArray();
    }
}