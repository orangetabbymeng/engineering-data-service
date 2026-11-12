package com.sulaksono.egineeringdataservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiteralSearchRequest(
        @NotBlank @Size(max = 500) String query,
        LiteralSearchField field,
        @Min(1) @Max(50) Integer limit,
        Boolean includeDeprecated
) {
    public LiteralSearchField effectiveField() {
        return field == null ? LiteralSearchField.CONTENT : field;
    }

    public int effectiveLimit() {
        return limit == null ? 10 : limit;
    }

    public boolean shouldIncludeDeprecated() {
        return Boolean.TRUE.equals(includeDeprecated);
    }
}
