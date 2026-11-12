package com.sulaksono.egineeringdataservice.dto;

import java.time.Instant;

public record EmbeddingIndexMaintenanceStatus(
        State state,
        Instant startedAt,
        Instant completedAt,
        String message
) {

    public enum State {
        IDLE,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public static EmbeddingIndexMaintenanceStatus idle() {
        return new EmbeddingIndexMaintenanceStatus(State.IDLE, null, null, "No reindex has been requested");
    }
}
