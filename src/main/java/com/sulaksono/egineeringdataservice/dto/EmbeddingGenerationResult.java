package com.sulaksono.egineeringdataservice.dto;

import java.util.Arrays;

public record EmbeddingGenerationResult(
        String status,
        String generatedBy,
        String deployment,
        int dimensions,
        float[] embedding,
        String disclosure
) {
    public EmbeddingGenerationResult {
        embedding = embedding == null ? null : embedding.clone();
    }

    @Override
    public float[] embedding() {
        return embedding == null ? null : embedding.clone();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EmbeddingGenerationResult(
                String otherStatus,
                String otherGeneratedBy,
                String otherDeployment,
                int otherDimensions,
                float[] otherEmbedding,
                String otherDisclosure))) {
            return false;
        }
        return dimensions == otherDimensions
                && java.util.Objects.equals(status, otherStatus)
                && java.util.Objects.equals(generatedBy, otherGeneratedBy)
                && java.util.Objects.equals(deployment, otherDeployment)
                && Arrays.equals(embedding, otherEmbedding)
                && java.util.Objects.equals(disclosure, otherDisclosure);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(status, generatedBy, deployment, dimensions, disclosure);
        return 31 * result + Arrays.hashCode(embedding);
    }

    @Override
    public String toString() {
        return "EmbeddingGenerationResult[status=" + status
                + ", generatedBy=" + generatedBy
                + ", deployment=" + deployment
                + ", dimensions=" + dimensions
                + ", embedding=" + Arrays.toString(embedding)
                + ", disclosure=" + disclosure + ']';
    }
}
