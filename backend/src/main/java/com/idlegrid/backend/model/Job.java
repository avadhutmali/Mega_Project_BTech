package com.idlegrid.backend.model;

public class Job {
    private String id;
    private int cpuReq;
    private int ramReqMb;
    private String command;
    private JobStatus status = JobStatus.QUEUED;
    private String assignedNode;
    private String result;

    public Job() {
    }

    public Job(String id, int cpuReq, int ramReqMb, String command) {
        this.id = id;
        this.cpuReq = cpuReq;
        this.ramReqMb = ramReqMb;
        this.command = command;
        this.status = JobStatus.QUEUED;
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
}
