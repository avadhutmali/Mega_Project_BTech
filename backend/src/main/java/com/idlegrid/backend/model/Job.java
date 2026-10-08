package com.idlegrid.backend.model;

public class Job {
    private String id;
    private int cpuReq;
    private int ramReqMb;
    private String command;
    private JobStatus status = JobStatus.QUEUED;
    private String assignedNode;
    private String result;
    private Integer sshPort;
    private String nodeIp;
    private String targetNodeId;
    private int durationMinutes;


    public Job() {
    }

    public Job(String id, int cpuReq, int ramReqMb, String command, String targetNodeId, int durationMinutes) {
        this.id = id;
        this.cpuReq = cpuReq;
        this.ramReqMb = ramReqMb;
        this.command = command;
        this.status = JobStatus.QUEUED;
        this.targetNodeId = targetNodeId;
        this.durationMinutes = durationMinutes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCpuReq() {
        return cpuReq;
    }

    public void setCpuReq(int cpuReq) {
        this.cpuReq = cpuReq;
    }

    public int getRamReqMb() {
        return ramReqMb;
    }

    public void setRamReqMb(int ramReqMb) {
        this.ramReqMb = ramReqMb;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getAssignedNode() {
        return assignedNode;
    }

    public void setAssignedNode(String assignedNode) {
        this.assignedNode = assignedNode;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Integer getSshPort() {
        return sshPort;
    }

    public void setSshPort(Integer sshPort) {
        this.sshPort = sshPort;
    }

    public String getNodeIp() {
        return nodeIp;
    }

    public void setNodeIp(String nodeIp) {
        this.nodeIp = nodeIp;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
}
