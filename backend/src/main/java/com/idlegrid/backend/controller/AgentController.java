package com.idlegrid.backend.controller;

import com.idlegrid.backend.model.Job;
import com.idlegrid.backend.model.JobStatus;
import com.idlegrid.backend.dto.StorageCompleteRequest;
import com.idlegrid.backend.dto.StorageTask;
import com.idlegrid.backend.model.StoredFile;
import com.idlegrid.backend.service.StorageService;
import com.idlegrid.backend.store.JobStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final JobStore jobStore;
    private final StorageService storageService;

    public AgentController(JobStore jobStore, StorageService storageService) {
        this.jobStore = jobStore;
        this.storageService = storageService;
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

    @GetMapping("/{nodeId}/storage/assignment")
    public ResponseEntity<StorageTask> storageAssignment(@PathVariable String nodeId) {
        StorageTask task = storageService.nextTask(nodeId);
        return task == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(task);
    }

    @GetMapping("/storage/{key}/content")
    public ResponseEntity<org.springframework.core.io.Resource> replicaContent(@PathVariable String key) {
        StoredFile file = storageService.get(key);
        java.nio.file.Path path = storageService.contentPath(key);
        if (file == null || path == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(new org.springframework.core.io.FileSystemResource(path));
    }

    @PostMapping("/{nodeId}/storage/{key}/complete")
    public ResponseEntity<Void> storageComplete(@PathVariable String nodeId, @PathVariable String key,
                                                @RequestBody StorageCompleteRequest request) {
        storageService.replicaComplete(nodeId, key, request.success());
        return ResponseEntity.noContent().build();
    }
}
