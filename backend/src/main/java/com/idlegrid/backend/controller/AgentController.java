package com.idlegrid.backend.controller;

import com.idlegrid.backend.model.Job;
import com.idlegrid.backend.model.JobStatus;
import com.idlegrid.backend.store.JobStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final JobStore jobStore;

    public AgentController(JobStore jobStore) {
        this.jobStore = jobStore;
    }

    /**
     * Agent polls this to ask "is anything assigned to me right now?"
     * Master doesn't push jobs to Agents - Agents pull, which keeps the
     * Master from needing to hold open connections to every node.
     *
     * Returns the assigned job (and flips it to RUNNING so it isn't handed
     * out again on the next poll), or 204 No Content if nothing's waiting.
     */
    @GetMapping("/{nodeId}/assignment")
    public ResponseEntity<Job> assignment(@PathVariable String nodeId) {
        return jobStore.byStatus(JobStatus.ASSIGNED).stream()
                .filter(j -> nodeId.equals(j.getAssignedNode()))
                .findFirst()
                .map(job -> {
                    job.setStatus(JobStatus.RUNNING);
                    return ResponseEntity.ok(job);
                })
                .orElse(ResponseEntity.noContent().build());
    }
}
