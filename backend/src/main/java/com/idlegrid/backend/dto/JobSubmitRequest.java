package com.idlegrid.backend.dto;

/**
 * Body of POST /jobs/submit.
 */
public record JobSubmitRequest(
        int cpuReq,
        int ramReqMb,
        String command
) {
}
