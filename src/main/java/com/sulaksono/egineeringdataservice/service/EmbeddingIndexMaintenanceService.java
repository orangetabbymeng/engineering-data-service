package com.sulaksono.egineeringdataservice.service;

import com.sulaksono.egineeringdataservice.dto.EmbeddingIndexMaintenanceStatus;
import com.sulaksono.egineeringdataservice.exception.EmbeddingIndexMaintenanceInProgressException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class EmbeddingIndexMaintenanceService {

    static final String REINDEX_SQL = """
            REINDEX (VERBOSE) INDEX CONCURRENTLY engineering_reference.file_embeddings_embedding_idx
            """;

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIndexMaintenanceService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AsyncTaskExecutor asyncExecutor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<EmbeddingIndexMaintenanceStatus> status =
            new AtomicReference<>(EmbeddingIndexMaintenanceStatus.idle());

    public EmbeddingIndexMaintenanceService(
            JdbcTemplate jdbcTemplate,
            @Qualifier("asyncExecutor") AsyncTaskExecutor asyncExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.asyncExecutor = asyncExecutor;
    }

    public EmbeddingIndexMaintenanceStatus startReindex() {
        if (!running.compareAndSet(false, true)) {
            throw new EmbeddingIndexMaintenanceInProgressException();
        }

        Instant startedAt = Instant.now();
        EmbeddingIndexMaintenanceStatus accepted = new EmbeddingIndexMaintenanceStatus(
                EmbeddingIndexMaintenanceStatus.State.RUNNING,
                startedAt,
                null,
                "Embedding index reindex is running"
        );
        status.set(accepted);

        try {
            asyncExecutor.execute(() -> reindex(startedAt));
            return accepted;
        } catch (RuntimeException ex) {
            running.set(false);
            status.set(new EmbeddingIndexMaintenanceStatus(
                    EmbeddingIndexMaintenanceStatus.State.FAILED,
                    startedAt,
                    Instant.now(),
                    "Embedding index reindex could not be scheduled"
            ));
            throw ex;
        }
    }

    public EmbeddingIndexMaintenanceStatus status() {
        return status.get();
    }

    private void reindex(Instant startedAt) {
        log.info("event=embedding_index_reindex_start index=file_embeddings_embedding_idx startedAt={}", startedAt);
        try {
            // REINDEX CONCURRENTLY must execute outside an explicit transaction block.
            jdbcTemplate.execute(REINDEX_SQL);
            Instant completedAt = Instant.now();
            status.set(new EmbeddingIndexMaintenanceStatus(
                    EmbeddingIndexMaintenanceStatus.State.SUCCEEDED,
                    startedAt,
                    completedAt,
                    "Embedding index reindex completed"
            ));
            log.info("event=embedding_index_reindex_complete index=file_embeddings_embedding_idx durationMs={}",
                    Duration.between(startedAt, completedAt).toMillis());
        } catch (Exception ex) {
            Instant completedAt = Instant.now();
            status.set(new EmbeddingIndexMaintenanceStatus(
                    EmbeddingIndexMaintenanceStatus.State.FAILED,
                    startedAt,
                    completedAt,
                    "Embedding index reindex failed"
            ));
            log.error("event=embedding_index_reindex_error index=file_embeddings_embedding_idx durationMs={} message={}",
                    Duration.between(startedAt, completedAt).toMillis(), ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }
}
