package com.idlegrid.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.DatagramSocket;
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

    @Value("${rendezvous.master-url:}")
    private String configuredMasterUrl;

    @Override
    public void onApplicationEvent(ServletWebServerInitializedEvent event) {
        if (apiKey.isBlank() || binId.isBlank()) {
            log.info("Rendezvous registration skipped (rendezvous.jsonbin.* not configured).");
            return;
        }

        int activePort = event.getWebServer().getPort();
        Thread thread = new Thread(() -> registerWithRetry(activePort), "rendezvous-register");
        thread.setDaemon(true);
        thread.start();
    }

    private void registerWithRetry(int activePort) {
        String masterUrl;
        try {
            masterUrl = configuredMasterUrl.isBlank()
                    ? "http://" + findRoutedLocalAddress().getHostAddress() + ":" + activePort
                    : trimTrailingSlash(configuredMasterUrl.trim());
        } catch (Exception e) {
            log.warn("Rendezvous registration could not determine Master URL: {}", e.getMessage());
            return;
        }

        String payload = "{\"masterUrl\":\"" + jsonEscape(masterUrl) + "\"}";
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(JSONBIN_UPDATE_URL + binId).openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                // JSONBin's core API key is sent as X-Master-Key for writes.
                conn.setRequestProperty("X-Master-Key", apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    log.info("Rendezvous registered Master URL (attempt {} of 3).", attempt);
                    return;
                }
                log.warn("Rendezvous registration failed — HTTP {} (attempt {} of 3). Check JSONBIN_API_KEY, bin access, and write permission.",
                        code, attempt);
            } catch (Exception e) {
                log.warn("Rendezvous registration error (attempt {} of 3): {}", attempt, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }

            if (attempt < 3) {
                try { Thread.sleep(2000L * attempt); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    /**
     * Ask the OS which local interface it would use to reach JSONBin. This is more
     * reliable than InetAddress.getLocalHost(), which can resolve to loopback or a
     * disconnected adapter on multi-homed lab PCs. UDP connect selects a route but
     * does not send a packet.
     */
    private InetAddress findRoutedLocalAddress() throws Exception {
        InetAddress rendezvousHost = InetAddress.getByName("api.jsonbin.io");
        try (DatagramSocket routeProbe = new DatagramSocket()) {
            routeProbe.connect(rendezvousHost, 443);
            InetAddress localAddress = routeProbe.getLocalAddress();
            if (!localAddress.isAnyLocalAddress() && !localAddress.isLoopbackAddress()) {
                return localAddress;
            }
        }

        InetAddress fallback = InetAddress.getLocalHost();
        if (fallback.isAnyLocalAddress() || fallback.isLoopbackAddress()) {
            throw new IllegalStateException("No non-loopback network address was found");
        }
        return fallback;
    }

    private String trimTrailingSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
