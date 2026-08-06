package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.domain.ImportJobStatus;
import com.zeus.upload.domain.ImportResult;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ImportJobServiceTest {

    @Test
    void shouldTrackSuccessfulJobLifecycle() {
        var service = new ImportJobService();
        var job = service.submit(() -> ImportResult.success("done", "", 3));

        await(() -> job.getStatus() == ImportJobStatus.SUCCEEDED);
        assertThat(job.getProgress()).isEqualTo(100);
        assertThat(job.getResult().getInsertedRows()).isEqualTo(3);
        service.shutdown();
    }

    @Test
    void shouldTrackFailedJob() {
        var service = new ImportJobService();
        var job = service.submit(() -> ImportResult.failure("failed", "", java.util.List.of()));

        await(() -> job.getStatus() == ImportJobStatus.FAILED);
        assertThat(job.getMessage()).isEqualTo("failed");
        service.shutdown();
    }

    private void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
