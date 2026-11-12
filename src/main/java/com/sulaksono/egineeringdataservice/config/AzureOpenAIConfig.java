package com.sulaksono.egineeringdataservice.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.okhttp.OkHttpAsyncHttpClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds a singleton {@link OpenAIClient}.
 */
@Configuration
@EnableConfigurationProperties(AzureOpenAIProperties.class)
public class AzureOpenAIConfig {


    @Bean
    public HttpClient okHttpClient() {
        return new OkHttpAsyncHttpClientBuilder()
                .build();
    }

    @Bean
    public OpenAIClient openAIClient(
            AzureOpenAIProperties props,
            HttpClient okHttpClient) {

        return new OpenAIClientBuilder()
                .endpoint(normalizeEndpoint(props.endpoint()))
                .credential(new AzureKeyCredential(props.apiKey()))
                .httpClient(okHttpClient)
                .buildClient();
    }

    static String normalizeEndpoint(String configuredEndpoint) {
        String endpoint = configuredEndpoint.trim();
        String suffix = "/openai/v1";
        int suffixStart = endpoint.endsWith("/")
                ? endpoint.length() - suffix.length() - 1
                : endpoint.length() - suffix.length();
        if (suffixStart >= 0
                && endpoint.regionMatches(true, suffixStart, suffix, 0, suffix.length())) {
            return endpoint.substring(0, suffixStart) + "/";
        }
        return endpoint;
    }
}