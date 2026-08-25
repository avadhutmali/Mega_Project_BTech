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
    public void tick() {
        expireStaleNodes();
        assignQueuedJobs();
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
                    job.setStatus(JobStatus.QUEUED);
                    job.setAssignedNode(null);
                }
            }
        }
    }

    private void assignQueuedJobs() {
        for (Job job : jobStore.byStatus(JobStatus.QUEUED)) {
            Node target = firstFit(job);
            if (target == null) {
                continue; // no node has room right now - stays QUEUED, tried again next tick
            }

            target.setCpuFree(target.getCpuFree() - job.getCpuReq());
            target.setRamFreeMb(target.getRamFreeMb() - job.getRamReqMb());

            job.setAssignedNode(target.getId());
            job.setStatus(JobStatus.ASSIGNED);
        }
    }

    private Node firstFit(Job job) {
        for (Node node : nodeStore.all()) {
            if (node.getStatus() != NodeStatus.ONLINE) {
                continue;
            }
            if (node.getCpuFree() >= job.getCpuReq() && node.getRamFreeMb() >= job.getRamReqMb()) {
                return node;
            }
        }
        return null;
    }
}
