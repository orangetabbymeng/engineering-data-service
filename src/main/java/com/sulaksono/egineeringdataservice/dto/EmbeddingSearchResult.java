package com.sulaksono.egineeringdataservice.dto;

import com.sulaksono.egineeringdataservice.model.FileType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record EmbeddingSearchResult(
        UUID id,
        UUID canonicalFileId,

        @Schema(description = "Mode-specific relevance score; compare scores only within one response.")
        double score,

        @Schema(description = "Cosine distance for semantic results; null for lexical and hybrid results.")
        Double distance,

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
    public EmbeddingSearchResult(UUID id, double score, String content, String fileName, String path,
                                 String module, String moduleVersion, FileType fileType,
                                 int chunkIndex, int chunkCount, boolean deprecated) {
        this(id, null, score, null, content, fileName, path, module, moduleVersion,
                fileType, chunkIndex, chunkCount, deprecated);
    }
}
