package com.sulaksono.egineeringdataservice.dto;

import com.sulaksono.egineeringdataservice.model.FileType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record EmbeddingSearchResult(
        UUID id,

        @Schema(description = "Mode-specific relevance score; compare scores only within one response.")
        double score,

        String content,
        String fileName,
        String path,

        @Schema(description = "Source module. Use this value to refine a broad follow-up search.",
                example = "engineering-data-service")
        String module,

        @Schema(description = "Source module version. Use this value to refine a broad follow-up search.",
                example = "1.5.1")
        String moduleVersion,

        FileType fileType,
        int chunkIndex,
        int chunkCount,
        boolean deprecated
) {
}
