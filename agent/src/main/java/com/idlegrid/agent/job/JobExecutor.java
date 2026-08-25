package com.idlegrid.agent.job;

import com.idlegrid.agent.config.AgentConfig;
import com.idlegrid.agent.util.HttpUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Executes jobs inside resource-capped Docker containers.
 *
 * <h3>Execution flow (Step 7)</h3>
 * <ol>
 *   <li>Prepare a local workspace directory for this job's files.</li>
 *   <li>{@code docker run -d} — detached mode; Docker prints the container ID to stdout,
 *       which we capture immediately so we can kill it on eviction.</li>
 *   <li>{@code docker logs -f} — follow the container logs, streaming output to a
 *       per-job file under {@code logs/sessions/<jobId>.log}.</li>
 *   <li>{@code docker wait} — blocks until the container exits, returns the exit code.</li>
 *   <li>Report to Master via {@code POST /jobs/{id}/complete}.</li>
 * </ol>
 *
 * <h3>Eviction (Step 9)</h3>
 * {@link #evict()} is called by {@link com.idlegrid.agent.eviction.EvictionManager} the instant
 * the user returns from idle (IDLE → ACTIVE).  It:
 * <ol>
 *   <li>Sets the {@code evicted} flag so {@code executeBlocking} won't report a second time.</li>
 *   <li>Calls {@code docker kill <containerId>} — this causes the running
 *       {@code docker logs -f} and {@code docker wait} to return immediately.</li>
 *   <li>Reports the eviction to the Master via {@code POST /jobs/{id}/complete}
 *       with {@code success=false}.</li>
 * </ol>
 *
 * <h3>Orphan cleanup (Step 10)</h3>
 * All containers are started with label {@code idlegrid.session=<jobId>}.
 * {@link #cleanupOrphanedContainers()} is called at agent startup to kill any
 * containers left over from a previous crash.
 *
 * <h3>Resource caps</h3>
 * <pre>
 *   docker run -d --rm
 *     --cpus=&lt;cpuReq&gt;
 *     --memory=&lt;ramReqMb&gt;m
 *     --network=none
 *     --label=idlegrid.session=&lt;jobId&gt;
 *     -v &lt;workspace&gt;:/workspace
 *     -w /workspace
 *     &lt;image&gt;
 *     sh -c "&lt;command&gt;"
 * </pre>
 */
public class JobExecutor {

    private static final Logger LOG = Logger.getLogger(JobExecutor.class.getName());

    private final AgentConfig config;
    private final HttpUtil    http;

    /**
     * The Docker container ID of the currently running job.
     * Set to non-null when the container starts, cleared to null on completion or eviction.
     */
    private final AtomicReference<String>   runningContainerId = new AtomicReference<>(null);

    /** The currently executing job — used by EvictionManager and the poll loop. */
    private final AtomicReference<JobModel> runningJob         = new AtomicReference<>(null);

    /**
     * Set to {@code true} by {@link #evict()} before killing the container.
     * Prevents {@code executeBlocking} from reporting a duplicate completion event.
     */
    private final AtomicBoolean             evicted            = new AtomicBoolean(false);

    /**
     * The running "docker logs -f" process.
     * Stored here so evict() can destroy it immediately on eviction rather than
     * waiting for the read loop to notice EOF (which already happens quickly, but
     * this is belt-and-suspenders).
     */
    private final AtomicReference<Process>  logsProcess        = new AtomicReference<>(null);

    public JobExecutor(AgentConfig config, HttpUtil http) {
        this.config = config;
        this.http   = http;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /** Returns {@code true} if a job is currently running. */
    public boolean isRunning() {
        return runningJob.get() != null;
    }

    /** Returns the Docker container ID of the running job, or {@code null}. */
    public String getRunningContainerId() {
        return runningContainerId.get();
    }

    /**
     * Starts the job asynchronously in a dedicated daemon thread.
     * Returns immediately. Only one job runs at a time — subsequent calls
     * while a job is running are ignored with a warning.
     */
    public void executeAsync(JobModel job) {
        if (!runningJob.compareAndSet(null, job)) {
            LOG.warning("Already executing job " + runningJob.get().getJobId()
                        + " — ignoring new assignment: " + job.getJobId());
            return;
        }
        evicted.set(false);
        Thread t = new Thread(() -> executeBlocking(job), "job-exec-" + job.getJobId());
        t.setDaemon(true);
        t.start();
    }

    /**
     * Evicts the currently running job because the user returned to an active session.
     *
     * <p>This method is designed to be called from the eviction thread (see
     * {@link com.idlegrid.agent.eviction.EvictionManager}). It is safe to call even if
     * no job is running — it will just log and return.
     */
    public void evict() {
        JobModel job = runningJob.get();
        String   cid = runningContainerId.get();

        if (job == null) {
            LOG.fine("evict() called but no job is running — nothing to do");
            return;
        }

        LOG.info("⚡ Evicting job " + job.getJobId()
                 + " (container=" + cid + ") — user returned to active session");

        // 1. Set flag BEFORE killing so executeBlocking doesn't report a completion race
        evicted.set(true);

        // 2. Kill the container
        if (cid != null) {
            killContainer(cid);
        }

        // 3. Report eviction to Master as a failed completion
        reportComplete(job.getJobId(), false,
                "evicted: user returned to active session at " + Instant.now(), -2);

        // 4. Clear state (executeBlocking will also clear it when it wakes up, but we
        //    clear here so isRunning() immediately returns false for the next poll)
        runningContainerId.set(null);
        runningJob.set(null);

        LOG.info("Eviction complete for job " + job.getJobId());
    }

    /**
     * Kills all IdleGrid Docker containers from a previous agent run.
     * Call this on startup before registering as ONLINE.
     */
    public void cleanupOrphanedContainers() {
        LOG.info("Scanning for orphaned IdleGrid containers from previous run...");
        try {
            // docker ps -a -q --filter label=idlegrid.session
            // Do NOT redirectErrorStream — keep stdout (IDs) separate from stderr (error msgs)
            Process ps = new ProcessBuilder(
                    "docker", "ps", "-a", "-q",
                    "--filter", "label=idlegrid.session")
                    .start();

            int killed = 0;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(ps.getInputStream()))) {
                String id;
                while ((id = r.readLine()) != null) {
                    id = id.trim();
                    // docker ps -q prints only 12-char (short) or 64-char (full) hex IDs
                    // Validate the format so Docker daemon error messages are never treated as IDs
                    if (!id.matches("[0-9a-f]{12,64}")) continue;
                    LOG.warning("Orphaned container found: " + id + " — killing");
                    killContainer(id);
                    new ProcessBuilder("docker", "rm", "-f", id)
                            .start().waitFor(5, TimeUnit.SECONDS);
                    killed++;
                }
            }
            // Drain stderr at FINE level (Docker-not-running is normal at startup on dev machines)
            try (BufferedReader err = new BufferedReader(
                    new InputStreamReader(ps.getErrorStream()))) {
                String line;
                while ((line = err.readLine()) != null) {
                    if (!line.isBlank()) LOG.fine("docker ps stderr: " + line);
                }
            }

            if (killed > 0) {
                LOG.warning("Cleaned up " + killed + " orphaned container(s)");
            } else {
                LOG.info("No orphaned containers found — clean startup");
            }

        } catch (Exception e) {
            LOG.warning("Orphan cleanup failed (Docker may not be running yet): "
                        + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private: job execution
    // ──────────────────────────────────────────────────────────────────────────

    private void executeBlocking(JobModel job) {
        LOG.info("Starting execution: " + job);
        Path sessionLogFile = null;

        try {
            // 1. Prepare workspace
            Path workspace = prepareWorkspace(job.getJobId());

            // 2. Prepare session log file
            Path sessionLogDir = Paths.get(config.getLogDir(), "sessions");
            Files.createDirectories(sessionLogDir);
            sessionLogFile = sessionLogDir.resolve(job.getJobId() + ".log");

            // 3. Start container in detached mode
            //    docker run -d prints the container ID to stdout (one line)
            List<String> startCmd = buildDockerCommand(job, workspace);
            LOG.info("Executing: " + String.join(" ", startCmd));

            Process startProc = new ProcessBuilder(startCmd)
                    .directory(new File(System.getProperty("user.dir")))
                    .start();

            String containerId;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(startProc.getInputStream()))) {
                containerId = r.readLine();
            }

            // Check for docker startup errors
            startProc.waitFor(10, TimeUnit.SECONDS);
            if (startProc.exitValue() != 0) {
                String err = new String(startProc.getErrorStream().readAllBytes()).trim();
                throw new RuntimeException("docker run -d failed (exit "
                        + startProc.exitValue() + "): " + err);
            }

            if (containerId == null || containerId.isBlank()) {
                throw new RuntimeException("docker run -d produced no container ID");
            }

            containerId = containerId.trim();
            runningContainerId.set(containerId);
            LOG.info("Container started: " + containerId + " for job " + job.getJobId());

            // 4. Stream logs to per-job file + capture output for result reporting
            //    Store logsProc so evict() can destroy it if needed.
            Process logsProc = new ProcessBuilder("docker", "logs", "-f", containerId)
                    .redirectErrorStream(true) // merge stderr into stdout
                    .start();
            logsProcess.set(logsProc);

            StringBuilder capturedOutput = new StringBuilder();
            try (PrintWriter logWriter = new PrintWriter(
                    Files.newBufferedWriter(sessionLogFile,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(logsProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String stamped = "[" + Instant.now() + "] " + line;
                    logWriter.println(stamped);
                    capturedOutput.append(line).append("\n");
                }
            } finally {
                logsProcess.set(null);
                logsProc.destroy(); // no-op if already exited; belt-and-suspenders cleanup
            }

            // 5. Get exit code via docker wait.
            //    IMPORTANT: --rm is NOT used on docker run, so the container still exists
            //    after it exits and docker wait can collect the exit code reliably.
            //    We do an explicit `docker rm` in the finally block below.
            int exitCode = -1;
            try {
                Process waitProc = new ProcessBuilder("docker", "wait", containerId)
                        .redirectErrorStream(true)
                        .start();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(waitProc.getInputStream()))) {
                    String line = r.readLine();
                    if (line != null && !line.isBlank()) {
                        exitCode = Integer.parseInt(line.trim());
                    }
                }
                waitProc.waitFor(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOG.warning("docker wait failed: " + e.getMessage());
            }

            LOG.info("Job " + job.getJobId() + " finished — exitCode=" + exitCode
                     + " evicted=" + evicted.get());

            // 6. Report result (skip if evict() already reported)
            if (!evicted.get()) {
                boolean success = exitCode == 0;
                reportComplete(job.getJobId(), success,
                        capturedOutput.toString().trim(), exitCode);
            }

        } catch (Exception e) {
            LOG.severe("Job " + job.getJobId() + " threw unexpected exception: " + e.getMessage());
            if (!evicted.get()) {
                reportComplete(job.getJobId(), false,
                        "Agent internal error: " + e.getMessage(), -1);
            }
        } finally {
            // Explicit docker rm — required because we don't use --rm on docker run.
            // (--rm would race with docker wait: container deleted before wait reads exit code.)
            String cid = runningContainerId.get();
            if (cid != null) {
                try {
                    new ProcessBuilder("docker", "rm", "-f", cid)
                            .redirectErrorStream(true)
                            .start()
                            .waitFor(5, TimeUnit.SECONDS);
                    LOG.fine("Removed container: " + cid);
                } catch (Exception e) {
                    LOG.fine("docker rm " + cid + " failed (may already be gone): " + e.getMessage());
                }
            }
            // Always clear running state so the next poll can accept a new job
            runningContainerId.set(null);
            runningJob.set(null);
            LOG.info("Job slot cleared — ready for next assignment");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private: helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds the {@code docker run -d} command with all resource caps applied.
     *
     * <p>Resource flags:
     * <ul>
     *   <li>{@code --cpus} — fractional CPU cores (e.g. 1.50 = 1.5 cores)</li>
     *   <li>{@code --memory} — hard RAM limit (container is OOM-killed if exceeded)</li>
     *   <li>{@code --network=none} — no network inside the container (security)</li>
     *   <li>{@code --label=idlegrid.session=<id>} — for orphan detection on restart</li>
     *   <li>{@code -v workspace:/workspace} — bind-mount job files</li>
     * </ul>
     *
     * <p>The command is always wrapped in {@code sh -c "..."} so shell features
     * (pipes, redirects, semicolons) work as expected.
     */
    private List<String> buildDockerCommand(JobModel job, Path workspace) {
        String image  = (job.getImage() != null && !job.getImage().isBlank())
                        ? job.getImage()
                        : config.getDefaultDockerImage();
        String cpuStr = String.format("%.2f", Math.max(0.10, job.getCpuReq()));
        String memStr = job.getRamReqMb() > 0 ? job.getRamReqMb() + "m" : "512m";

        // Docker Desktop on Windows can access Windows paths directly.
        // Convert backslashes for Docker CLI compatibility.
        String wsPath = workspace.toAbsolutePath().toString().replace('\\', '/');

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");
        // NOTE: do NOT add --rm here.
        // --rm causes Docker to delete the container the moment it exits, which races with
        // `docker wait` and causes it to fail (exit code becomes -1, job reported FAILED).
        // We do an explicit `docker rm -f` in the finally block instead.
        cmd.add("--cpus=" + cpuStr);
        cmd.add("--memory=" + memStr);
        cmd.add("--network=none");
        cmd.add("--label=idlegrid.session=" + job.getJobId());
        cmd.add("-v"); cmd.add(wsPath + ":/workspace");
        cmd.add("-w"); cmd.add("/workspace");
        cmd.add(image);
        cmd.add("sh"); cmd.add("-c"); cmd.add(job.getCommand());

        return cmd;
    }

    private Path prepareWorkspace(String jobId) throws IOException {
        Path workspace = Paths.get(config.getWorkspaceDir(), jobId);
        Files.createDirectories(workspace);
        return workspace;
    }

    private void killContainer(String containerId) {
        try {
            Process kill = new ProcessBuilder("docker", "kill", containerId)
                    .redirectErrorStream(true)
                    .start();
            kill.waitFor(5, TimeUnit.SECONDS);
            LOG.info("Killed container: " + containerId);
        } catch (Exception e) {
            LOG.warning("docker kill " + containerId + " failed: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private: report to Master
    // ──────────────────────────────────────────────────────────────────────────

    private void reportComplete(String jobId, boolean success, String result, int exitCode) {
        // Sanitise result string for embedding in JSON
        String safe = result == null ? "" : result
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        if (safe.length() > 4096) {
            safe = safe.substring(0, 4096) + "\\n...[truncated]";
        }

        String body = String.format("""
                {
                  "result":   "%s",
                  "success":  %b,
                  "exitCode": %d,
                  "nodeId":   "%s"
                }""", safe, success, exitCode, config.getNodeId());

        var response = http.post("/jobs/" + jobId + "/complete", body);
        if (response != null &&
                (response.statusCode() == 200 || response.statusCode() == 204)) {
            LOG.info("Reported job " + jobId + " → " + (success ? "DONE" : "FAILED/EVICTED"));
        } else {
            int code = response != null ? response.statusCode() : -1;
            LOG.warning("Failed to report job " + jobId
                        + " completion — Master returned HTTP " + code);
        }
    }
}
