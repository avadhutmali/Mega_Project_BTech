package com.idlegrid.backend.service;

import com.idlegrid.backend.dto.DistributedTrainingStatus;
import com.idlegrid.backend.dto.TrainingWorkerStatus;
import com.idlegrid.backend.model.DistributedTrainingRun;
import com.idlegrid.backend.model.Job;
import com.idlegrid.backend.model.JobStatus;
import com.idlegrid.backend.model.Node;
import com.idlegrid.backend.model.NodeStatus;
import com.idlegrid.backend.scheduler.SchedulerService;
import com.idlegrid.backend.store.DistributedTrainingStore;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Gang placement and run state for CPU-first PyTorch DistributedDataParallel jobs. */
@Service
public class DistributedTrainingService {
    private static final int MASTER_PORT = 29500;
    private static final Pattern SAFE_COMMAND = Pattern.compile("^[A-Za-z0-9_./-]+(?:\\s+[A-Za-z0-9_./=,:-]+)*$");
    private static final Pattern SAFE_OUTPUT = Pattern.compile("^[A-Za-z0-9_.-]+$");

    private final NodeStore nodeStore;
    private final JobStore jobStore;
    private final DistributedTrainingStore runStore;
    private final SchedulerService scheduler;
    private final Path runsDirectory;

    public DistributedTrainingService(NodeStore nodeStore, JobStore jobStore,
                                      DistributedTrainingStore runStore, SchedulerService scheduler,
                                      @Value("${idlegrid.training.directory:}") String configuredDirectory) {
        this.nodeStore = nodeStore;
        this.jobStore = jobStore;
        this.runStore = runStore;
        this.scheduler = scheduler;
        String directory = configuredDirectory == null || configuredDirectory.isBlank()
                ? Paths.get(System.getProperty("java.io.tmpdir"), "idlegrid-training-runs").toString()
                : configuredDirectory;
        this.runsDirectory = Paths.get(directory).toAbsolutePath().normalize();
    }

    public DistributedTrainingStatus submit(MultipartFile bundle, String command, int workerCount,
                                             double cpuReq, int ramReqMb, String image,
                                             String outputFile, String masterUrl) {
        validateRequest(bundle, command, workerCount, cpuReq, ramReqMb, image, outputFile, masterUrl);

        String runId = UUID.randomUUID().toString();
        Path runDirectory = runsDirectory.resolve(runId).normalize();
        if (!runDirectory.startsWith(runsDirectory)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Path bundlePath = runDirectory.resolve("bundle.zip");
        try {
            Files.createDirectories(runDirectory);
            bundle.transferTo(bundlePath);
            validateBundle(bundlePath, command);
        } catch (ResponseStatusException e) {
            deleteTree(runDirectory);
            throw e;
        } catch (Exception e) {
            deleteTree(runDirectory);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read training ZIP: " + e.getMessage());
        }

        String finalImage = image == null || image.isBlank()
                ? "ghcr.io/avadhutmali/idlegrid-pytorch-cpu:latest" : image.trim();
        String finalOutput = outputFile == null || outputFile.isBlank() ? "model.pt" : outputFile.trim();
        return scheduler.coordinatePlacement(() -> {
            List<Node> selected = selectNodes(workerCount, cpuReq, ramReqMb);
            if (selected.size() != workerCount) {
                deleteTree(runDirectory);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Need " + workerCount + " online PCs with at least " + cpuReq
                                + " free CPU cores and " + ramReqMb + " MB RAM each.");
            }

            Node coordinator = selected.get(0);
            DistributedTrainingRun run = new DistributedTrainingRun(runId, masterUrl,
                    coordinator.getIp(), MASTER_PORT, workerCount, finalImage, finalOutput, bundlePath.toString());
            runStore.add(run);
            try {
                for (int rank = 0; rank < selected.size(); rank++) {
                    Node node = selected.get(rank);
                    String workerCommand = buildWorkerCommand(run, command.trim(), rank);
                    Job job = jobStore.createAssigned(cpuReq, ramReqMb, workerCommand,
                            node.getId(), finalImage, "host");
                    job.setDistributedTrainingRunId(runId);
                    job.setNodeIp(node.getIp());
                    run.getWorkerJobs().put(rank, job.getId());
                }
            } catch (RuntimeException e) {
                run.getWorkerJobs().values().forEach(jobStore::remove);
                runStore.remove(runId);
                deleteTree(runDirectory);
                throw e;
            }
            return status(run);
        });
    }

