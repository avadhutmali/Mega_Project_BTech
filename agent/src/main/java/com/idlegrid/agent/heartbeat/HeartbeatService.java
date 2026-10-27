package com.idlegrid.agent.heartbeat;

import com.idlegrid.agent.config.AgentConfig;
import com.idlegrid.agent.idle.NodeState;
import com.idlegrid.agent.resources.ResourceMonitor;
import com.idlegrid.agent.util.HttpUtil;

import java.net.InetAddress;
import java.net.http.HttpResponse;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Sends a heartbeat to the Master every N seconds via a {@link ScheduledExecutorService}.
 *
 * <h3>Why ScheduledExecutorService, not a sleep loop</h3>
 * A {@code ScheduledExecutorService} with {@code scheduleAtFixedRate} fires on wall-clock
 * time.  If one heartbeat POST blocks for 2 s (e.g. Master is slow), the next fires
 * immediately rather than being delayed by an additional N seconds — timing stays reliable.
 *
 * <h3>Payload</h3>
 * <pre>
 * POST /nodes/heartbeat
 * {
 *   "nodeId":     "...",
 *   "hostname":   "...",
 *   "ip":         "...",
 *   "cpuFree":    75.3,
 *   "ramFreeMb":  4096,
 *   "diskFreeMb": 50000,
 *   "status":     "ONLINE",
 *   "idleState":  "IDLE"
 * }
 * </pre>
 *
 * <h3>Failure handling</h3>
 * Consecutive failure count is tracked and logged so operators can see if the Master
 * is unreachable without wading through stack traces.  The loop never crashes.
 */
public class HeartbeatService implements Runnable {

    private static final Logger LOG = Logger.getLogger(HeartbeatService.class.getName());

    private final AgentConfig     config;
    private final ResourceMonitor monitor;
    private final NodeState       nodeState;
    private final HttpUtil        http;
    private final AtomicInteger   consecutiveFailures = new AtomicInteger(0);

    public HeartbeatService(AgentConfig config, ResourceMonitor monitor,
                            NodeState nodeState, HttpUtil http) {
        this.config    = config;
        this.monitor   = monitor;
        this.nodeState = nodeState;
        this.http      = http;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Heartbeat loop body
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            double cpuFree   = monitor.getCpuCores() * monitor.getCpuFreePercent() / 100.0;
            long   ramFree   = monitor.getRamFreeMb();
            long   diskFree  = monitor.getDiskFreeMb();
            long   diskTotal = monitor.getDiskTotalMb();
            String idleState = nodeState.getState().name();

            String body = buildPayload(cpuFree, ramFree, diskFree, diskTotal, idleState, "ONLINE");

            HttpResponse<String> response = http.post("/nodes/heartbeat", body);

            if (response != null && response.statusCode() == 200) {
                int prev = consecutiveFailures.getAndSet(0);
                if (prev > 0) {
                    LOG.info("Heartbeat resumed after " + prev + " failure(s)");
                }
                LOG.fine(String.format(
                        "♥ heartbeat OK — cpu=%.2f cores ram=%dMB disk=%d/%dMB idle=%s",
                        cpuFree, ramFree, diskFree, diskTotal, idleState));
            } else {
                int failures = consecutiveFailures.incrementAndGet();
                String status = response == null ? "no response (Master unreachable?)"
                                                 : "HTTP " + response.statusCode();
                LOG.warning("Heartbeat failed [" + failures + "×]: " + status);
            }

        } catch (Exception e) {
            // Never crash the scheduled loop
            consecutiveFailures.incrementAndGet();
            LOG.warning("Heartbeat run() threw: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Payload builder
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds the heartbeat JSON payload manually.
     *
     * <p>CPU availability is expressed in core equivalents, matching job requests
     * and Docker's {@code --cpus} value.
     */
    private String buildPayload(double cpuFreeDouble, long ramFree, long diskFree, long diskTotal,
                                 String idleState, String status) {
        return String.format("""
                {
                  "nodeId":     "%s",
                  "hostname":   "%s",
                  "ip":         "%s",
                  "sshCommand": "%s",
                  "cpuTotal":   %d,
                  "cpuFree":    %.2f,
                  "ramTotalMb": %d,
                  "ramFreeMb":  %d,
                  "diskTotalMb": %d,
                  "diskFreeMb": %d,
                  "status":     "%s",
                  "idleState":  "%s"
                }""",
                config.getNodeId(),
                getHostname(),
                config.getLocalIp(),
                jsonEscape(config.getSshCommand()),
                monitor.getCpuCores(),
                cpuFreeDouble,
                monitor.getRamTotalMb(),
                ramFree,
                diskTotal,
                diskFree,
                status,
                idleState);
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scheduling
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Schedules this heartbeat to fire immediately and then every N seconds.
     *
     * <p>Uses {@code scheduleAtFixedRate} (not {@code scheduleWithFixedDelay})
     * so timing stays predictable even if a heartbeat POST takes a while to return.
     */
    public void scheduleOn(ScheduledExecutorService scheduler) {
        int interval = config.getHeartbeatIntervalSeconds();
        scheduler.scheduleAtFixedRate(this, 0, interval, TimeUnit.SECONDS);
        LOG.info("Heartbeat scheduled every " + interval + "s → "
                 + config.getMasterUrl() + "/nodes/heartbeat");
    }
}
