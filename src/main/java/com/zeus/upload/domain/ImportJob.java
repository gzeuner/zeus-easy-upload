package com.zeus.upload.domain;

import java.time.Instant;
import java.util.UUID;

public class ImportJob {

    private final String id = UUID.randomUUID().toString();
    private volatile ImportJobStatus status = ImportJobStatus.QUEUED;
    private volatile int progress;
    private volatile String message = "Queued";
    private volatile ImportResult result;
    private final Instant createdAt = Instant.now();
    private volatile Instant startedAt;
    private volatile Instant finishedAt;

    public String getId() { return id; }
    public ImportJobStatus getStatus() { return status; }
    public int getProgress() { return progress; }
    public String getMessage() { return message; }
    public ImportResult getResult() { return result; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }

    public synchronized void markRunning() {
        status = ImportJobStatus.RUNNING;
        progress = 5;
        message = "Running";
        startedAt = Instant.now();
    }

    public synchronized void markSucceeded(ImportResult result) {
        status = ImportJobStatus.SUCCEEDED;
        progress = 100;
        message = result == null ? "Completed" : result.getMessage();
        this.result = result;
        finishedAt = Instant.now();
    }

    public synchronized void markFailed(String message, ImportResult result) {
        status = ImportJobStatus.FAILED;
        this.message = message;
        this.result = result;
        finishedAt = Instant.now();
    }

    public synchronized void markCancelled() {
        status = ImportJobStatus.CANCELLED;
        message = "Cancelled";
        finishedAt = Instant.now();
    }
}
