package com.idlegrid.agent.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Thin, shared wrapper around Java 11's built-in {@link HttpClient}.
 *
 * <p>One instance is created at startup and passed to every service that needs
 * to talk to the Master. This avoids creating one HTTP client per heartbeat.
 *
 * <p>All methods are safe to call from any thread (HttpClient is thread-safe).
 * Errors are logged and null is returned rather than throwing, so callers can
 * treat a null response as "Master is unreachable" without crashing loops.
 */
public class HttpUtil {

    private static final Logger LOG = Logger.getLogger(HttpUtil.class.getName());

    private final HttpClient client;
    private final String     masterUrl;

    public HttpUtil(String masterUrl) {
        // Strip trailing slash once so callers don't have to think about it
        this.masterUrl = masterUrl.endsWith("/")
                ? masterUrl.substring(0, masterUrl.length() - 1)
                : masterUrl;

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * HTTP POST with a JSON body to a Master endpoint.
     *
     * @param path     e.g. {@code "/nodes/heartbeat"} — will be appended to masterUrl
     * @param jsonBody UTF-8 JSON string
     * @return the response, or {@code null} if the request could not be sent
     */
    public HttpResponse<String> post(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(masterUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.fine("POST " + path + " → HTTP " + response.statusCode());
            return response;
        } catch (Exception e) {
            LOG.warning("POST " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * HTTP GET a Master endpoint.
     *
     * @param path e.g. {@code "/agent/abc123/assignment"}
     * @return the response, or {@code null} if the request could not be sent
     */
    public HttpResponse<String> get(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(masterUrl + path))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.fine("GET " + path + " → HTTP " + response.statusCode());
            return response;
        } catch (Exception e) {
            LOG.warning("GET " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    /** Returns the configured master base URL (for logging). */
    public String getMasterUrl() { return masterUrl; }
}
