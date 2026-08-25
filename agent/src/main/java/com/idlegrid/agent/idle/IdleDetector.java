package com.idlegrid.agent.idle;

import com.idlegrid.agent.config.AgentConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Periodically checks whether the PC user is idle, and transitions {@link NodeState}
 * between ACTIVE and IDLE accordingly.
 *
 * <h3>Two complementary idle signals</h3>
 * <ol>
 *   <li><b>GetLastInputInfo</b> — milliseconds since the last keyboard or mouse event,
 *       obtained via JNA from {@code user32.dll}.  This is the same mechanism used by
 *       BOINC, Condor, and Windows screensaver triggers.</li>
 *   <li><b>query user</b> — shell command that lists active Windows sessions.
 *       A session in "Active" state is a stronger signal that someone is at the machine
 *       even if they haven't moved the mouse in the last few seconds
 *       (e.g. watching a video).</li>
 * </ol>
 *
 * <h3>Transition logic (Step 5 of plan)</h3>
 * <ul>
 *   <li>→ IDLE: only if idle ms ≥ threshold AND no Active session.  Slow departure.</li>
 *   <li>→ ACTIVE: immediately if idle ms &lt; threshold OR an Active session is detected.
 *       No threshold on the return path — eviction must be instant.</li>
 * </ul>
 *
 * <h3>Empirical logging (Step 4 of plan)</h3>
 * Every state transition is appended to {@code logs/idle_transitions.log} with a
 * timestamp and idle duration.  Run the logger for a day on a real lab PC to study
 * actual idle/active patterns and justify the threshold choice in the thesis.
 */
public class IdleDetector implements Runnable {

    private static final Logger LOG = Logger.getLogger(IdleDetector.class.getName());

    private final NodeState nodeState;
    private final long      thresholdMs;
    private PrintWriter     transitionLog;

    public IdleDetector(AgentConfig config, NodeState nodeState) {
        this.nodeState    = nodeState;
        this.thresholdMs  = (long) config.getIdleThresholdSeconds() * 1000L;
        initTransitionLog(config.getLogDir());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Main loop
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            long    idleMs        = getIdleMillis();
            boolean sessionActive = isSessionActive();

            // User is considered "present" if either signal fires
            boolean userPresent = sessionActive || (idleMs < thresholdMs);
            NodeState.State desired = userPresent ? NodeState.State.ACTIVE : NodeState.State.IDLE;
            NodeState.State current = nodeState.getState();

            if (desired != current) {
                nodeState.setState(desired);
                logTransition(desired, idleMs);
            }

            LOG.fine(String.format(
                    "Idle check: idleMs=%d threshold=%d sessionActive=%b state=%s",
                    idleMs, thresholdMs, sessionActive, nodeState.getState()));

        } catch (Exception e) {
            // Never crash the scheduled loop — just log and continue
            LOG.warning("Idle detection error: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Signal 1: GetLastInputInfo
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns milliseconds since the last keyboard or mouse event.
     *
     * <p>Uses unsigned arithmetic to handle the 32-bit tick counter wrap-around
     * (~49.7 days of uptime). See {@link Kernel32Ext} for details.
     *
     * @return idle duration in ms; returns 0 if unavailable (treats as active — safe default)
     */
    long getIdleMillis() {
        try {
            User32Ext.LASTINPUTINFO.ByReference lii = new User32Ext.LASTINPUTINFO.ByReference();
            boolean ok = User32Ext.INSTANCE.GetLastInputInfo(lii);
            if (!ok) {
                LOG.warning("GetLastInputInfo returned false — treating as active");
                return 0;
            }

            // Both values are Windows DWORDs (unsigned 32-bit); Java int is signed.
            // Convert to unsigned longs before subtracting to avoid negative results.
            long currentTick = Integer.toUnsignedLong(Kernel32Ext.INSTANCE.GetTickCount());
            long lastInput   = Integer.toUnsignedLong(lii.dwTime);

            // The & mask handles the rare wrap-around case (e.g. currentTick has wrapped
            // but lastInput hasn't yet). Result is always in [0, 2^32) ms.
            long idleMs = (currentTick - lastInput) & 0xFFFFFFFFL;

            // Sanity guard: if result is more than 7 days, something is wrong — clamp to 0
            if (idleMs > 7L * 24 * 60 * 60 * 1000) {
                LOG.warning("Implausible idle time (" + idleMs + "ms) — clamping to 0");
                return 0;
            }
            return idleMs;

        } catch (UnsatisfiedLinkError | Exception e) {
            // JNA not available (running on Linux/macOS for testing) — assume active
            LOG.fine("GetLastInputInfo unavailable: " + e.getMessage());
            return 0;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Signal 2: query user (Windows session state)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if at least one Windows logon session is in "Active" state.
     *
     * <p>Output format of {@code query user}:
     * <pre>
     *  USERNAME     SESSIONNAME   ID  STATE    IDLE TIME  LOGON TIME
     * &gt;yash         console        1  Active       none   25-08-2026 09:00
     * </pre>
     *
     * <p>A "Disc" (disconnected) session means the user is logged in but away from the
     * screen — that is a <em>better</em> signal for "safe to run" than relying on idle
     * time alone.  Only "Active" blocks job execution.
     *
     * @return {@code false} if {@code query user} fails or is unavailable (safe default)
     */
    boolean isSessionActive() {
        try {
            Process proc = new ProcessBuilder("query", "user")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Columns are space-separated; STATE column contains "Active" or "Disc"
                    if (line.contains("Active")) {
                        return true;
                    }
                }
            }
            proc.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            // query user may not exist on all Windows editions or on test machines
            LOG.fine("query user failed (expected on non-Windows or minimal installs): "
                     + e.getMessage());
        }
        // If we can't determine session state, fall through to idle-ms only
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Transition logging — Step 4 empirical study data
    // ──────────────────────────────────────────────────────────────────────────

    private void logTransition(NodeState.State newState, long idleMs) {
        String msg = String.format("[%s] %-6s  idleMs=%-8d threshold=%d",
                Instant.now(), newState, idleMs, thresholdMs);
        LOG.info("Idle transition → " + newState + "  (idleMs=" + idleMs + ")");
        if (transitionLog != null) {
            transitionLog.println(msg);
        }
    }

    private void initTransitionLog(String logDir) {
        try {
            Path dir = Paths.get(logDir);
            Files.createDirectories(dir);
            Path logFile = dir.resolve("idle_transitions.log");
            transitionLog = new PrintWriter(
                    Files.newBufferedWriter(logFile,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true /* auto-flush */);
            LOG.info("Idle transition log: " + logFile.toAbsolutePath());
        } catch (Exception e) {
            LOG.warning("Could not open idle_transitions.log: " + e.getMessage());
            transitionLog = null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scheduling
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Schedules this detector to run every {@code intervalSeconds} on the given executor.
     * First check runs immediately (initial delay = 0).
     */
    public void scheduleOn(ScheduledExecutorService scheduler, int intervalSeconds) {
        scheduler.scheduleAtFixedRate(this, 0, intervalSeconds, TimeUnit.SECONDS);
        LOG.info("Idle detector scheduled every " + intervalSeconds +
                 "s (threshold=" + (thresholdMs / 1000) + "s)");
    }

    /**
     * Flushes and closes the transition log file.
     * Call from the JVM shutdown hook to release the file handle cleanly.
     * Data is not lost even without calling this (auto-flush is enabled),
     * but the OS file handle is properly released.
     */
    public void close() {
        if (transitionLog != null) {
            transitionLog.flush();
            transitionLog.close();
        }
    }
}
