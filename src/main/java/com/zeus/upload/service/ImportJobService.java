package com.zeus.upload.service;

import com.zeus.upload.domain.ImportJob;
import com.zeus.upload.domain.ImportResult;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

@Service
public class ImportJobService {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, ImportJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();

    public ImportJob submit(Supplier<ImportResult> task) {
        ImportJob job = new ImportJob();
        jobs.put(job.getId(), job);
        futures.put(job.getId(), executor.submit(() -> {
            job.markRunning();
            try {
                ImportResult result = task.get();
                if (Thread.currentThread().isInterrupted()) {
                    job.markCancelled();
                } else if (result != null && result.isSuccess()) {
                    job.markSucceeded(result);
                } else {
                    job.markFailed(result == null ? "Import returned no result" : result.getMessage(), result);
                }
            } catch (Exception ex) {
                job.markFailed(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), null);
            } finally {
                futures.remove(job.getId());
            }
        }));
        return job;
    }

    public ImportJob get(String id) {
        return jobs.get(id);
    }

    public List<ImportJob> list() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(ImportJob::getCreatedAt).reversed())
                .toList();
    }

    public boolean cancel(String id) {
        ImportJob job = jobs.get(id);
        Future<?> future = futures.get(id);
        if (job == null || future == null || job.getStatus().ordinal() >= 2) {
            return false;
        }
        boolean cancelled = future.cancel(true);
        if (cancelled) {
            job.markCancelled();
        }
        return cancelled;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
