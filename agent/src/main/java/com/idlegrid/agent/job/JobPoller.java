package com.idlegrid.agent.job;

import com.idlegrid.agent.config.AgentConfig;
import com.idlegrid.agent.util.HttpUtil;
import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Polls the Master for a job assigned to this node.
 *
 * <h3>Endpoint</h3>
 * {@code GET /agent/{nodeId}/assignment}
 * <ul>
 *   <li>200 + JSON body → job assigned, start executing</li>
 *   <li>204 (No Content) → no assignment, poll again next tick</li>
 *   <li>404 (Not Found) → endpoint not yet implemented on Master;
 *       treat as no assignment (graceful fallback)</li>
 * </ul>
 *
 * <h3>Double-execution prevention</h3>
 * If {@link JobExecutor#isRunning()} returns {@code true}, the poll result is
 * discarded.  The Master should not assign a second job to a node that already has
 * one running (the heartbeat payload tells it the node is busy), but we guard
 * here too for robustness.
 *
 * <h3>Coordination note</h3>
 * The {@code GET /agent/{nodeId}/assignment} endpoint needs to be added to the
 * backend (see AGENTS.md open question).  The poller handles 404 gracefully so
 * the agent can start up and heartbeat before that endpoint exists.
 */
public class JobPoller implements Runnable {

    private static final Logger LOG = Logger.getLogger(JobPoller.class.getName());

    private final AgentConfig config;
    private final HttpUtil    http;
    private final JobExecutor executor;

    public JobPoller(AgentConfig config, HttpUtil http, JobExecutor executor) {
        this.config   = config;
        this.http     = http;
        this.executor = executor;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Poll loop body
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            // Don't poll if we're already running a job
            if (executor.isRunning()) {
                LOG.fine("Job already running — skipping poll");
                return;
            }

            String path = "/agent/" + config.getNodeId() + "/assignment";
            HttpResponse<String> response = http.get(path);

            if (response == null) {
                // Master unreachable — heartbeat will surface this, nothing to do here
                LOG.fine("No response from Master during job poll");
                return;
            }

            int status = response.statusCode();

            switch (status) {
                case 200 -> handleAssignment(response.body());
                case 204 -> LOG.fine("No assignment (204 No Content)");
                case 404 -> LOG.fine("Assignment endpoint not yet implemented on Master (404) — "
                                    + "skipping (this is expected until backend adds the endpoint)");
                default  -> LOG.warning("Unexpected assignment response: HTTP " + status);
            }

        } catch (Exception e) {
            // Never crash the scheduled loop
            LOG.warning("JobPoller threw: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void handleAssignment(String body) {
        if (body == null || body.isBlank()) {
            LOG.fine("Assignment response body is empty — treating as no assignment");
            return;
        }

        JobModel job = parseAssignment(body);
        if (job != null) {
            LOG.info("New job assignment received: " + job);
            executor.executeAsync(job);
        }
    }

    /**
     * Parses the assignment JSON.
     *
     * <p>Expected format:
     * <pre>
     * {
     *   "jobId":    "abc-123",      // or "id" as an alias
     *   "command":  "python3 t.py",
     *   "image":    "python:3.11",  // optional
     *   "cpuReq":   1.0,            // optional, default 1.0
     *   "ramReqMb": 512             // optional, default 512
     * }
     * </pre>
     *
     * @return parsed {@link JobModel}, or {@code null} if parsing fails
     */
    private JobModel parseAssignment(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            // Accept "jobId" or "id" (handle both naming conventions from backend)
            String jobId = obj.optString("jobId", obj.optString("id", "")).trim();
            if (jobId.isEmpty()) {
                LOG.warning("Assignment JSON missing 'jobId'/'id' field: " + json);
                return null;
            }

            String command = obj.optString("command", "echo 'no command specified'");
            String image   = obj.has("image") ? obj.getString("image") : null;
            double cpuReq  = obj.optDouble("cpuReq", 1.0);
            int    ramReq  = obj.optInt("ramReqMb", 512);

            return new JobModel(jobId, command, image, cpuReq, ramReq);

        } catch (Exception e) {
            LOG.warning("Failed to parse assignment JSON: " + e.getMessage()
                        + "\nBody: " + json);
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scheduling
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Schedules the poller to run every N seconds.
     * Initial delay is 5s to let the heartbeat establish the node in the Master
     * registry before we start asking for jobs.
     */
    public void scheduleOn(ScheduledExecutorService scheduler) {
        int interval = config.getJobPollIntervalSeconds();
        scheduler.scheduleAtFixedRate(this, 5, interval, TimeUnit.SECONDS);
        LOG.info("Job poller scheduled every " + interval + "s"
                 + " (initial delay 5s) → "
                 + config.getMasterUrl() + "/agent/{nodeId}/assignment");
    }
}
