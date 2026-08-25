package com.idlegrid.agent.job;

/**
 * Represents a job assignment received from the Master.
 *
 * <p>Field names intentionally mirror the JSON keys expected in the
 * {@code GET /agent/{nodeId}/assignment} response, so the poller's
 * JSON parsing maps naturally without extra transformations.
 */
public class JobModel {

    private final String jobId;
    private final String command;
    private final String image;
    private final double cpuReq;
    private final int    ramReqMb;

    /**
     * @param jobId    unique job identifier assigned by the Master
     * @param command  shell command to run inside the container (e.g. "python3 train.py")
     * @param image    Docker image name, or {@code null} to use the agent's default
     * @param cpuReq   CPU allocation in fractional cores (e.g. 1.5)
     * @param ramReqMb RAM allocation in megabytes
     */
    public JobModel(String jobId, String command, String image, double cpuReq, int ramReqMb) {
        this.jobId    = jobId;
        this.command  = command;
        this.image    = image;
        this.cpuReq   = cpuReq;
        this.ramReqMb = ramReqMb;
    }

    public String getJobId()   { return jobId; }
    public String getCommand() { return command; }
    public String getImage()   { return image; }
    public double getCpuReq()  { return cpuReq; }
    public int    getRamReqMb(){ return ramReqMb; }

    @Override
    public String toString() {
        return "Job{id=" + jobId
               + ", image=" + (image != null ? image : "default")
               + ", cpu=" + cpuReq
               + ", ram=" + ramReqMb + "MB"
               + ", cmd=\"" + command + "\"}";
    }
}
