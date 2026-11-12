package com.sulaksono.egineeringdataservice.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * Supported file extensions and helper methods.
 */
public enum FileType {

    // ---- Build / project manifests ----
    POM("pom.xml", ".pom"),
    MAVEN_WRAPPER("mvnw", "mvnw.cmd"),

    GRADLE("build.gradle", "settings.gradle", ".gradle",
            "build.gradle.kts", "settings.gradle.kts", ".gradle.kts"),
    GRADLE_WRAPPER("gradlew", "gradlew.bat"),

    COMPOSER("composer.json", "composer.lock"),

    CARGO("cargo.toml", "cargo.lock"),                 // Cargo.toml/Cargo.lock
    PYPROJECT("pyproject.toml"),
    PYTHON_DEPS("requirements.txt", "pipfile", "pipfile.lock", "setup.py", "setup.cfg"),

    CMAKE("cmakelists.txt", ".cmake"),                 // CMakeLists.txt
    MAKEFILE("makefile", "gnumakefile"),

    DOTNET_SOLUTION(".sln"),
    DOTNET_PROJECT(".csproj", ".fsproj", ".vbproj"),
    NUGET("packages.config", ".nuspec"),

    // ---- Languages ----
    JAVA(".java"),
    PHP(".php", ".phtml", ".php3", ".php4", ".php5", ".php7", ".php8", ".phpt", ".phps"),
    PYTHON(".py", ".pyi"),
    C(".c", ".h"),
    CPP(".cpp", ".cxx", ".cc", ".hpp", ".hxx", ".hh"),
    CSHARP(".cs", ".csx"),
    RUST(".rs"),

    HTML(".html", ".htm"),
    JAVASCRIPT(".js", ".mjs", ".cjs"),
    CSS(".css"),

    // ---- Docs / diagrams / data / config ----
    MERMAID(".mmd", ".mermaid"),
    SQL(".sql"),
    PLANT_UML(".puml", ".plantuml"),
    DRAWIO(".drawio", ".dio"),
    MARKDOWN(".md", ".markdown"),
    YAML(".yml", ".yaml"),
    TOML(".toml"),
    XML(".xml"),
    JSON(".json"),
    PROPERTIES(".properties"),
    DOCKERFILE("dockerfile", "docker-compose.yml", "docker-compose.yaml", ".dockerfile"),
    CSV(".csv"),
    TXT(".txt"),

    UNKNOWN("");

    private final String[] patterns;

    FileType(String... patterns) {
        this.patterns = patterns;
    }

    public static FileType fromFileName(String name) {
        if (name == null || name.isBlank()) {
            return UNKNOWN;
        }

        String normalized = name.toLowerCase(Locale.ROOT).replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);

        return Arrays.stream(values())
                .filter(type -> type.matches(baseName))
                .findFirst()
                .orElse(UNKNOWN);
    }

    private boolean matches(String baseName) {
        return Arrays.stream(patterns)
                .filter(pattern -> pattern != null && !pattern.isEmpty())
                .map(pattern -> pattern.toLowerCase(Locale.ROOT))
                .anyMatch(pattern -> pattern.startsWith(".")
                        ? baseName.endsWith(pattern)
                        : baseName.equals(pattern));
    }

    public boolean isTextual() {
        return this != UNKNOWN && this != DRAWIO;
    }
}