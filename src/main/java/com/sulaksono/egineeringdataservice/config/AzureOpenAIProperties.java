package com.sulaksono.egineeringdataservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe mapping for azure.openai.* properties.
 */
@Validated
@ConfigurationProperties(prefix = "azure.openai")
public record AzureOpenAIProperties(
        @NotBlank String endpoint,
        @NotBlank String apiKey,
        @NotBlank String embeddingModelDeployment) {
}
