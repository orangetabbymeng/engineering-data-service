package com.sulaksono.egineeringdataservice.exception;

public class EmbeddingIndexMaintenanceInProgressException extends RuntimeException {

    public EmbeddingIndexMaintenanceInProgressException() {
        super("Embedding index reindex is already running");
    }
}
