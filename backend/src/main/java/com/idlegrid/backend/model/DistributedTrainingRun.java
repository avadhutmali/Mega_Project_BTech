package com.idlegrid.backend.model;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/** In-memory state for one gang-scheduled, multi-node DDP run. */
public class DistributedTrainingRun {
    private final String id;
    private final Instant createdAt = Instant.now();
    private final String masterUrl;
    private final String coordinatorIp;
    private final int coordinatorPort;
    private final int workerCount;
    private final String image;
    private final String outputFile;
    private final String bundlePath;
    private final Map<Integer, String> workerJobs = new ConcurrentSkipListMap<>();
    private volatile String artifactPath;
    private volatile String error;

    public DistributedTrainingRun(String id, String masterUrl, String coordinatorIp, int coordinatorPort,
                                  int workerCount, String image, String outputFile, String bundlePath) {
        this.id = id;
        this.masterUrl = masterUrl;
        this.coordinatorIp = coordinatorIp;
        this.coordinatorPort = coordinatorPort;
        this.workerCount = workerCount;
        this.image = image;
        this.outputFile = outputFile;
        this.bundlePath = bundlePath;
    }

    public String getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getMasterUrl() { return masterUrl; }
    public String getCoordinatorIp() { return coordinatorIp; }
    public int getCoordinatorPort() { return coordinatorPort; }
    public int getWorkerCount() { return workerCount; }
    public String getImage() { return image; }
    public String getOutputFile() { return outputFile; }
    public String getBundlePath() { return bundlePath; }
    public Map<Integer, String> getWorkerJobs() { return workerJobs; }
    public String getArtifactPath() { return artifactPath; }
    public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
