package com.idlegrid.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlegrid.backend.dto.ClientJobRequest;
import com.idlegrid.backend.model.Job;
import com.idlegrid.backend.model.JobStatus;
import com.idlegrid.backend.scheduler.SchedulerService;
import com.idlegrid.backend.store.JobStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClientJobService {
    private final JobStore jobs;
    private final SchedulerService scheduler;
    private final ObjectMapper mapper;
    private final Path historyFile;
    private final Map<String, Map<String, Job>> history = new ConcurrentHashMap<>();

    public ClientJobService(JobStore jobs, SchedulerService scheduler,
                            ObjectMapper mapper,
                            @Value("${idlegrid.storage.directory:./storage}") String storageDirectory) {
        this.jobs = jobs;
        this.scheduler = scheduler;
        this.mapper = mapper;
        this.historyFile = Paths.get(storageDirectory).toAbsolutePath().normalize().resolve("client-jobs.json");
    }

    @PostConstruct
    void loadHistory() {
        try {
            Files.createDirectories(historyFile.getParent());
            if (!Files.isRegularFile(historyFile)) return;
            Map<String, Map<String, Job>> restored = mapper.readValue(historyFile.toFile(), new TypeReference<>() {});
            if (restored == null) return;
            restored.forEach((username, jobsForUser) -> {
                Map<String, Job> userHistory = new ConcurrentHashMap<>(jobsForUser);
                for (Job job : userHistory.values()) {
                    if (isActive(job)) {
                        job.setStatus(JobStatus.FAILED);
                        job.setResult("Master restarted before this job completed; the previous run was interrupted.");
                    }
                }
                history.put(username, userHistory);
            });
            saveHistory();
        } catch (Exception e) {
            throw new IllegalStateException("Could not restore student job history from " + historyFile, e);
        }
    }

    public synchronized List<Job> list(String username) {
        Map<String, Job> userHistory = history.getOrDefault(username, Map.of());
        for (var entry : userHistory.entrySet()) {
            Job live = jobs.get(entry.getKey());
            if (live != null) userHistory.put(entry.getKey(), live);
        }
        saveHistory();
        return userHistory.values().stream()
                .sorted(Comparator.comparing(Job::getCreatedAt).reversed()).toList();
    }

    public Job submit(String username, ClientJobRequest request) {
        if (request == null || !Double.isFinite(request.cpuReq()) || request.cpuReq() <= 0
                || request.ramReqMb() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CPU and RAM requests must be positive; placement uses current lab capacity.");
        }
        String command = request.command() == null ? "" : request.command().trim();
        if (command.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a command to run.");
        }
        synchronized (this) {
            Map<String, Job> owned = history.computeIfAbsent(username, ignored -> new ConcurrentHashMap<>());
            Job job = jobs.create(request.cpuReq(), request.ramReqMb(), command, null, 0);
            owned.put(job.getId(), job);
            saveHistory();
            scheduler.scheduleNow();
            return job;
        }
    }

    public Job get(String username, String id) {
        Map<String, Job> userHistory = history.get(username);
        if (userHistory == null || !userHistory.containsKey(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        Job job = jobs.get(id);
        return job == null ? userHistory.get(id) : job;
    }

    public Job cancel(String username, String id) {
        Job job = get(username, id);
        synchronized (job) {
            if (job.getStatus() == JobStatus.CANCELLED) return job;
            if (job.getStatus() == JobStatus.DONE || job.getStatus() == JobStatus.FAILED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This job has already finished.");
            }
            job.setCancelRequested(true);
            if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.ASSIGNED) {
                job.setStatus(JobStatus.CANCELLED);
                job.setResult("Cancelled by student before the job started.");
            }
            synchronized (this) { saveHistory(); }
            return job;
        }
    }

    @Scheduled(fixedDelayString = "${idlegrid.client.history.snapshot-ms:2000}")
    public synchronized void snapshotHistory() {
        for (Map<String, Job> userHistory : history.values()) {
            for (var entry : userHistory.entrySet()) {
                Job live = jobs.get(entry.getKey());
                if (live != null) userHistory.put(entry.getKey(), live);
            }
        }
        saveHistory();
    }

    private boolean isActive(Job job) {
        return job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.ASSIGNED
                || job.getStatus() == JobStatus.RUNNING;
    }

    private synchronized void saveHistory() {
        try {
            Path temporary = historyFile.resolveSibling(historyFile.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), history);
            try {
                Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist student job history", e);
        }
    }
}
