package com.idlegrid.backend.controller;

import com.idlegrid.backend.dto.JobCompleteRequest;
import com.idlegrid.backend.dto.JobSubmitRequest;
import com.idlegrid.backend.dto.JobSubmitResponse;
import com.idlegrid.backend.model.Job;
import com.idlegrid.backend.model.JobStatus;
import com.idlegrid.backend.model.Node;
import com.idlegrid.backend.store.JobStore;
import com.idlegrid.backend.store.NodeStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobStore jobStore;
    private final NodeStore nodeStore;

    public JobController(JobStore jobStore, NodeStore nodeStore) {
        this.jobStore = jobStore;
        this.nodeStore = nodeStore;
    }

    /** Dashboard polls this every 2 s to render the jobs table. */
    @GetMapping
    public Collection<Job> all() {
        return jobStore.all();
    }

    /** Test client / (later) frontend -> Master. Submits a new job, starts it QUEUED. */
    @PostMapping("/submit")
    public JobSubmitResponse submit(@RequestBody JobSubmitRequest req) {
        Job job = jobStore.create(req.cpuReq(), req.ramReqMb(), req.command());
        return new JobSubmitResponse(job.getId());
    }

    /** Poll this to watch a job move QUEUED -> ASSIGNED -> RUNNING -> DONE/FAILED. */
    @GetMapping("/{id}/status")
    public ResponseEntity<Job> status(@PathVariable String id) {
        Job job = jobStore.get(id);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }

    /** Agent -> Master, once the job's container has finished (or failed). */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Job> complete(@PathVariable String id, @RequestBody JobCompleteRequest req) {
        Job job = jobStore.get(id);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        job.setResult(req.result());
        job.setStatus(req.success() ? JobStatus.DONE : JobStatus.FAILED);

        // free up the capacity this job was holding on its assigned node
        if (job.getAssignedNode() != null) {
            Node node = nodeStore.get(job.getAssignedNode());
            if (node != null) {
                node.setCpuFree(node.getCpuFree() + job.getCpuReq());
                node.setRamFreeMb(node.getRamFreeMb() + job.getRamReqMb());
            }
        }

        return ResponseEntity.ok(job);
    }
}
