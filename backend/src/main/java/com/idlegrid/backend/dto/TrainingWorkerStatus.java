package com.idlegrid.backend.dto;

public record TrainingWorkerStatus(int rank, String nodeId, String nodeIp, String status, String output) {}
