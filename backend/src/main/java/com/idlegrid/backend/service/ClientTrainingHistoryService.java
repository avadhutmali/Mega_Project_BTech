package com.idlegrid.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlegrid.backend.dto.DistributedTrainingStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent ownership and status history for student-submitted DDP runs. */
@Service
public class ClientTrainingHistoryService {
    private final ObjectMapper mapper;
    private final DistributedTrainingService training;
    private final Path historyFile;
    private final Map<String, OwnedRun> runs = new ConcurrentHashMap<>();

    public ClientTrainingHistoryService(ObjectMapper mapper, DistributedTrainingService training,
                                        @Value("${idlegrid.storage.directory:./storage}") String storageDirectory) {
        this.mapper = mapper;
        this.training = training;
        this.historyFile = Paths.get(storageDirectory).toAbsolutePath().normalize()
                .resolve("client-training-history.json");
    }

    @PostConstruct
    void loadHistory() {
        try {
            Files.createDirectories(historyFile.getParent());
            if (!Files.isRegularFile(historyFile)) return;
            Map<String, OwnedRun> restored = mapper.readValue(historyFile.toFile(), new TypeReference<>() {});
            if (restored != null) runs.putAll(restored);
            boolean changed = false;
            for (var entry : runs.entrySet()) {
                DistributedTrainingStatus last = entry.getValue().status();
                if (isActive(last.status())) {
                    runs.put(entry.getKey(), new OwnedRun(entry.getValue().username(), interrupted(last)));
                    changed = true;
                }
            }
            if (changed) save();
        } catch (Exception e) {
            throw new IllegalStateException("Could not restore student training history from " + historyFile, e);
        }
    }

    public synchronized void record(String username, DistributedTrainingStatus status) {
        runs.put(status.runId(), new OwnedRun(username, status));
        save();
    }

    public synchronized List<DistributedTrainingStatus> list(String username) {
        refreshOwned(username);
        return runs.values().stream().filter(run -> username.equals(run.username()))
                .map(OwnedRun::status)
                .sorted(Comparator.comparing(DistributedTrainingStatus::createdAt).reversed()).toList();
    }

    public synchronized DistributedTrainingStatus get(String username, String runId) {
        OwnedRun owned = requireOwner(username, runId);
        refreshOne(runId, owned);
        return runs.get(runId).status();
    }

    public synchronized Path artifactPath(String username, String runId) {
        DistributedTrainingStatus status = get(username, runId);
        if (!"COMPLETED".equals(status.status())) return null;
        return training.artifactPathFromHistory(runId, status.outputFile());
    }

    @Scheduled(fixedDelayString = "${idlegrid.client.history.snapshot-ms:2000}")
    public synchronized void snapshot() {
        for (var entry : runs.entrySet()) refreshOne(entry.getKey(), entry.getValue());
    }

    private void refreshOwned(String username) {
        for (var entry : runs.entrySet()) {
            if (username.equals(entry.getValue().username())) refreshOne(entry.getKey(), entry.getValue());
        }
    }

    private void refreshOne(String runId, OwnedRun current) {
        try {
            DistributedTrainingStatus latest = training.status(runId);
            if (!latest.equals(current.status())) {
                runs.put(runId, new OwnedRun(current.username(), latest));
                save();
            }
        } catch (ResponseStatusException missingFromMemoryAfterRestart) {
            // The run remains visible from the persisted snapshot; its active state
            // was marked FAILED at startup because the in-memory worker group is gone.
        }
    }

    private OwnedRun requireOwner(String username, String runId) {
        OwnedRun owned = runs.get(runId);
        if (owned == null || !username.equals(owned.username())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Training run not found");
        }
        return owned;
    }

    private boolean isActive(String status) {
        return "STARTING".equals(status) || "RUNNING".equals(status);
    }

    private DistributedTrainingStatus interrupted(DistributedTrainingStatus previous) {
        return new DistributedTrainingStatus(previous.runId(), "FAILED", previous.workerCount(),
                previous.coordinatorIp(), previous.coordinatorPort(), previous.outputFile(),
                previous.workers(), null,
                "Master restarted while this DDP run was active. The last known status is shown.",
                previous.createdAt());
    }

    private synchronized void save() {
        try {
            Path temporary = historyFile.resolveSibling(historyFile.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), runs);
            try {
                Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist student training history", e);
        }
    }

    public record OwnedRun(String username, DistributedTrainingStatus status) {}
}
