package com.idlegrid.backend.dto;

import java.time.Instant;
import java.util.List;

public record DistributedTrainingStatus(
        String runId,
        String status,
        int workerCount,
        String coordinatorIp,
        int coordinatorPort,
        String outputFile,
        List<TrainingWorkerStatus> workers,
        String artifactUrl,
        String error,
        Instant createdAt
) {}
