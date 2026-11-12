package com.sulaksono.egineeringdataservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record EmbeddingSearchRequest(
        @Schema(description = "Natural-language question, code identifier, or error text to find.",
                example = "Where are Keycloak roles mapped?")
        @NotBlank String query,

        @Schema(description = "Maximum number of results. Defaults to 10.",
                example = "10", minimum = "1", maximum = "50")
        @Min(1) @Max(50) Integer limit,

        @Schema(description = "Exact module filter. Omit when unknown, then discover it from result metadata.",
                example = "engineering-data-service")
        String module,

        @Schema(description = "Exact version filter. Omit when unknown or to search all module versions.",
                example = "1.5.1")
        String moduleVersion,

        @Schema(description = "Whether deprecated chunks may be returned. Defaults to false.",
                example = "false")
        Boolean includeDeprecated,

        @Schema(description = "Retrieval strategy. HYBRID is recommended for broad discovery. "
                + "Defaults to SEMANTIC.", example = "HYBRID")
        SearchMode mode,

        @Schema(description = "Optional maximum cosine distance for semantic candidates.", example = "0.45")
        @Positive Double maxDistance
) {
    public EmbeddingSearchRequest(String query, Integer limit, String module, String moduleVersion,
                                  Boolean includeDeprecated, SearchMode mode) {
        this(query, limit, module, moduleVersion, includeDeprecated, mode, null);
    }

    public int effectiveLimit() {
        return limit == null ? 10 : limit;
    }

    public boolean shouldIncludeDeprecated() {
        return Boolean.TRUE.equals(includeDeprecated);
    }

    public SearchMode effectiveMode() {
        return mode == null ? SearchMode.SEMANTIC : mode;
    }
}
