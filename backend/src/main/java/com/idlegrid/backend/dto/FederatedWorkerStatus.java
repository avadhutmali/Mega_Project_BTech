package com.idlegrid.backend.dto;

import java.time.Instant;

public record FederatedWorkerStatus(int rank, String nodeId, String nodeIp, String status,
                                   String output, Instant startedAt, Instant completedAt,
                                   Long durationMs, boolean updateUploaded) {}
