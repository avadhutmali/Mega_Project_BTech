package com.idlegrid.backend.store;

import com.idlegrid.backend.model.Job;
import com.idlegrid.backend.model.JobStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory job queue / registry. See NodeStore for why there's no database yet.
 */
@Component
public class JobStore {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public Job create(int cpuReq, int ramReqMb, String command) {
        String id = UUID.randomUUID().toString();
        Job job = new Job(id, cpuReq, ramReqMb, command);
        jobs.put(id, job);
        return job;
    }

    public Job get(String id) {
        return jobs.get(id);
    }

    public Collection<Job> all() {
        return jobs.values();
    }

    public Collection<Job> byStatus(JobStatus status) {
        return jobs.values().stream()
                .filter(j -> j.getStatus() == status)
                .toList();
    }
}
