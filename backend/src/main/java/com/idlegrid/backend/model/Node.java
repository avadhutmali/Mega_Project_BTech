package com.idlegrid.backend.model;

import java.time.Instant;

public class Node {
    private String id;
    private String ip;
    private int cpuFree;
    private int ramFreeMb;
    private Instant lastHeartbeat;
    private NodeStatus status = NodeStatus.ONLINE;

    public Node() {
    }

    public Node(String id, String ip, int cpuFree, int ramFreeMb) {
        this.id = id;
        this.ip = ip;
        this.cpuFree = cpuFree;
        this.ramFreeMb = ramFreeMb;
        this.lastHeartbeat = Instant.now();
        this.status = NodeStatus.ONLINE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getCpuFree() {
        return cpuFree;
    }

    public void setCpuFree(int cpuFree) {
        this.cpuFree = cpuFree;
    }

    public int getRamFreeMb() {
        return ramFreeMb;
    }

    public void setRamFreeMb(int ramFreeMb) {
        this.ramFreeMb = ramFreeMb;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }
}
