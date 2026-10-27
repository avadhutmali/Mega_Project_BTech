package com.idlegrid.backend.model;

import java.time.Instant;

public class Node {
    private String id;
    private String ip;
    private String sshCommand;
    private double cpuTotal;
    private double cpuFree;
    private long ramTotalMb;
    private long ramFreeMb;
    private long diskTotalMb;
    private long diskFreeMb;
    private Instant lastHeartbeat;
    private NodeStatus status = NodeStatus.ONLINE;

    public Node() {
    }

    public Node(String id, String ip, double cpuTotal, double cpuFree, long ramTotalMb, long ramFreeMb,
                long diskTotalMb, long diskFreeMb) {
        this.id = id;
        this.ip = ip;
        this.cpuTotal = cpuTotal;
        this.cpuFree = cpuFree;
        this.ramTotalMb = ramTotalMb;
        this.ramFreeMb = ramFreeMb;
        this.diskTotalMb = diskTotalMb;
        this.diskFreeMb = diskFreeMb;
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

    public String getSshCommand() {
        return sshCommand;
    }

    public void setSshCommand(String sshCommand) {
        this.sshCommand = sshCommand;
    }

    public double getCpuTotal() { return cpuTotal; }
    public void setCpuTotal(double cpuTotal) { this.cpuTotal = cpuTotal; }
    public double getCpuFree() {
        return cpuFree;
    }

    public void setCpuFree(double cpuFree) {
        this.cpuFree = cpuFree;
    }

    public long getRamTotalMb() { return ramTotalMb; }
    public void setRamTotalMb(long ramTotalMb) { this.ramTotalMb = ramTotalMb; }
    public long getRamFreeMb() {
        return ramFreeMb;
    }

    public void setRamFreeMb(long ramFreeMb) {
        this.ramFreeMb = ramFreeMb;
    }

    public long getDiskTotalMb() { return diskTotalMb; }
    public void setDiskTotalMb(long diskTotalMb) { this.diskTotalMb = diskTotalMb; }
    public long getDiskFreeMb() { return diskFreeMb; }
    public void setDiskFreeMb(long diskFreeMb) { this.diskFreeMb = diskFreeMb; }

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
