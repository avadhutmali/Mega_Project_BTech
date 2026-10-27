package com.idlegrid.backend.scheduler;

import com.idlegrid.backend.model.Job;
import com.idlegrid.backend.model.JobStatus;
import com.idlegrid.backend.model.Node;
import com.idlegrid.backend.model.NodeStatus;
import com.idlegrid.backend.store.JobStore;
import com.idlegrid.backend.store.NodeStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * The Master's scheduling loop.
 *
 * Phase 1: first-fit only. Runs every 3 seconds:
 *   1. mark nodes OFFLINE if their heartbeat has gone stale, and requeue anything
 *      they were running
 *   2. assign QUEUED jobs to the first ONLINE node with enough free capacity
 *
 * Fairness (Dominant Resource Fairness) is a Phase 2 upgrade - see
 * /docs/PROJECT_CONTEXT.md. Don't add it here yet.
 */
@Service
public class SchedulerService {

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(15);

    private final NodeStore nodeStore;
    private final JobStore jobStore;

    public SchedulerService(NodeStore nodeStore, JobStore jobStore) {
        this.nodeStore = nodeStore;
        this.jobStore = jobStore;
    }

    @Scheduled(fixedRate = 3000)
    public synchronized void tick() {
        expireStaleNodes();
        assignQueuedJobs();
    }

    /** Performs an immediate placement pass for callers that need assignment in their response. */
    public synchronized void scheduleNow() {
        assignQueuedJobs();
    }

    /** Run an atomic gang-placement operation against the same lock as normal scheduling. */
    public synchronized <T> T coordinatePlacement(Supplier<T> placement) {
        return placement.get();
    }

    private void expireStaleNodes() {
        Instant now = Instant.now();

        for (Node node : nodeStore.all()) {
            if (node.getStatus() != NodeStatus.ONLINE) {
                continue;
            }
            boolean stale = Duration.between(node.getLastHeartbeat(), now).compareTo(HEARTBEAT_TIMEOUT) > 0;
            if (!stale) {
                continue;
            }

            node.setStatus(NodeStatus.OFFLINE);

            // requeue anything this node was assigned or running
            for (Job job : jobStore.all()) {
                boolean wasOnThisNode = node.getId().equals(job.getAssignedNode());
                boolean inFlight = job.getStatus() == JobStatus.ASSIGNED || job.getStatus() == JobStatus.RUNNING;
                if (wasOnThisNode && inFlight) {
                    if (job.getDistributedTrainingRunId() != null) {
                        job.setStatus(JobStatus.FAILED);
                        job.setResult("Distributed training worker went offline; the DDP group cannot continue.");
                    } else {
                        job.setStatus(JobStatus.QUEUED);
                        job.setAssignedNode(null);
                    }
                }
            }
        }
    }

    private void assignQueuedJobs() {
        for (Job job : jobStore.byStatus(JobStatus.QUEUED)) {
            Node target = pickNode(job);
            if (target == null) {
                continue; // no node available right now — stays QUEUED, tried again next tick
            }

            job.setAssignedNode(target.getId());
            job.setStatus(JobStatus.ASSIGNED);
        }
    }

    /**
     * If the job has a targetNodeId, try to assign it there (and wait if the node
     * is busy/offline — it stays QUEUED until that specific node has room).
     * Otherwise fall back to first-fit across all ONLINE nodes.
     */
    private Node pickNode(Job job) {
        String targetId = job.getTargetNodeId();
        if (targetId != null && !targetId.isBlank()) {
            Node node = nodeStore.get(targetId);
            if (node != null
                    && node.getStatus() == NodeStatus.ONLINE
                    && !hasDistributedTrainingOn(node.getId())
                    && availableCpu(node) >= job.getCpuReq()
                    && availableRam(node) >= job.getRamReqMb()) {
                return node;
            }
            return null; // wait for the specific node to become available
        }
        return firstFit(job);
    }

    private Node firstFit(Job job) {
        for (Node node : nodeStore.all()) {
            if (node.getStatus() != NodeStatus.ONLINE) {
                continue;
            }
            if (hasDistributedTrainingOn(node.getId())) {
                continue;
            }
            if (availableCpu(node) >= job.getCpuReq() && availableRam(node) >= job.getRamReqMb()) {
                return node;
            }
        }
        return null;
    }

    // Heartbeats report live host capacity. Keep that measurement intact and
    // subtract scheduler reservations when placing work, so a later heartbeat
    // cannot accidentally make already-assigned jobs disappear from accounting.
    private double availableCpu(Node node) {
        double reserved = jobStore.all().stream()
                .filter(job -> node.getId().equals(job.getAssignedNode()))
                .filter(job -> job.getStatus() == JobStatus.ASSIGNED || job.getStatus() == JobStatus.RUNNING)
                .mapToDouble(Job::getCpuReq).sum();
        return Math.max(0, node.getCpuFree() - reserved);
    }

    private long availableRam(Node node) {
        long reserved = jobStore.all().stream()
                .filter(job -> node.getId().equals(job.getAssignedNode()))
                .filter(job -> job.getStatus() == JobStatus.ASSIGNED || job.getStatus() == JobStatus.RUNNING)
                .mapToLong(Job::getRamReqMb).sum();
        return Math.max(0, node.getRamFreeMb() - reserved);
    }

    private boolean hasDistributedTrainingOn(String nodeId) {
        return jobStore.all().stream().anyMatch(job -> nodeId.equals(job.getAssignedNode())
                && job.getDistributedTrainingRunId() != null
                && (job.getStatus() == JobStatus.ASSIGNED || job.getStatus() == JobStatus.RUNNING));
    }
}
