package com.sulaksono.egineeringdataservice.service;

import com.sulaksono.egineeringdataservice.dto.EmbeddingIndexMaintenanceStatus;
import com.sulaksono.egineeringdataservice.exception.EmbeddingIndexMaintenanceInProgressException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmbeddingIndexMaintenanceServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AsyncTaskExecutor executor;
    private EmbeddingIndexMaintenanceService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        executor = mock(AsyncTaskExecutor.class);
        service = new EmbeddingIndexMaintenanceService(jdbcTemplate, executor);
    }

    @Test
    void startsReindexAndRecordsSuccess() {
        EmbeddingIndexMaintenanceStatus accepted = service.startReindex();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());

        assertThat(accepted.state()).isEqualTo(EmbeddingIndexMaintenanceStatus.State.RUNNING);
        assertThat(accepted.startedAt()).isNotNull();
        assertThat(accepted.completedAt()).isNull();

        task.getValue().run();

        verify(jdbcTemplate).execute(EmbeddingIndexMaintenanceService.REINDEX_SQL);
        assertThat(service.status().state()).isEqualTo(EmbeddingIndexMaintenanceStatus.State.SUCCEEDED);
        assertThat(service.status().startedAt()).isEqualTo(accepted.startedAt());
        assertThat(service.status().completedAt()).isNotNull();
    }

    @Test
    void rejectsAnotherRequestWhileReindexIsQueued() {
        service.startReindex();

        assertThatThrownBy(service::startReindex)
                .isInstanceOf(EmbeddingIndexMaintenanceInProgressException.class);
    }

    @Test
    void recordsDatabaseFailureAndAllowsRetry() {
        service.startReindex();
        ArgumentCaptor<Runnable> firstTask = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(firstTask.capture());
        doThrow(new RuntimeException("database unavailable"))
                .when(jdbcTemplate).execute(EmbeddingIndexMaintenanceService.REINDEX_SQL);

        firstTask.getValue().run();

        assertThat(service.status().state()).isEqualTo(EmbeddingIndexMaintenanceStatus.State.FAILED);
        assertThat(service.status().message()).isEqualTo("Embedding index reindex failed");
        assertThat(service.startReindex().state()).isEqualTo(EmbeddingIndexMaintenanceStatus.State.RUNNING);
    }

    @Test
    void recordsSchedulingFailureAndAllowsRetry() {
        doThrow(new RejectedExecutionException("executor full"))
                .doNothing()
                .when(executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));

        assertThatThrownBy(service::startReindex)
                .isInstanceOf(RejectedExecutionException.class);
        assertThat(service.status().state()).isEqualTo(EmbeddingIndexMaintenanceStatus.State.FAILED);
        assertThat(service.status().message())
                .isEqualTo("Embedding index reindex could not be scheduled");

        assertThat(service.startReindex().state()).isEqualTo(EmbeddingIndexMaintenanceStatus.State.RUNNING);
    }
}
