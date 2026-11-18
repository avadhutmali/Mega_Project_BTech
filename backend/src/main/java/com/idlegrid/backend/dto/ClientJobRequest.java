package com.idlegrid.backend.dto;

public record ClientJobRequest(double cpuReq, int ramReqMb, String command) {}
