package com.sulaksono.egineeringdataservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record EmbeddingSearchRequest(
        @NotBlank String query,
        @Min(1) @Max(50) Integer limit,
        String module,
        String moduleVersion,
        Boolean includeDeprecated
) {
    public int effectiveLimit() {
        return limit == null ? 10 : limit;
    }

    public boolean shouldIncludeDeprecated() {
        return Boolean.TRUE.equals(includeDeprecated);
    }
}
