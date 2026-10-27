package com.idlegrid.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlegrid.backend.dto.StorageTask;
import com.idlegrid.backend.model.Node;
import com.idlegrid.backend.model.NodeStatus;
import com.idlegrid.backend.model.StoredFile;
import com.idlegrid.backend.store.NodeStore;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/** Stores a master copy and places up to two replicas on available worker disks. */
@Service
public class StorageService {
    private static final Logger LOG = Logger.getLogger(StorageService.class.getName());
    private static final int REPLICA_COUNT = 2;
    private final ObjectMapper mapper;
    private final NodeStore nodeStore;
    private final Path root;
    private final Path filesDir;
    private final Path manifest;
    private final Map<String, StoredFile> files = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> pending = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedQueue<StorageTask>> tasks = new ConcurrentHashMap<>();
    private final Map<String, Long> inFlight = new ConcurrentHashMap<>();

    public StorageService(ObjectMapper mapper, NodeStore nodeStore,
                          @Value("${idlegrid.storage.directory:./storage}") String directory) {
        this.mapper = mapper;
        this.nodeStore = nodeStore;
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        this.filesDir = root.resolve("files");
        this.manifest = root.resolve("index.json");
    }

    @PostConstruct
    void load() {
        try {
            Files.createDirectories(filesDir);
            if (Files.exists(manifest)) {
                List<StoredFile> restored = mapper.readValue(manifest.toFile(), new TypeReference<>() {});
                restored.forEach(file -> files.put(file.getKey(), file));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not load IdleGrid storage index", e);
        }
    }

    public synchronized StoredFile upload(MultipartFile upload) throws IOException {
        if (upload == null || upload.isEmpty()) throw new IllegalArgumentException("Choose a non-empty file");
        String original = upload.getOriginalFilename() == null ? "file" : upload.getOriginalFilename();
        String name = Paths.get(original).getFileName().toString();
        String key = UUID.randomUUID().toString();
        StoredFile file = new StoredFile(key, name,
                upload.getContentType() == null ? "application/octet-stream" : upload.getContentType(),
                upload.getSize());
        if (Files.getFileStore(filesDir).getUsableSpace() < upload.getSize()) {
            throw new IllegalArgumentException("Master storage does not have enough free disk space");
        }
        upload.transferTo(filesDir.resolve(key));
        files.put(key, file);
        saveIndex();
        refreshReplicas();
        return file;
    }

    public Collection<StoredFile> all() {
        return files.values().stream().sorted(Comparator.comparing(StoredFile::getCreatedAt).reversed()).toList();
    }

    public StoredFile get(String key) { return files.get(key); }

    public Path contentPath(String key) {
        if (!files.containsKey(key)) return null;
        Path path = filesDir.resolve(key).normalize();
        return path.startsWith(filesDir) && Files.isRegularFile(path) ? path : null;
    }

    public synchronized StorageTask nextTask(String nodeId) {
        ConcurrentLinkedQueue<StorageTask> queue = tasks.get(nodeId);
        StorageTask task = queue == null ? null : queue.poll();
        if (task != null) inFlight.put(taskId(nodeId, task.key()), System.currentTimeMillis());
        return task;
    }

    public synchronized void replicaComplete(String nodeId, String key, boolean success) {
        StoredFile file = files.get(key);
        if (file == null) return;
        Set<String> assigned = pending.get(key);
        if (assigned != null) assigned.remove(nodeId);
        inFlight.remove(taskId(nodeId, key));
        if (success && !file.getReplicaNodeIds().contains(nodeId)) {
            file.getReplicaNodeIds().add(nodeId);
            Node node = nodeStore.get(nodeId);
            if (node != null) {
                long usedMb = (file.getSizeBytes() + 1024L * 1024L - 1) / (1024L * 1024L);
                node.setDiskFreeMb(Math.max(0, node.getDiskFreeMb() - usedMb));
            }
        }
        file.setStatus(file.getReplicaNodeIds().size() >= REPLICA_COUNT ? "AVAILABLE" : "REPLICATING");
        saveIndex();
        refreshReplicas();
    }

    /** Rechecks placement after uploads and node heartbeats. */
    public synchronized void refreshReplicas() {
        retryExpiredTasks();
        boolean changed = false;
        List<Node> online = nodeStore.all().stream().filter(n -> n.getStatus() == NodeStatus.ONLINE)
                .sorted(Comparator.comparingLong(Node::getDiskFreeMb).reversed()).toList();
        for (StoredFile file : files.values()) {
            Set<String> outstanding = pending.computeIfAbsent(file.getKey(), ignored -> ConcurrentHashMap.newKeySet());
            for (Node node : online) {
                if (file.getReplicaNodeIds().size() + outstanding.size() >= REPLICA_COUNT) break;
                if (file.getReplicaNodeIds().contains(node.getId()) || outstanding.contains(node.getId())) continue;
                long reservedBytes = pendingBytes(node.getId());
                long freeBytes = Math.max(0, node.getDiskFreeMb()) * 1024L * 1024L;
                if (freeBytes - reservedBytes < file.getSizeBytes()) continue;
                outstanding.add(node.getId());
                tasks.computeIfAbsent(node.getId(), ignored -> new ConcurrentLinkedQueue<>())
                        .add(new StorageTask(file.getKey(), file.getFileName(), file.getSizeBytes()));
                changed = true;
            }
            String nextStatus = file.getReplicaNodeIds().size() >= REPLICA_COUNT ? "AVAILABLE" : "REPLICATING";
            if (!nextStatus.equals(file.getStatus())) {
                file.setStatus(nextStatus);
                changed = true;
            }
        }
        if (changed) saveIndex();
    }

    private void retryExpiredTasks() {
        long now = System.currentTimeMillis();
        long leaseMillis = 60_000;
        for (Map.Entry<String, Long> entry : inFlight.entrySet()) {
            if (now - entry.getValue() < leaseMillis || !inFlight.remove(entry.getKey(), entry.getValue())) continue;
            int separator = entry.getKey().indexOf('|');
            if (separator < 0) continue;
            String nodeId = entry.getKey().substring(0, separator);
            String key = entry.getKey().substring(separator + 1);
            StoredFile file = files.get(key);
            if (file != null && nodeStore.get(nodeId) != null && nodeStore.get(nodeId).getStatus() == NodeStatus.ONLINE) {
                tasks.computeIfAbsent(nodeId, ignored -> new ConcurrentLinkedQueue<>())
                        .add(new StorageTask(file.getKey(), file.getFileName(), file.getSizeBytes()));
            } else {
                Set<String> assigned = pending.get(key);
                if (assigned != null) assigned.remove(nodeId);
            }
        }
    }

    private String taskId(String nodeId, String key) { return nodeId + "|" + key; }

    public Map<String, Object> summary() {
        long total = nodeStore.all().stream().filter(n -> n.getStatus() == NodeStatus.ONLINE)
                .mapToLong(n -> Math.max(0, n.getDiskTotalMb())).sum();
        long free = nodeStore.all().stream().filter(n -> n.getStatus() == NodeStatus.ONLINE)
                .mapToLong(n -> Math.max(0, n.getDiskFreeMb())).sum();
        long stored = files.values().stream().mapToLong(StoredFile::getSizeBytes).sum();
        long masterTotal = 0;
        long masterFree = 0;
        try {
            var masterDisk = Files.getFileStore(filesDir);
            masterTotal = masterDisk.getTotalSpace() / (1024L * 1024L);
            masterFree = masterDisk.getUsableSpace() / (1024L * 1024L);
        } catch (IOException e) {
            LOG.warning("Could not read Master storage capacity: " + e.getMessage());
        }
        return Map.of("onlineNodeDiskTotalMb", total, "onlineNodeDiskFreeMb", free,
                "masterDiskTotalMb", masterTotal, "masterDiskFreeMb", masterFree,
                "fileCount", files.size(), "primaryStoredBytes", stored, "replicaCount", REPLICA_COUNT);
    }

    private long pendingBytes(String nodeId) {
        return pending.entrySet().stream().filter(e -> e.getValue().contains(nodeId))
                .mapToLong(e -> {
                    StoredFile file = files.get(e.getKey());
                    return file == null ? 0 : file.getSizeBytes();
                }).sum();
    }

    private void saveIndex() {
        try {
            Path temporary = manifest.resolveSibling("index.json.tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), new ArrayList<>(files.values()));
            try {
                Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warning("Could not persist storage index: " + e.getMessage());
        }
    }
}
