package com.idlegrid.agent.storage;

import com.idlegrid.agent.config.AgentConfig;
import com.idlegrid.agent.util.HttpUtil;
import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Pulls assigned shared-storage replicas and writes them to this node's disk. */
public class StorageReplicaPoller implements Runnable {
    private static final Logger LOG = Logger.getLogger(StorageReplicaPoller.class.getName());
    private final AgentConfig config;
    private final HttpUtil http;
    private final Path storageDir;

    public StorageReplicaPoller(AgentConfig config, HttpUtil http) {
        this.config = config;
        this.http = http;
        this.storageDir = Paths.get(config.getStorageDir()).toAbsolutePath().normalize();
    }

    @Override public void run() {
        try {
            String path = "/agent/" + config.getNodeId() + "/storage/assignment";
            HttpResponse<String> response = http.get(path);
            if (response == null || response.statusCode() == 204) return;
            if (response.statusCode() != 200) {
                LOG.warning("Storage task poll returned HTTP " + response.statusCode());
                return;
            }
            JSONObject task = new JSONObject(response.body());
            String key = task.getString("key");
            boolean success = false;
            try {
                if (!key.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid file key");
                HttpResponse<byte[]> content = http.getBytes("/agent/storage/" + key + "/content");
                if (content == null || content.statusCode() != 200) throw new IllegalStateException("Replica source unavailable");
                if (content.body().length != task.getLong("sizeBytes")) throw new IllegalStateException("Replica size mismatch");
                Files.createDirectories(storageDir);
                Path target = storageDir.resolve(key).normalize();
                if (!target.startsWith(storageDir)) throw new IllegalArgumentException("Invalid storage path");
                Files.write(target, content.body());
                success = true;
                LOG.info("Stored replica " + key + " (" + content.body().length + " bytes)");
            } catch (Exception e) {
                LOG.warning("Could not store replica " + key + ": " + e.getMessage());
            }
            http.post("/agent/" + config.getNodeId() + "/storage/" + key + "/complete",
                    "{\"success\":" + success + "}");
        } catch (Exception e) {
            LOG.warning("Storage replica poll failed: " + e.getMessage());
        }
    }

    public void scheduleOn(ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(this, 10, 5, TimeUnit.SECONDS);
    }
}
