package com.sulaksono.egineeringdataservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * storage.location property mapping.
 */
@Validated
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(@NotBlank String location) {
}
