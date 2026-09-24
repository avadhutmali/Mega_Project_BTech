package com.idlegrid.agent.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import com.idlegrid.agent.discovery.UdpDiscoveryClient;

/**
 * Loads agent configuration from agent.properties.
 *
 * <p>Load priority (highest wins):
 * <ol>
 *   <li>Working-directory {@code agent.properties} — operator-supplied override</li>
 *   <li>Classpath {@code agent.properties} — shipped defaults inside the JAR</li>
 * </ol>
 *
 * <p>Node ID resolution order:
 * <ol>
 *   <li>{@code node.id} property if non-empty</li>
 *   <li>{@code .node_id} file in the working directory (persisted across restarts)</li>
 *   <li>Auto-generated UUID — saved to {@code .node_id} for future runs</li>
 * </ol>
 */
public class AgentConfig {

    private static final Logger LOG = Logger.getLogger(AgentConfig.class.getName());
    private static final String NODE_ID_FILE = ".node_id";

    private final Properties props;
    private final String nodeId;
    private final String localIp;
    private final String masterUrl;

    public AgentConfig() {
        this.props   = loadProperties();
        this.nodeId  = resolveNodeId();
        this.localIp = resolveIp();
        this.masterUrl = resolveMasterUrl();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Initialisation helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Properties loadProperties() {
        Properties p = new Properties();

        // 1. Classpath defaults (bundled in JAR)
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("agent.properties")) {
            if (in != null) {
                p.load(in);
            } else {
                LOG.warning("Default agent.properties not found on classpath.");
            }
        } catch (IOException e) {
            LOG.warning("Could not load classpath agent.properties: " + e.getMessage());
        }

        // 2. Working-directory override
        Path override = Paths.get("agent.properties");
        if (Files.exists(override)) {
            try (InputStream in = Files.newInputStream(override)) {
                p.load(in);
                LOG.info("Loaded config overrides from: " + override.toAbsolutePath());
            } catch (IOException e) {
                LOG.warning("Could not load override agent.properties: " + e.getMessage());
            }
        }

        return p;
    }

    private String resolveNodeId() {
        // Priority 1: explicit property
        String id = props.getProperty("node.id", "").trim();
        if (!id.isEmpty()) {
            LOG.info("Using node.id from config: " + id);
            return id;
        }

        // Priority 2: persisted .node_id file
        Path idFile = Paths.get(NODE_ID_FILE);
        if (Files.exists(idFile)) {
            try {
                id = Files.readString(idFile).trim();
                if (!id.isEmpty()) {
                    LOG.info("Loaded persisted node ID: " + id);
                    return id;
                }
            } catch (IOException e) {
                LOG.warning("Could not read " + NODE_ID_FILE + ": " + e.getMessage());
            }
        }

        // Priority 3: generate a new UUID and persist it
        id = UUID.randomUUID().toString();
        try {
            Files.writeString(idFile, id);
            LOG.info("Generated new node ID (saved to " + NODE_ID_FILE + "): " + id);
        } catch (IOException e) {
            LOG.warning("Could not persist node ID — will regenerate on next restart: " + e.getMessage());
        }
        return id;
    }

    private String resolveIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            LOG.warning("Could not determine local IP address: " + e.getMessage());
            return "127.0.0.1";
        }
    }

    private String resolveMasterUrl() {
        String url = props.getProperty("master.url", "auto").trim();
        if ("auto".equalsIgnoreCase(url)) {
            String discovered = UdpDiscoveryClient.discoverMasterUrl();
            if (discovered != null) {
                return discovered;
            }
            LOG.warning("Auto-discovery failed. Defaulting to http://localhost:9090");
            return "http://localhost:9090";
        }
        return url;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Accessors
    // ──────────────────────────────────────────────────────────────────────────

    /** Base URL of the Master server, no trailing slash. */
    public String getMasterUrl() {
        return masterUrl;
    }

    /** Stable identifier for this node, used as the key in all Master API calls. */
    public String getNodeId() { return nodeId; }

    /** Best-effort local IPv4 address. */
    public String getLocalIp() { return localIp; }

    /** Seconds between heartbeat POSTs. */
    public int getHeartbeatIntervalSeconds() {
        return intProp("heartbeat.interval.seconds", 3);
    }

    /** Seconds between job-assignment polls. */
    public int getJobPollIntervalSeconds() {
        return intProp("job.poll.interval.seconds", 5);
    }

    /**
     * Seconds of keyboard/mouse inactivity before the node transitions to IDLE.
     * Slow reaction on departure (must exceed threshold).
     */
    public int getIdleThresholdSeconds() {
        return intProp("idle.threshold.seconds", 300);
    }

    /** How often (seconds) the idle detection loop runs. */
    public int getIdleCheckIntervalSeconds() {
        return intProp("idle.check.interval.seconds", 10);
    }

    /** Docker image used when the job payload doesn't specify one. */
    public String getDefaultDockerImage() {
        return props.getProperty("docker.default.image", "alpine:latest");
    }

    /** Root directory for per-job workspace directories. */
    public String getWorkspaceDir() {
        return props.getProperty("workspace.dir", "./workspace");
    }

    /** Root directory for all log files. */
    public String getLogDir() {
        return props.getProperty("log.dir", "./logs");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private int intProp(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            LOG.warning("Invalid int for property '" + key + "', using default " + defaultValue);
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return "AgentConfig{" +
               "masterUrl=" + getMasterUrl() +
               ", nodeId=" + nodeId +
               ", ip=" + localIp +
               ", heartbeat=" + getHeartbeatIntervalSeconds() + "s" +
               ", idleThreshold=" + getIdleThresholdSeconds() + "s" +
               '}';
    }
}
