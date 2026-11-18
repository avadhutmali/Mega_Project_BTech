package com.idlegrid.backend.model;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;

/** Coordinator-aggregated, single-round FedAvg training run. */
public class FederatedTrainingRun {
    private String id;
    private Instant createdAt = Instant.now();
    private String masterUrl;
    private int workerCount;
    private String image;
    private String bundlePath;
    private final Map<Integer, String> workerJobs = new ConcurrentSkipListMap<>();
    private final Set<Integer> uploadedUpdates = new ConcurrentSkipListSet<>();
    private volatile String artifactPath;
    private volatile String error;
    private final Map<String, Map<String, Object>> comparison = new ConcurrentHashMap<>();

    public FederatedTrainingRun() {}

    public FederatedTrainingRun(String id, String masterUrl, int workerCount,
                                String image, String bundlePath) {
        this.id = id;
        this.masterUrl = masterUrl;
        this.workerCount = workerCount;
        this.image = image;
        this.bundlePath = bundlePath;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getMasterUrl() { return masterUrl; }
    public void setMasterUrl(String masterUrl) { this.masterUrl = masterUrl; }
    public int getWorkerCount() { return workerCount; }
    public void setWorkerCount(int workerCount) { this.workerCount = workerCount; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getBundlePath() { return bundlePath; }
    public void setBundlePath(String bundlePath) { this.bundlePath = bundlePath; }
    public Map<Integer, String> getWorkerJobs() { return workerJobs; }
    public Set<Integer> getUploadedUpdates() { return uploadedUpdates; }
    public String getArtifactPath() { return artifactPath; }
    public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Map<String, Map<String, Object>> getComparison() { return comparison; }
}
