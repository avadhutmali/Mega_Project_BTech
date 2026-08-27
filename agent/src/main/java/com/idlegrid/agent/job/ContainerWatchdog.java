package com.idlegrid.agent.job;

import com.idlegrid.agent.util.HttpUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Watchdog that periodically checks whether a running job's Docker container
 * is still alive, and reports an unexpected exit to the Master if it vanishes.
 *
 * <h3>Why this exists</h3>
 * {@link JobExecutor#executeBlocking} already handles normal job completion and
 * planned eviction. However, if Docker Desktop itself crashes and restarts, or
 * if the container is killed externally (e.g. an OOM condition at the OS level,
 * or a manual {@code docker kill}), the blocking threads may not wake up
 * correctly, or the {@code docker wait} exit code may be ambiguous.
 *
 * <p>This watchdog is an independent safety net: it runs every
 * {@value #INTERVAL_SECONDS}s and calls {@code docker inspect} to verify the
 * container is still in a running state. If the container has disappeared or
 * entered a terminal state ({@code exited} / {@code dead}) while the agent
 * still thinks a job is running, it triggers an immediate FAILED report to the
 * Master so the job can be requeued.
 *
 * <h3>Coordination with JobExecutor</h3>
 * The watchdog reads {@link JobExecutor#isRunning()} and
 * {@link JobExecutor#getRunningContainerId()} (both non-blocking, atomic).
 * If it decides to evict, it calls {@link JobExecutor#evict()} — the same code
 * path used for the idle-eviction case — which sets the eviction flag and
 * handles reporting, preventing a double-report from the blocking thread.
 *
 * <h3>Threading</h3>
 * Scheduled on the shared {@link ScheduledExecutorService} alongside heartbeat
 * and idle checks. Each invocation runs fast (one {@code docker inspect} shell
 * call) so it never blocks the pool.
 */
public class ContainerWatchdog implements Runnable {

    private static final Logger LOG = Logger.getLogger(ContainerWatchdog.class.getName());

    /** How often (seconds) the watchdog checks the running container. */
    public static final int INTERVAL_SECONDS = 15;

    /** Initial delay before first check — give docker run -d time to start. */
    private static final int INITIAL_DELAY_SECONDS = 20;

    private final JobExecutor executor;
    private final HttpUtil    http;

    public ContainerWatchdog(JobExecutor executor, HttpUtil http) {
        this.executor = executor;
        this.http     = http;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Watchdog loop body
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            // Nothing to watch if no job is running
            if (!executor.isRunning()) {
                return;
            }

            String containerId = executor.getRunningContainerId();
            if (containerId == null) {
                // Job is registered as running but container ID not set yet
                // (still starting) — check again next tick
                LOG.fine("Watchdog: job is running but container ID not set yet — will retry");
                return;
            }

            ContainerStatus status = inspectContainer(containerId);

            switch (status) {
                case RUNNING:
                    LOG.fine("Watchdog: container " + containerId.substring(0, 12) + " is running OK");
                    break;

                case EXITED_NONZERO:
                    LOG.warning("Watchdog: container " + containerId.substring(0, 12)
                            + " has exited with non-zero code — triggering eviction");
                    executor.evict();
                    break;

                case MISSING:
                    // Container is completely gone — Docker Desktop may have restarted
                    LOG.warning("Watchdog: container " + containerId.substring(0, 12)
                            + " has DISAPPEARED (Docker Desktop restart? OOM kill?) — triggering eviction");
                    executor.evict();
                    break;

                case DEAD:
                    LOG.warning("Watchdog: container " + containerId.substring(0, 12)
                            + " is in DEAD state — triggering eviction");
                    executor.evict();
                    break;

                case EXITED_ZERO:
                    // Container finished cleanly. The executeBlocking thread will handle
                    // reporting via docker wait — don't interfere.
                    LOG.fine("Watchdog: container " + containerId.substring(0, 12)
                            + " exited cleanly (exit 0) — letting executor handle reporting");
                    break;

                default:
                    LOG.fine("Watchdog: container " + containerId.substring(0, 12)
                            + " status=" + status + " — no action needed");
            }

        } catch (Exception e) {
            // Never crash the scheduled loop
            LOG.warning("ContainerWatchdog threw: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Docker inspect helper
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Runs {@code docker inspect <containerId> --format '{{.State.Status}} {{.State.ExitCode}}'}
     * and maps the result to a {@link ContainerStatus}.
     *
     * <p>Possible Docker state strings: {@code created}, {@code running},
     * {@code paused}, {@code restarting}, {@code removing}, {@code exited}, {@code dead}.
     *
     * @return {@link ContainerStatus#MISSING} if the container no longer exists or
     *         {@code docker inspect} fails for any reason.
     */
    private ContainerStatus inspectContainer(String containerId) {
        try {
            Process proc = new ProcessBuilder(
                    "docker", "inspect",
                    "--format", "{{.State.Status}} {{.State.ExitCode}}",
                    containerId)
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                output = r.readLine();
            }

            boolean finished = proc.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                LOG.warning("docker inspect timed out for container " + containerId.substring(0, 12));
                return ContainerStatus.UNKNOWN;
            }

            if (proc.exitValue() != 0 || output == null || output.isBlank()) {
                // docker inspect returns exit code 1 when the container is not found
                return ContainerStatus.MISSING;
            }

            // Output format: "<status> <exitCode>", e.g. "exited 1" or "running 0"
            String[] parts = output.trim().split("\\s+", 2);
            String dockerState = parts[0].toLowerCase();
            int exitCode = parts.length > 1 ? parseExitCode(parts[1]) : -1;

            return switch (dockerState) {
                case "running"    -> ContainerStatus.RUNNING;
                case "dead"       -> ContainerStatus.DEAD;
                case "exited"     -> exitCode == 0 ? ContainerStatus.EXITED_ZERO
                                                   : ContainerStatus.EXITED_NONZERO;
                case "paused",
                     "restarting",
                     "created"    -> ContainerStatus.TRANSITIONING;
                default           -> ContainerStatus.UNKNOWN;
            };

        } catch (Exception e) {
            LOG.fine("docker inspect failed: " + e.getMessage() + " — treating as MISSING");
            return ContainerStatus.MISSING;
        }
    }

    private int parseExitCode(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Container status enum
    // ──────────────────────────────────────────────────────────────────────────

    enum ContainerStatus {
        /** Container is running normally — no action needed. */
        RUNNING,
        /** Container exited with code 0 — executor will handle reporting. */
        EXITED_ZERO,
        /** Container exited with non-zero code — trigger eviction/failed report. */
        EXITED_NONZERO,
        /** Container is in Docker's "dead" state — trigger eviction. */
        DEAD,
        /** Container ID not found — Docker restarted or OOM kill. */
        MISSING,
        /** Container is starting, pausing, or restarting — check again next tick. */
        TRANSITIONING,
        /** docker inspect call failed or timed out — don't act. */
        UNKNOWN
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scheduling
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Schedules the watchdog on the given executor.
     *
     * <p>Initial delay of {@value #INITIAL_DELAY_SECONDS}s gives the container
     * time to fully start before the first inspection.
     */
    public void scheduleOn(ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(
                this,
                INITIAL_DELAY_SECONDS,
                INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        LOG.info("ContainerWatchdog scheduled every " + INTERVAL_SECONDS
                 + "s (initial delay " + INITIAL_DELAY_SECONDS + "s)");
    }
}
