package com.idlegrid.backend.service;

import com.idlegrid.backend.dto.FederatedTrainingStatus;
import com.idlegrid.backend.dto.FederatedWorkerStatus;
import com.idlegrid.backend.model.FederatedTrainingRun;
import com.idlegrid.backend.model.Job;
import com.idlegrid.backend.model.JobStatus;
import com.idlegrid.backend.model.Node;
import com.idlegrid.backend.model.NodeStatus;
import com.idlegrid.backend.scheduler.SchedulerService;
import com.idlegrid.backend.store.FederatedTrainingStore;
import com.idlegrid.backend.store.JobStore;
import com.idlegrid.backend.store.NodeStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Coordinator-mediated single-round FedAvg demo; workers only contact the Master. */
@Service
public class FederatedTrainingService {
    private static final Pattern SAFE_IMAGE = Pattern.compile("[A-Za-z0-9._/:@-]+");
    private static final int UPDATE_WAIT_SECONDS = 120;
    private static final int MAX_WORKERS = 20;

    private final NodeStore nodeStore;
    private final JobStore jobStore;
    private final FederatedTrainingStore runStore;
    private final SchedulerService scheduler;
    private final Path runsDirectory;

    public FederatedTrainingService(NodeStore nodeStore, JobStore jobStore,
                                    FederatedTrainingStore runStore, SchedulerService scheduler,
                                    @Value("${idlegrid.training.directory:storage/training}") String directory) {
        this.nodeStore = nodeStore;
        this.jobStore = jobStore;
        this.runStore = runStore;
        this.scheduler = scheduler;
        this.runsDirectory = Paths.get(directory).toAbsolutePath().normalize().resolve("federated");
    }

