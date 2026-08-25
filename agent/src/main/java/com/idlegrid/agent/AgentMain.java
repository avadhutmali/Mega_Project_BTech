package com.idlegrid.agent;

import com.idlegrid.agent.config.AgentConfig;
import com.idlegrid.agent.eviction.EvictionManager;
import com.idlegrid.agent.heartbeat.HeartbeatService;
import com.idlegrid.agent.idle.IdleDetector;
import com.idlegrid.agent.idle.NodeState;
import com.idlegrid.agent.job.JobExecutor;
import com.idlegrid.agent.job.JobPoller;
import com.idlegrid.agent.resources.ResourceMonitor;
import com.idlegrid.agent.util.HttpUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * IdleGrid Agent — main entry point.
 *
 * <h3>Startup sequence</h3>
 * <ol>
 *   <li>Load configuration (agent.properties from classpath + working directory)</li>
 *   <li>Configure logging (console INFO + rolling file ALL)</li>
 *   <li>Clean up orphaned Docker containers from any previous crash (Step 10)</li>
 *   <li>Wire all services together (no framework, plain dependency injection)</li>
 *   <li>Register the eviction manager as a NodeState listener</li>
 *   <li>Schedule all three loops on a shared ScheduledExecutorService</li>
 *   <li>Park the main thread; loops run on daemon threads</li>
 * </ol>
 *
 * <h3>Graceful shutdown (Ctrl+C)</h3>
 * A JVM shutdown hook sends a final heartbeat with {@code status=OFFLINE} so the
 * Master can immediately mark this node offline rather than waiting for the
 * heartbeat timeout.
 *
 * <h3>Running</h3>
 * <pre>
 *   java -jar target/agent-1.0-SNAPSHOT-exec.jar
 * </pre>
 *
 * Override config by placing {@code agent.properties} in the working directory:
 * <pre>
 *   master.url=http://192.168.1.100:8080
 *   idle.threshold.seconds=120
 * </pre>
 */
public class AgentMain {

    public static void main(String[] args) throws InterruptedException {
        // ── 1. Load Config ────────────────────────────────────────────────────
        AgentConfig config = new AgentConfig();

        // ── 2. Configure Logging ──────────────────────────────────────────────
        setupLogging(config.getLogDir());
        Logger log = Logger.getLogger(AgentMain.class.getName());

        log.info("╔══════════════════════════════════════╗");
        log.info("║     IdleGrid Agent  v1.0-SNAPSHOT    ║");
        log.info("╚══════════════════════════════════════╝");
        log.info(config.toString());

        // ── 3. Orphan Cleanup ─────────────────────────────────────────────────
        // Kill any IdleGrid Docker containers left over from a previous agent crash.
        // Must run before we register as ONLINE so the Master doesn't think we're
        // running jobs when we've actually lost state.
        HttpUtil     http     = new HttpUtil(config.getMasterUrl());
        JobExecutor  executor = new JobExecutor(config, http);
        executor.cleanupOrphanedContainers();

        // ── 4. Wire Services ──────────────────────────────────────────────────
        ResourceMonitor  monitor   = new ResourceMonitor();
        NodeState        nodeState = new NodeState();
        IdleDetector     idleDet   = new IdleDetector(config, nodeState);
        HeartbeatService heartbeat = new HeartbeatService(config, monitor, nodeState, http);
        JobPoller        poller    = new JobPoller(config, http, executor);
        EvictionManager  eviction  = new EvictionManager(executor);

        // Register eviction BEFORE starting the idle loop so no IDLE→ACTIVE
        // transitions are missed between wiring and scheduling.
        eviction.register(nodeState);

        // ── 5. Schedule Loops ─────────────────────────────────────────────────
        // 4 threads: heartbeat, idle-check, job-poll, plus one spare for eviction spillover
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4,
                runnable -> {
                    Thread t = new Thread(runnable, "agent-scheduler");
                    t.setDaemon(true); // don't block JVM shutdown
                    return t;
                });

        heartbeat.scheduleOn(scheduler);
        idleDet.scheduleOn(scheduler, config.getIdleCheckIntervalSeconds());
        poller.scheduleOn(scheduler);

        // ── 6. Summary Log ────────────────────────────────────────────────────
        log.info("All loops started successfully:");
        log.info("  Node ID    : " + config.getNodeId());
        log.info("  Local IP   : " + config.getLocalIp());
        log.info("  Master URL : " + config.getMasterUrl());
        log.info("  Heartbeat  : every " + config.getHeartbeatIntervalSeconds() + "s");
        log.info("  Idle check : every " + config.getIdleCheckIntervalSeconds()
                 + "s  (threshold=" + config.getIdleThresholdSeconds() + "s)");
        log.info("  Job poll   : every " + config.getJobPollIntervalSeconds() + "s");
        log.info("  Log dir    : " + Paths.get(config.getLogDir()).toAbsolutePath());
        log.info("Press Ctrl+C to stop.");

        // ── 7. Graceful Shutdown Hook ─────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received — notifying Master of OFFLINE status...");
            // Best-effort; if Master is down this will timeout and we move on
            http.post("/nodes/heartbeat", String.format(
                    "{\"nodeId\":\"%s\",\"ip\":\"%s\",\"status\":\"OFFLINE\"}",
                    config.getNodeId(), config.getLocalIp()));
            idleDet.close(); // flush + close idle_transitions.log file handle
            scheduler.shutdownNow();
            log.info("Agent stopped.");
        }, "shutdown-hook"));

        // ── 8. Park Main Thread ───────────────────────────────────────────────
        // All work happens on daemon threads via the ScheduledExecutorService.
        // Joining the main thread keeps the JVM alive indefinitely.
        Thread.currentThread().join();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Logging setup
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Configures java.util.logging with:
     * <ul>
     *   <li>Console handler at INFO level — clean one-line format</li>
     *   <li>Rolling file handler at ALL levels — 10 MB per file, 3 files,
     *       saved to {@code logs/agent.log}</li>
     * </ul>
     */
    private static void setupLogging(String logDir) {
        try {
            Files.createDirectories(Paths.get(logDir));

            // Remove default handlers from root logger
            Logger root = Logger.getLogger("");
            for (var h : root.getHandlers()) root.removeHandler(h);
            root.setLevel(Level.ALL);

            SimpleFormatter fmt = new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord lr) {
                    return String.format("[%tT] [%-7s] [%s] %s%n",
                            lr.getMillis(),
                            lr.getLevel().getName(),
                            shortName(lr.getLoggerName()),
                            lr.getMessage());
                }

                private String shortName(String name) {
                    if (name == null) return "?";
                    int dot = name.lastIndexOf('.');
                    return dot >= 0 ? name.substring(dot + 1) : name;
                }
            };

            // Console — INFO and above
            ConsoleHandler console = new ConsoleHandler();
            console.setLevel(Level.INFO);
            console.setFormatter(fmt);

            // Rolling file — ALL levels (includes FINE for idle/heartbeat detail)
            FileHandler file = new FileHandler(
                    logDir + "/agent.log",
                    10 * 1024 * 1024, // 10 MB per file
                    3,                 // keep 3 rotation files
                    true               // append on restart
            );
            file.setLevel(Level.ALL);
            file.setFormatter(fmt);

            root.addHandler(console);
            root.addHandler(file);

            // Suppress JNA's very verbose internal logging
            Logger.getLogger("com.sun.jna").setLevel(Level.WARNING);
            Logger.getLogger("com.sun.jna.Native").setLevel(Level.WARNING);

        } catch (IOException e) {
            System.err.println("WARNING: Could not configure file logging: " + e.getMessage());
        }
    }
}
