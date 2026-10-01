package com.idlegrid.backend.controller;

import com.idlegrid.backend.dto.JobCompleteRequest;
import com.idlegrid.backend.dto.JobRunningRequest;
import com.idlegrid.backend.dto.JobSubmitRequest;
import com.idlegrid.backend.dto.JobSubmitResponse;
import com.idlegrid.backend.model.Job;
import com.idlegrid.backend.model.JobStatus;
import com.idlegrid.backend.model.Node;
import com.idlegrid.backend.store.JobStore;
import com.idlegrid.backend.store.NodeStore;
import com.idlegrid.backend.scheduler.SchedulerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobStore jobStore;
    private final NodeStore nodeStore;
    private final SchedulerService schedulerService;

    public JobController(JobStore jobStore, NodeStore nodeStore, SchedulerService schedulerService) {
        this.jobStore = jobStore;
        this.nodeStore = nodeStore;
        this.schedulerService = schedulerService;
    }

    /** Dashboard polls this every 2 s to render the jobs table. */
    @GetMapping
    public Collection<Job> all() {
        return jobStore.all();
    }

    /** Test client / frontend -> Master. Submits and immediately attempts first-fit assignment. */
    @PostMapping("/submit")
    public JobSubmitResponse submit(@RequestBody JobSubmitRequest req) {
        Job job = jobStore.create(req.cpuReq(), req.ramReqMb(), req.command());
        schedulerService.scheduleNow();

        Node assignedNode = job.getAssignedNode() == null ? null : nodeStore.get(job.getAssignedNode());
        return new JobSubmitResponse(
                job.getId(),
                job.getStatus().name(),
                job.getAssignedNode(),
                assignedNode == null ? null : assignedNode.getIp(),
                assignedNode == null ? null : assignedNode.getSshCommand());
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

    /** Agent -> Master, when the job's container has successfully started and port is mapped. */
    @PostMapping("/{id}/running")
    public ResponseEntity<Job> running(@PathVariable String id, @RequestBody JobRunningRequest req) {
        Job job = jobStore.get(id);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        job.setStatus(JobStatus.RUNNING);
        job.setSshPort(req.sshPort());

        Node node = nodeStore.get(req.nodeId());
        if (node != null) {
            job.setNodeIp(node.getIp());
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
