package com.idlegrid.backend.model;

import java.time.Instant;

public class Booking {
    private String id;
    private String nodeId;
    private String requester;
    private Instant startAt;
    private Instant endAt;
    private int cpuReq;
    private int ramReqMb;

    public Booking() {
    }

    public Booking(String id, String nodeId, String requester, Instant startAt, Instant endAt,
                   int cpuReq, int ramReqMb) {
        this.id = id;
        this.nodeId = nodeId;
        this.requester = requester;
        this.startAt = startAt;
        this.endAt = endAt;
        this.cpuReq = cpuReq;
        this.ramReqMb = ramReqMb;
    }

    public String getId() { return id; }
    public String getNodeId() { return nodeId; }
    public String getRequester() { return requester; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public int getCpuReq() { return cpuReq; }
    public int getRamReqMb() { return ramReqMb; }
}