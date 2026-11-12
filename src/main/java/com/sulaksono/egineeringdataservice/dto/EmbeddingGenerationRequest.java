package com.sulaksono.egineeringdataservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmbeddingGenerationRequest(
        @NotBlank
        @Size(max = 8000)
        String text
) {
}