    public FederatedTrainingStatus submit(MultipartFile bundle, int workers, double cpuReq,
                                          int ramReqMb, int localEpochs, String image,
                                          String masterUrl) {
        validate(bundle, workers, cpuReq, ramReqMb, localEpochs, image, masterUrl);
        String runId = UUID.randomUUID().toString();
        Path runDirectory = runsDirectory.resolve(runId).normalize();
        if (!runDirectory.startsWith(runsDirectory)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Path bundlePath = runDirectory.resolve("bundle.zip");
        try {
            Files.createDirectories(runDirectory);
            Files.createDirectories(runDirectory.resolve("updates"));
            bundle.transferTo(bundlePath);
            validateBundle(bundlePath);
        } catch (ResponseStatusException e) {
            deleteTree(runDirectory);
            throw e;
        } catch (Exception e) {
            deleteTree(runDirectory);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The FedAvg bundle must contain fedavg_train.py, fedavg_merge.py, and data/<class> files: " + e.getMessage());
        }

        String workerImage = image == null || image.isBlank()
                ? "ghcr.io/avadhutmali/idlegrid-pytorch-cpu@sha256:74e60bc071f7349b0f7d9656f2105d7238152133e52dc71377b08eaa51883221"
                : image.trim();
        return scheduler.coordinatePlacement(() -> {
            List<Node> selected = selectNodes(workers, cpuReq, ramReqMb);
            if (selected.size() != workers) {
                deleteTree(runDirectory);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Need " + workers + " available PCs with at least " + cpuReq + " free CPU cores and " + ramReqMb + " MB RAM each.");
            }
            FederatedTrainingRun run = new FederatedTrainingRun(runId, masterUrl, workers,
                    workerImage, bundlePath.toString());
            runStore.add(run);
            try {
                for (int rank = 0; rank < selected.size(); rank++) {
                    Node node = selected.get(rank);
                    Job job = jobStore.createAssigned(cpuReq, ramReqMb,
                            workerCommand(run, rank, localEpochs), node.getId(), workerImage, "bridge");
                    job.setDistributedTrainingRunId(runId);
                    job.setNodeIp(node.getIp());
                    run.getWorkerJobs().put(rank, job.getId());
                }
            } catch (RuntimeException e) {
                run.getWorkerJobs().values().forEach(jobStore::remove);
                deleteTree(runDirectory);
                throw e;
            }
            return status(run);
        });
    }

    public List<FederatedTrainingStatus> all() {
        return runStore.all().stream().sorted(Comparator.comparing(FederatedTrainingRun::getCreatedAt).reversed())
                .map(this::status).toList();
    }

    public FederatedTrainingStatus status(String runId) { return status(requireRun(runId)); }

    public Path bundlePath(String runId) { return Paths.get(requireRun(runId).getBundlePath()); }

    public synchronized Path saveUpdate(String runId, int rank, MultipartFile checkpoint) throws IOException {
        FederatedTrainingRun run = requireRun(runId);
        if (rank < 0 || rank >= run.getWorkerCount()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid worker rank.");
        if (checkpoint == null || checkpoint.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Model update is empty.");
        Path directory = Paths.get(run.getBundlePath()).getParent().resolve("updates").normalize();
        Path target = directory.resolve("rank-" + rank + ".pt").normalize();
        if (!target.startsWith(directory)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Files.createDirectories(directory);
        checkpoint.transferTo(target);
        run.getUploadedUpdates().add(rank);
        return target;
    }

    public Path updatePath(String runId, int rank) {
        FederatedTrainingRun run = requireRun(runId);
        if (rank < 0 || rank >= run.getWorkerCount()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid worker rank.");
        Path directory = Paths.get(run.getBundlePath()).getParent().resolve("updates").normalize();
        Path update = directory.resolve("rank-" + rank + ".pt").normalize();
        return run.getUploadedUpdates().contains(rank) && Files.isRegularFile(update) ? update : null;
    }

    /** True only after every rank has uploaded; false with 409 if any worker already failed. */
    public boolean updatesReady(String runId) {
        FederatedTrainingRun run = requireRun(runId);
        for (String jobId : run.getWorkerJobs().values()) {
            Job job = jobStore.get(jobId);
            if (job != null && (job.getStatus() == JobStatus.FAILED || job.getStatus() == JobStatus.CANCELLED)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A worker failed before uploading its update.");
            }
        }
        return run.getUploadedUpdates().size() == run.getWorkerCount();
    }

    public Path saveArtifact(String runId, MultipartFile artifact) throws IOException {
        FederatedTrainingRun run = requireRun(runId);
        if (artifact == null || artifact.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Model artifact is empty.");
        Path directory = Paths.get(run.getBundlePath()).getParent().resolve("artifacts").normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve("model.pt").normalize();
        if (!target.startsWith(directory)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        artifact.transferTo(target);
        run.setArtifactPath(target.toString());
        return target;
    }

    public Path artifactPath(String runId) {
        FederatedTrainingRun run = requireRun(runId);
        if (run.getArtifactPath() == null) return null;
        Path artifact = Paths.get(run.getArtifactPath()).normalize();
        return Files.isRegularFile(artifact) ? artifact : null;
    }

    public void saveComparison(String runId, String kind, Map<String, Object> metrics) {
        if (!"fedavg".equals(kind) && !"baseline".equals(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown comparison metric type.");
        }
        FederatedTrainingRun run = requireRun(runId);
        Map<String, Object> recorded = new java.util.HashMap<>(metrics);
        if ("fedavg".equals(kind)) {
            Instant firstWorkerStart = run.getWorkerJobs().values().stream().map(jobStore::get)
                    .filter(java.util.Objects::nonNull).map(Job::getStartedAt).filter(java.util.Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(run.getCreatedAt());
            recorded.put("distributedWallSeconds", Duration.between(firstWorkerStart, Instant.now()).toMillis() / 1000.0);
            recorded.put("comparisonDefinition", "Elapsed on the Master from the first worker start through completion of FedAvg aggregation, including worker waiting and checkpoint transfers; excludes queue/setup before a worker starts and the later single-PC baseline.");
        }
        run.getComparison().put(kind, Map.copyOf(recorded));
    }

    private FederatedTrainingStatus status(FederatedTrainingRun run) {
        List<FederatedWorkerStatus> workers = new ArrayList<>();
        boolean anyRunning = false, anyFailed = false, allDone = !run.getWorkerJobs().isEmpty();
        boolean allTerminal = !run.getWorkerJobs().isEmpty();
        Instant started = null, completed = null;
        for (var entry : run.getWorkerJobs().entrySet()) {
            Job job = jobStore.get(entry.getValue());
            JobStatus state = job == null ? JobStatus.FAILED : job.getStatus();
            if (job != null && job.getStartedAt() != null && (started == null || job.getStartedAt().isBefore(started))) started = job.getStartedAt();
            if (job != null && job.getCompletedAt() != null && (completed == null || job.getCompletedAt().isAfter(completed))) completed = job.getCompletedAt();
            Long workerDuration = job == null || job.getStartedAt() == null ? null
                    : Duration.between(job.getStartedAt(), job.getCompletedAt() == null ? Instant.now() : job.getCompletedAt()).toMillis();
            Node node = job == null ? null : nodeStore.get(job.getAssignedNode());
            workers.add(new FederatedWorkerStatus(entry.getKey(), job == null ? null : job.getAssignedNode(),
                    node == null ? (job == null ? null : job.getNodeIp()) : node.getIp(), state.name(),
                    job == null ? null : tail(job.getResult(), 4000), job == null ? null : job.getStartedAt(),
                    job == null ? null : job.getCompletedAt(), workerDuration,
                    run.getUploadedUpdates().contains(entry.getKey())));
            anyRunning |= state == JobStatus.RUNNING;
            anyFailed |= state == JobStatus.FAILED || state == JobStatus.CANCELLED;
            allDone &= state == JobStatus.DONE;
            allTerminal &= state == JobStatus.DONE || state == JobStatus.FAILED || state == JobStatus.CANCELLED;
        }
        String state;
        String error = run.getError();
        if (anyFailed && allTerminal) {
            state = "FAILED";
            if (error == null) error = workers.stream().filter(w -> "FAILED".equals(w.status()) || "CANCELLED".equals(w.status()))
                    .map(FederatedWorkerStatus::output).filter(o -> o != null && !o.isBlank()).findFirst()
                    .orElse("A worker failed before the aggregate model was produced.");
        } else if (allDone) {
            state = artifactPathIfPresent(run) != null ? "COMPLETED" : "FAILED";
            if ("FAILED".equals(state) && error == null) error = "Workers finished but the coordinator did not upload model.pt.";
        } else if (anyRunning) state = "RUNNING";
        else if (anyFailed) {
            state = "FAILED";
            if (error == null) error = "A worker failed before the aggregate model was produced.";
        } else state = "STARTING";
        if (error == null && anyFailed) error = workers.stream()
                .filter(w -> "FAILED".equals(w.status()) || "CANCELLED".equals(w.status()))
                .map(FederatedWorkerStatus::output).filter(o -> o != null && !o.isBlank()).findFirst()
                .orElse("A worker failed before the aggregate model was produced.");

        Instant end = "COMPLETED".equals(state) || "FAILED".equals(state) ? completed : null;
        Long duration = end == null ? Duration.between(run.getCreatedAt(), Instant.now()).toMillis()
                : Duration.between(run.getCreatedAt(), end).toMillis();
        String base = "/federated-training/runs/" + run.getId();
        return new FederatedTrainingStatus(run.getId(), state, "FedAvg (one aggregation round)", run.getWorkerCount(),
                workers, "COMPLETED".equals(state) ? base + "/artifact" : null,
                base + "/report", error, run.getCreatedAt(), started, end, duration, Map.copyOf(run.getComparison()));
    }

    private Path artifactPathIfPresent(FederatedTrainingRun run) {
        if (run.getArtifactPath() == null) return null;
        Path path = Paths.get(run.getArtifactPath()).normalize();
        return Files.isRegularFile(path) ? path : null;
    }

    private List<Node> selectNodes(int count, double cpu, int ramMb) {
        return nodeStore.all().stream().filter(n -> n.getStatus() == NodeStatus.ONLINE)
                .filter(n -> n.getIp() != null && !n.getIp().isBlank())
                .filter(n -> !hasActiveJob(n.getId()))
                .filter(n -> availableCpu(n) >= cpu && availableRam(n) >= ramMb)
                .sorted(Comparator.comparingDouble(Node::getCpuFree).reversed()).limit(count).toList();
    }

    private boolean hasActiveJob(String nodeId) {
        return jobStore.all().stream().anyMatch(j -> nodeId.equals(j.getAssignedNode())
                && (j.getStatus() == JobStatus.ASSIGNED || j.getStatus() == JobStatus.RUNNING));
    }
    private double availableCpu(Node node) {
        return Math.max(0, node.getCpuFree() - jobStore.all().stream().filter(j -> node.getId().equals(j.getAssignedNode()))
                .filter(j -> j.getStatus() == JobStatus.ASSIGNED || j.getStatus() == JobStatus.RUNNING).mapToDouble(Job::getCpuReq).sum());
    }
    private long availableRam(Node node) {
        return Math.max(0, node.getRamFreeMb() - jobStore.all().stream().filter(j -> node.getId().equals(j.getAssignedNode()))
                .filter(j -> j.getStatus() == JobStatus.ASSIGNED || j.getStatus() == JobStatus.RUNNING).mapToLong(Job::getRamReqMb).sum());
    }

    private String workerCommand(FederatedTrainingRun run, int rank, int epochs) {
        String root = run.getMasterUrl() + "/federated-training/runs/" + run.getId();
        String rankFile = "update-" + rank + ".pt";
        String readyLoop = "deadline=$(date +%s); deadline=$((deadline + " + UPDATE_WAIT_SECONDS + ")); while :; do "
                + "code=$(curl -sS --connect-timeout 5 --max-time 10 -o /tmp/idlegrid-ready.txt -w '%{http_code}' "
                + shellQuote(root + "/updates-ready") + " || true); "
                + "if [ \"$code\" = 200 ]; then break; fi; "
                + "if [ \"$code\" = 409 ]; then cat /tmp/idlegrid-ready.txt; exit 1; fi; "
                + "now=$(date +%s); if [ \"$now\" -ge \"$deadline\" ]; then echo 'Timed out waiting for every worker update; see worker statuses and logs.'; exit 124; fi; sleep 2; done; ";
        StringBuilder command = new StringBuilder("set -eu; mkdir -p /workspace/idlegrid-fedavg; ")
                .append("curl --connect-timeout 10 --max-time 60 -fsS ").append(shellQuote(root + "/bundle"))
                .append(" -o /workspace/idlegrid-fedavg.zip; unzip -oq /workspace/idlegrid-fedavg.zip -d /workspace/idlegrid-fedavg; cd /workspace/idlegrid-fedavg; ")
                .append("python3 fedavg_train.py --rank ").append(rank).append(" --world-size ").append(run.getWorkerCount())
                .append(" --epochs ").append(epochs).append(" --checkpoint ").append(rankFile).append("; ")
                .append("curl --connect-timeout 10 --max-time 120 -fsS -F ").append(shellQuote("checkpoint=@" + rankFile))
                .append(" ").append(shellQuote(root + "/updates/" + rank)).append("; ");
        if (rank == 0) {
            command.append(readyLoop);
            for (int i = 0; i < run.getWorkerCount(); i++) {
                command.append("curl --connect-timeout 10 --max-time 120 -fsS ")
                        .append(shellQuote(root + "/updates/" + i)).append(" -o update-").append(i).append(".pt; ");
            }
            command.append("python3 fedavg_merge.py --workers ").append(run.getWorkerCount()).append(" --output model.pt; ")
                    .append("curl --connect-timeout 10 --max-time 30 -fsS -H 'Content-Type: application/json' --data-binary @fedavg_report.json ")
                    .append(shellQuote(root + "/comparison/fedavg")).append("; ")
                    .append("python3 fedavg_train.py --rank 0 --world-size 1 --epochs ").append(epochs)
                    .append(" --checkpoint baseline.pt --baseline; ")
                    .append("curl --connect-timeout 10 --max-time 30 -fsS -H 'Content-Type: application/json' --data-binary @baseline_report.json ")
                    .append(shellQuote(root + "/comparison/baseline")).append("; ")
                    .append("curl --connect-timeout 10 --max-time 120 -fsS -F 'file=@model.pt' ")
                    .append(shellQuote(root + "/artifact"));
        }
        return command.toString();
    }

    private void validate(MultipartFile bundle, int workers, double cpu, int ramMb,
                          int epochs, String image, String masterUrl) {
        if (bundle == null || bundle.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a FedAvg ZIP bundle.");
        if (workers < 2 || workers > MAX_WORKERS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose between 2 and " + MAX_WORKERS + " workers.");
        if (!Double.isFinite(cpu) || cpu <= 0 || ramMb < 512) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each worker needs positive CPU and at least 512 MB RAM.");
        if (epochs < 1 || epochs > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Local epochs must be from 1 to 10.");
        if (image != null && !image.isBlank() && !SAFE_IMAGE.matcher(image).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Docker image name.");
        if (masterUrl == null || !masterUrl.matches("https?://[A-Za-z0-9._:-]+")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not determine the worker-reachable Master URL.");
    }

    private void validateBundle(Path bundle) throws IOException {
        boolean train = false, merge = false, data = false;
        long expandedBytes = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(bundle))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 50000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP contains too many entries.");
                String name = entry.getName().replace('\\', '/');
                Path normalized = Paths.get(name).normalize();
                if (name.startsWith("/") || normalized.startsWith("..") || name.contains(":")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP contains an unsafe path.");
                }
                if (!entry.isDirectory() && (name.equals("fedavg_train.py") || name.endsWith("/fedavg_train.py"))) train = true;
                if (!entry.isDirectory() && (name.equals("fedavg_merge.py") || name.endsWith("/fedavg_merge.py"))) merge = true;
                if (!entry.isDirectory() && (name.startsWith("data/") || name.contains("/data/"))) data = true;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > 1024L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uncompressed ZIP may not exceed 1 GB.");
                }
                zip.closeEntry();
            }
        }
        if (!train || !merge || !data) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "ZIP needs fedavg_train.py, fedavg_merge.py, and at least one file under data/.");
    }

    private FederatedTrainingRun requireRun(String id) {
        FederatedTrainingRun run = runStore.get(id);
        if (run == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Federated training run not found.");
        return run;
    }

    private String shellQuote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    private String tail(String value, int max) { return value == null || value.length() <= max ? value : value.substring(value.length() - max); }
    private void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); }
        catch (IOException ignored) {}
    }
}
