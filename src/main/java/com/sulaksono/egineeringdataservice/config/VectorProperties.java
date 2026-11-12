package com.sulaksono.egineeringdataservice.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Vector chunking configuration.
 *
 * @param includePathDepth how many path segments from the right to include in the header;
 *                         {@code 0} includes only the file name
 * @param chunkSizeTokens chunk window size in tokens
 * @param chunkOverlapTokens overlap between adjacent chunks in tokens
 * @param maxChunksPerFile maximum chunks generated per file; {@code 0} means unlimited
 */
@Validated
@ConfigurationProperties(prefix = "vector")
public record VectorProperties(
        @Min(0) @DefaultValue("0") int includePathDepth,
        @DefaultValue("800") int chunkSizeTokens,
        @DefaultValue("100") int chunkOverlapTokens,
        @Min(0) @DefaultValue("0") int maxChunksPerFile) {
}