    public DistributedTrainingStatus status(String runId) {
        DistributedTrainingRun run = runStore.get(runId);
        if (run == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Training run not found");
        return status(run);
    }

    public List<DistributedTrainingStatus> all() {
        return runStore.all().stream()
                .sorted(Comparator.comparing(DistributedTrainingRun::getCreatedAt).reversed())
                .map(this::status).toList();
    }

    public Path bundlePath(String runId) {
        DistributedTrainingRun run = runStore.get(runId);
        return run == null ? null : Paths.get(run.getBundlePath());
    }

    public Path saveArtifact(String runId, MultipartFile artifact) throws IOException {
        DistributedTrainingRun run = runStore.get(runId);
        if (run == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Training run not found");
        if (artifact == null || artifact.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artifact is empty");
        Path dir = Paths.get(run.getBundlePath()).getParent().resolve("artifacts");
        Files.createDirectories(dir);
        Path target = dir.resolve(run.getOutputFile()).normalize();
        if (!target.startsWith(dir)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid artifact filename");
        artifact.transferTo(target);
        run.setArtifactPath(target.toString());
        return target;
    }

    public Path artifactPath(String runId) {
        DistributedTrainingRun run = runStore.get(runId);
        if (run == null || run.getArtifactPath() == null) return null;
        Path path = Paths.get(run.getArtifactPath()).normalize();
        return Files.isRegularFile(path) ? path : null;
    }

    private DistributedTrainingStatus status(DistributedTrainingRun run) {
        List<TrainingWorkerStatus> workers = new ArrayList<>();
        boolean running = false;
        boolean failed = false;
        boolean allDone = true;
        for (var entry : run.getWorkerJobs().entrySet()) {
            Job job = jobStore.get(entry.getValue());
            Node node = job == null ? null : nodeStore.get(job.getAssignedNode());
            JobStatus workerState = job == null ? JobStatus.FAILED : job.getStatus();
            workers.add(new TrainingWorkerStatus(entry.getKey(), job == null ? null : job.getAssignedNode(),
                    node == null ? (job == null ? null : job.getNodeIp()) : node.getIp(), workerState.name(),
                    job == null ? null : tail(job.getResult(), 1600)));
            running |= workerState == JobStatus.RUNNING;
            failed |= workerState == JobStatus.FAILED;
            allDone &= workerState == JobStatus.DONE;
        }

        String state;
        String error = run.getError();
        if (failed) {
            state = "FAILED";
            if (error == null) error = run.getWorkerJobs().values().stream().map(jobStore::get)
                    .filter(j -> j != null && j.getStatus() == JobStatus.FAILED)
                    .map(Job::getResult).filter(r -> r != null && !r.isBlank()).map(r -> tail(r, 500))
                    .findFirst().orElse("A DDP worker failed");
        } else if (allDone && !run.getWorkerJobs().isEmpty()) {
            state = run.getArtifactPath() != null && Files.isRegularFile(Paths.get(run.getArtifactPath()))
                    ? "COMPLETED" : "FAILED";
            if ("FAILED".equals(state)) error = "Workers completed but rank 0 did not upload the model artifact.";
        } else if (running) {
            state = "RUNNING";
        } else {
            state = "STARTING";
        }

        String artifactUrl = "COMPLETED".equals(state) ? "/distributed-training/runs/" + run.getId() + "/artifact" : null;
        return new DistributedTrainingStatus(run.getId(), state, run.getWorkerCount(),
                run.getCoordinatorIp(), run.getCoordinatorPort(), run.getOutputFile(), workers,
                artifactUrl, error, run.getCreatedAt());
    }

    private List<Node> selectNodes(int count, double cpuReq, int ramReqMb) {
        return nodeStore.all().stream()
                .filter(n -> n.getStatus() == NodeStatus.ONLINE && n.getIp() != null && !n.getIp().isBlank())
                .filter(n -> !hasActiveJobOn(n.getId()))
                .filter(n -> availableCpu(n) >= cpuReq && availableRam(n) >= ramReqMb)
                .sorted(Comparator.comparingDouble(Node::getCpuFree).reversed())
                .limit(count).toList();
    }

    private boolean hasActiveJobOn(String nodeId) {
        return jobStore.all().stream().anyMatch(j -> nodeId.equals(j.getAssignedNode())
                && (j.getStatus() == JobStatus.ASSIGNED || j.getStatus() == JobStatus.RUNNING));
    }

    private double availableCpu(Node node) {
        double reserved = jobStore.all().stream().filter(j -> node.getId().equals(j.getAssignedNode()))
                .filter(j -> j.getStatus() == JobStatus.ASSIGNED || j.getStatus() == JobStatus.RUNNING)
                .mapToDouble(Job::getCpuReq).sum();
        return Math.max(0, node.getCpuFree() - reserved);
    }

    private long availableRam(Node node) {
        long reserved = jobStore.all().stream().filter(j -> node.getId().equals(j.getAssignedNode()))
                .filter(j -> j.getStatus() == JobStatus.ASSIGNED || j.getStatus() == JobStatus.RUNNING)
                .mapToLong(Job::getRamReqMb).sum();
        return Math.max(0, node.getRamFreeMb() - reserved);
    }

    private String buildWorkerCommand(DistributedTrainingRun run, String command, int rank) {
        String bundleUrl = run.getMasterUrl() + "/distributed-training/runs/" + run.getId() + "/bundle";
        String artifactUrl = run.getMasterUrl() + "/distributed-training/runs/" + run.getId() + "/artifact";
        String output = shellQuote("/workspace/idlegrid-bundle/" + run.getOutputFile());
        return "set -eu; mkdir -p /workspace/idlegrid-bundle; "
                + "curl -fsS " + shellQuote(bundleUrl) + " -o /workspace/idlegrid-bundle.zip; "
                + "unzip -oq /workspace/idlegrid-bundle.zip -d /workspace/idlegrid-bundle; "
                + "cd /workspace/idlegrid-bundle; "
                + "torchrun --nnodes=" + run.getWorkerCount() + " --nproc-per-node=1 --node-rank=" + rank
                + " --master-addr=" + run.getCoordinatorIp() + " --master-port=" + run.getCoordinatorPort()
                + " --max-restarts=0 " + command + "; "
                + (rank == 0 ? "test -f " + output + "; curl -fsS -F "
                + shellQuote("file=@/workspace/idlegrid-bundle/" + run.getOutputFile()) + " "
                + shellQuote(artifactUrl) + "; " : "");
    }

    private void validateRequest(MultipartFile bundle, String command, int workers,
                                 double cpu, int ram, String image, String outputFile, String masterUrl) {
        if (bundle == null || bundle.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a ZIP bundle containing train.py and your data.");
        if (workers < 2 || workers > 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose 2 or 3 workers for this lab demo.");
        if (!Double.isFinite(cpu) || cpu <= 0 || ram < 512) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each worker needs positive CPU and at least 512 MB RAM.");
        if (command == null || !SAFE_COMMAND.matcher(command.trim()).matches() || !command.trim().split("\\s+")[0].endsWith(".py")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Command must be a Python entrypoint such as train.py --epochs 2.");
        }
        if (image != null && !image.isBlank() && !image.matches("[A-Za-z0-9._/:@-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Docker image name contains unsupported characters.");
        }
        String output = outputFile == null || outputFile.isBlank() ? "model.pt" : outputFile.trim();
        if (!SAFE_OUTPUT.matcher(output).matches() || output.startsWith("/") || output.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Output path must be a safe relative path, for example model.pt.");
        }
        if (masterUrl == null || !masterUrl.matches("https?://[A-Za-z0-9._:-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not determine the Master URL for worker downloads.");
        }
    }

    private void validateBundle(Path bundle, String command) throws IOException {
        String entrypoint = command.trim().split("\\s+")[0];
        boolean hasEntrypoint = false;
        long expandedBytes = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(bundle))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 50000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP contains too many entries.");
                String name = entry.getName().replace('\\', '/');
                Path normalized = Paths.get(name).normalize();
                if (name.startsWith("/") || normalized.startsWith("..") || name.contains(":/")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP contains an unsafe path.");
                }
                if (!entry.isDirectory() && (name.equals(entrypoint) || name.endsWith("/" + entrypoint))) hasEntrypoint = true;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > 1024L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uncompressed ZIP may not exceed 1 GB.");
                }
                zip.closeEntry();
            }
        }
        if (!hasEntrypoint) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP must contain the Python entrypoint named in the command.");
    }

    private String shellQuote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }

    private String tail(String value, int limit) {
        if (value == null || value.length() <= limit) return value;
        return "…" + value.substring(value.length() - limit);
    }

    private void deleteTree(Path root) {
        try {
            if (Files.exists(root)) try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) { }
    }
}
