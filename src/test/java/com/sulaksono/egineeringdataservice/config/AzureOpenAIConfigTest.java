package com.sulaksono.egineeringdataservice.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.core.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAIConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AzureOpenAIConfig.class)
            .withPropertyValues(
                    "azure.openai.endpoint=https://fake-instance.openai.azure.com/",
                    "azure.openai.api-key=fake-api-key",
                    "azure.openai.embedding-model-deployment=text-embedding-3-large"
            );

    @Test
    void httpClientBean_shouldBeRegistered() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(HttpClient.class));
    }

    @Test
    void openAIClientBean_shouldBeRegistered() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(OpenAIClient.class));
    }

    @Test
    void azureOpenAIPropertiesBean_shouldBindFromEnvironment() {
        contextRunner.run(context -> {
            AzureOpenAIProperties props =
                    context.getBean(AzureOpenAIProperties.class);

            assertThat(props.endpoint())
                    .isEqualTo("https://fake-instance.openai.azure.com/");

            assertThat(props.apiKey())
                    .isEqualTo("fake-api-key");

            assertThat(props.embeddingModelDeployment())
                    .isEqualTo("text-embedding-3-large");
        });
    }

    @Test
    void openAIClientBean_shouldBeSingleton() {
        contextRunner.run(context -> {
            OpenAIClient first = context.getBean(OpenAIClient.class);
            OpenAIClient second = context.getBean(OpenAIClient.class);

            assertThat(first).isSameAs(second);
        });
    }

    @Test
    void normalizesOpenAICompatibleV1EndpointForAzureSdk() {
        assertThat(AzureOpenAIConfig.normalizeEndpoint(
                "https://hcid-pg-eastus2-foundry.openai.azure.com/openai/v1"))
                .isEqualTo("https://hcid-pg-eastus2-foundry.openai.azure.com/");
    }

    @Test
    void preservesAzureResourceRootEndpoint() {
        assertThat(AzureOpenAIConfig.normalizeEndpoint(
                "https://hcid-pg-eastus2-foundry.openai.azure.com/"))
                .isEqualTo("https://hcid-pg-eastus2-foundry.openai.azure.com/");
    }

    @Test
    void whenEndpointMissing_shouldFailContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(AzureOpenAIConfig.class)
                .withPropertyValues(
                        "azure.openai.api-key=fake-api-key"
                )
                .run(context -> assertThat(context).hasFailed());
    }
}