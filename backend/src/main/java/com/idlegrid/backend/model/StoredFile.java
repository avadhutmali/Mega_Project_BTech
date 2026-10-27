package com.idlegrid.backend.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Persistent metadata for a file uploaded to IdleGrid shared storage. */
public class StoredFile {
    private String key;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private Instant createdAt;
    private String status;
    private List<String> replicaNodeIds = new ArrayList<>();

    public StoredFile() {}
    public StoredFile(String key, String fileName, String contentType, long sizeBytes) {
        this.key = key;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now();
        this.status = "REPLICATING";
    }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getReplicaNodeIds() { return replicaNodeIds; }
    public void setReplicaNodeIds(List<String> replicaNodeIds) { this.replicaNodeIds = replicaNodeIds; }
}
