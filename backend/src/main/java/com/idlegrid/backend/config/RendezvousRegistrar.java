package com.idlegrid.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * On startup, publishes the Master's current IP to a JSONBin.io "rendezvous bin"
 * so Agents on any subnet can discover the Master without any manual config.
 *
 * Flow:
 *   Master starts → detects local IP → PUTs {"masterUrl":"http://<ip>:<port>"} to JSONBin
 *   Agent starts  → GETs the same bin → reads masterUrl → connects
 *
 * Required config in application.properties:
 *   rendezvous.jsonbin.api-key=YOUR_API_KEY
 *   rendezvous.jsonbin.bin-id=YOUR_BIN_ID
 *
 * If either property is blank/missing, registration is silently skipped.
 */
@Component
public class RendezvousRegistrar implements ApplicationListener<ServletWebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(RendezvousRegistrar.class);
    private static final String JSONBIN_UPDATE_URL = "https://api.jsonbin.io/v3/b/";

    @Value("${rendezvous.jsonbin.api-key:}")
    private String apiKey;

    @Value("${rendezvous.jsonbin.bin-id:}")
    private String binId;

    @Value("${server.port:9090}")
    private int serverPort;

    @Override
    public void onApplicationEvent(ServletWebServerInitializedEvent event) {
        if (apiKey.isBlank() || binId.isBlank()) {
            log.info("Rendezvous registration skipped (rendezvous.jsonbin.* not configured).");
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                String localIp = InetAddress.getLocalHost().getHostAddress();
                String masterUrl = "http://" + localIp + ":" + serverPort;
                String payload = "{\"masterUrl\":\"" + masterUrl + "\"}";

                URL url = new URL(JSONBIN_UPDATE_URL + binId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-Access-Key", apiKey);
                conn.setRequestProperty("X-Bin-Versioning", "false"); // don't keep old versions
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    log.info("✓ Rendezvous: registered Master at {} (bin: {})", masterUrl, binId);
                } else {
                    log.warn("Rendezvous registration failed — HTTP {}", code);
                }
            } catch (Exception e) {
                log.warn("Rendezvous registration error: {}", e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.setName("rendezvous-register");
        thread.start();
    }
}
