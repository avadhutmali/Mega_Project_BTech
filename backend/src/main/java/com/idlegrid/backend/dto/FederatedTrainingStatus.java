package com.idlegrid.backend.dto;

import java.time.Instant;
import java.util.List;

public record FederatedTrainingStatus(
        String runId,
        String status,
        String strategy,
        int workerCount,
        List<FederatedWorkerStatus> workers,
        String artifactUrl,
        String reportUrl,
        String error,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Long durationMs
) {}
