package com.idlegrid.backend.dto;

public record JobSubmitResponse(
        String jobId,
        String status,
        String assignedNode,
        String nodeIp,
        String sshCommand
) {
}
