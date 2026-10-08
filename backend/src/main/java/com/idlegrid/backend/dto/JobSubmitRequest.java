package com.idlegrid.backend.dto;

/**
 * Body of POST /jobs/submit.
 * targetNodeId: optional — pin the job to a specific node (used by the "Book PC" feature).
 * durationMinutes: how long the SSH session should stay alive (max 1440 = 24h).
 *                  When > 0, the backend auto-builds the command: /usr/sbin/sshd && sleep Xs
 */
public record JobSubmitRequest(
        int cpuReq,
        int ramReqMb,
        String command,
        String targetNodeId,
        int durationMinutes
) {
}
