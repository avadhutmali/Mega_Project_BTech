package com.idlegrid.backend.dto;

import java.time.Instant;

public record BookingRequest(
        String nodeId,
        String requester,
        Instant startAt,
        Instant endAt,
        int cpuReq,
        int ramReqMb
) {
}