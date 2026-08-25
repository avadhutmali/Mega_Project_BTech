package com.idlegrid.backend.dto;

/**
 * Body of POST /jobs/{id}/complete.
 * Sent by the Agent once a job's container has finished (or failed).
 */
public record JobCompleteRequest(
        String result,
        boolean success
) {
}
