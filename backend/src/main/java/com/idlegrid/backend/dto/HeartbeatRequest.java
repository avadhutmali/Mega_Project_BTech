package com.idlegrid.backend.dto;

/**
 * Body of POST /nodes/heartbeat.
 * Sent by the Agent every few seconds.
 */
public record HeartbeatRequest(
        String nodeId,
        String ip,
        String sshCommand,
        int cpuFree,
        int ramFreeMb
) {
}
