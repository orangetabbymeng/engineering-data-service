package com.sulaksono.egineeringdataservice.dto;

import com.sulaksono.egineeringdataservice.model.FileType;

import java.util.UUID;

public record EmbeddingSearchResult(
        UUID id,
        double score,
        String content,
        String fileName,
        String path,
        String module,
        String moduleVersion,
        FileType fileType,
        int chunkIndex,
        int chunkCount,
        boolean deprecated
) {
}
