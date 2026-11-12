package com.sulaksono.egineeringdataservice.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ZipGradleUtilTest {

    @Test
    void extractRootProjectName_fromSettingsGradle_shouldReturnName() throws IOException {
        byte[] zip = zipWithEntry(
                "settings.gradle",
                "rootProject.name = 'demo-service'"
        );

        String result = ZipGradleUtil.extractRootProjectName(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("demo-service");
    }

    @Test
    void extractRootProjectName_fromSettingsGradleKts_shouldReturnName() throws IOException {
        byte[] zip = zipWithEntry(
                "settings.gradle.kts",
                "rootProject.name = \"demo-kts-service\""
        );

        String result = ZipGradleUtil.extractRootProjectName(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("demo-kts-service");
    }

    @Test
    void extractRootProjectName_fromNestedSettingsGradle_shouldReturnName() throws IOException {
        byte[] zip = zipWithEntry(
                "project/subproject/settings.gradle",
                "rootProject.name = 'nested-service'"
        );

        String result = ZipGradleUtil.extractRootProjectName(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("nested-service");
    }

    @Test
    void extractVersion_fromBuildGradleEqualsSyntax_shouldReturnVersion() throws IOException {
        byte[] zip = zipWithEntry(
                "build.gradle",
                "version = '1.2.3'"
        );

        String result = ZipGradleUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("1.2.3");
    }

    @Test
    void extractVersion_fromBuildGradleKtsEqualsSyntax_shouldReturnVersion() throws IOException {
        byte[] zip = zipWithEntry(
                "build.gradle.kts",
                "version = \"2.0.0\""
        );

        String result = ZipGradleUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("2.0.0");
    }

    @Test
    void extractVersion_fromGroovyVersionSyntax_shouldReturnVersion() throws IOException {
        byte[] zip = zipWithEntry(
                "build.gradle",
                "version '3.1.4'"
        );

        String result = ZipGradleUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("3.1.4");
    }

    @Test
    void extractVersion_whenBothSyntaxesExist_shouldPreferEqualsSyntax() throws IOException {
        byte[] zip = zipWithEntry(
                "build.gradle",
                """
                version = '1.0.0'
                version '2.0.0'
                """
        );

        String result = ZipGradleUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("1.0.0");
    }

    @Test
    void extractVersion_fromNestedBuildGradle_shouldReturnVersion() throws IOException {
        byte[] zip = zipWithEntry(
                "demo/build.gradle",
                "version = '5.0.0'"
        );

        String result = ZipGradleUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("5.0.0");
    }

    @Test
    void extractRootProjectName_whenSettingsGradleHasNoName_shouldReturnNull() throws IOException {
        byte[] zip = zipWithEntry(
                "settings.gradle",
                "pluginManagement { repositories { gradlePluginPortal() } }"
        );

        String result = ZipGradleUtil.extractRootProjectName(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractVersion_whenBuildGradleHasNoVersion_shouldReturnNull() throws IOException {
        byte[] zip = zipWithEntry(
                "build.gradle",
                "plugins { id 'java' }"
        );

        String result = ZipGradleUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractRootProjectName_whenNoGradleSettingsFile_shouldReturnNull() throws IOException {
        byte[] zip = zipWithEntry(
                "README.md",
                "# demo"
        );

        String result = ZipGradleUtil.extractRootProjectName(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractVersion_whenNoGradleBuildFile_shouldReturnNull() throws IOException {
        byte[] zip = zipWithEntry(
                "README.md",
                "# demo"
        );

        String result = ZipGradleUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractRootProjectName_whenZipStreamIsNull_shouldReturnNull() {
        String result = ZipGradleUtil.extractRootProjectName(null);

        assertThat(result).isNull();
    }

    @Test
    void extractVersion_whenZipStreamIsNull_shouldReturnNull() {
        String result = ZipGradleUtil.extractVersion(null);

        assertThat(result).isNull();
    }

    @Test
    void extractRootProjectName_whenInputIsNotZip_shouldReturnNull() {
        ByteArrayInputStream input = new ByteArrayInputStream(
                "not a zip".getBytes(StandardCharsets.UTF_8)
        );

        String result = ZipGradleUtil.extractRootProjectName(input);

        assertThat(result).isNull();
    }

    @Test
    void constructor_shouldBePrivate() throws ReflectiveOperationException {
        Constructor<ZipGradleUtil> constructor =
                ZipGradleUtil.class.getDeclaredConstructor();

        assertThat(constructor.canAccess(null)).isFalse();

        constructor.setAccessible(true);
        ZipGradleUtil instance = constructor.newInstance();

        assertThat(instance).isNotNull();
    }

    static byte[] zipWithEntry(String entryName, String content) throws IOException {
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