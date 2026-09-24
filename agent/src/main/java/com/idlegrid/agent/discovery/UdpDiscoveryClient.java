package com.idlegrid.agent.discovery;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Discovers the IdleGrid Master server automatically — no manual IP config needed.
 *
 * Strategy (in order):
 *   1. UDP broadcast  — fast (3s), works on flat networks
 *   2. TCP subnet scan — fallback for Wi-Fi with client-isolation (common in college labs)
 */
public class UdpDiscoveryClient {

    private static final Logger log = Logger.getLogger(UdpDiscoveryClient.class.getName());
    private static final int    DISCOVERY_UDP_PORT = 9091;
    private static final int    MASTER_HTTP_PORT   = 9090;
    private static final String DISCOVERY_REQ      = "IDLEGRID_DISCOVER_MASTER";
    private static final String DISCOVERY_ACK_PFX  = "IDLEGRID_MASTER_ACK:";

    // ── Public entry point ────────────────────────────────────────────────────

    public static String discoverMasterUrl() {
        log.info("=== IdleGrid Master Auto-Discovery starting ===");

        // 0. Try cloud rendezvous (JSONBin) — works across any subnet/network
        String url = tryRendezvous();
        if (url != null) return url;

        // 1. Try fast UDP broadcast — works on flat/same-subnet networks
        url = tryUdpBroadcast();
        if (url != null) return url;

        log.warning("UDP broadcast failed (likely Wi-Fi client isolation). Falling back to TCP subnet scan...");

        // 2. Fallback: parallel TCP scan of the local /24 subnet
        url = tryTcpSubnetScan();
        if (url != null) return url;

        log.warning("=== Auto-discovery found no Master. Using fallback URL. ===");
        return null;
    }

    // ── Strategy 0: Cloud rendezvous (JSONBin) ────────────────────────────────

    /** Bin ID of the JSONBin rendezvous store. Must match the backend config. */
    private static final String JSONBIN_BIN_ID =
            System.getProperty("rendezvous.jsonbin.bin-id",
            System.getenv("RENDEZVOUS_BIN_ID") != null
                    ? System.getenv("RENDEZVOUS_BIN_ID") : "");
    private static final String JSONBIN_URL = "https://api.jsonbin.io/v3/b/";

    private static String tryRendezvous() {
        // Read bin-id from agent.properties via system property, or env var
        String binId = readBinIdFromProperties();
        if (binId == null || binId.isBlank()) {
            log.fine("Rendezvous skipped (rendezvous.jsonbin.bin-id not set).");
            return null;
        }
        try {
            log.info("  Trying cloud rendezvous (JSONBin: " + binId + ")...");
            HttpURLConnection conn = (HttpURLConnection)
                    new URL(JSONBIN_URL + binId + "/latest").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            if (code != 200) {
                log.warning("  Rendezvous HTTP " + code);
                return null;
            }

            // Read the full response body
            String body = new String(conn.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            // Body is: {"record":{"masterUrl":"http://x.x.x.x:9090"},...}
            int start = body.indexOf("\"masterUrl\"");
            if (start < 0) { log.warning("  Rendezvous: no masterUrl field in response."); return null; }
            int valStart = body.indexOf('"', start + "\"masterUrl\"".length() + 1) + 1;
            int valEnd   = body.indexOf('"', valStart);
            if (valStart <= 0 || valEnd <= 0) return null;

            String masterUrl = body.substring(valStart, valEnd);
            log.info("  ✓ Master discovered via cloud rendezvous: " + masterUrl);
            return masterUrl;
        } catch (Exception e) {
            log.warning("  Rendezvous failed: " + e.getMessage());
            return null;
        }
    }

    /** Read rendezvous.jsonbin.bin-id from the agent.properties in working dir or classpath. */
    private static String readBinIdFromProperties() {
        // Try working-directory agent.properties first
        try {
            java.util.Properties p = new java.util.Properties();
            java.nio.file.Path f = java.nio.file.Paths.get("agent.properties");
            if (java.nio.file.Files.exists(f)) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(f)) { p.load(in); }
            } else {
                // Fall back to classpath
                try (java.io.InputStream in = UdpDiscoveryClient.class.getClassLoader()
                        .getResourceAsStream("agent.properties")) {
                    if (in != null) p.load(in);
                }
            }
            return p.getProperty("rendezvous.jsonbin.bin-id", "").trim();
        } catch (Exception e) {
            return "";
        }
    }


    private static String tryUdpBroadcast() {
        List<InetAddress> broadcasts = collectBroadcastAddresses();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(3000);
            byte[] sendData = DISCOVERY_REQ.getBytes();

            for (InetAddress broadcast : broadcasts) {
                try {
                    log.info("  UDP → " + broadcast.getHostAddress() + ":" + DISCOVERY_UDP_PORT);
                    socket.send(new DatagramPacket(sendData, sendData.length, broadcast, DISCOVERY_UDP_PORT));

                    byte[] buf = new byte[256];
                    DatagramPacket reply = new DatagramPacket(buf, buf.length);
                    socket.receive(reply);

                    String response = new String(reply.getData(), 0, reply.getLength()).trim();
                    if (response.startsWith(DISCOVERY_ACK_PFX)) {
                        String ip   = reply.getAddress().getHostAddress();
                        String port = response.substring(DISCOVERY_ACK_PFX.length());
                        String masterUrl = "http://" + ip + ":" + port;
                        log.info("  ✓ Master found via UDP: " + masterUrl);
                        return masterUrl;
                    }
                } catch (SocketTimeoutException ignored) {
                    log.fine("  No UDP reply from " + broadcast.getHostAddress());
                }
            }
        } catch (Exception e) {
            log.warning("UDP discovery error: " + e.getMessage());
        }
        return null;
    }

    private static List<InetAddress> collectBroadcastAddresses() {
        List<InetAddress> result = new ArrayList<>();
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                for (InterfaceAddress ifAddr : iface.getInterfaceAddresses()) {
                    InetAddress bc = ifAddr.getBroadcast();
                    if (bc != null) result.add(bc);
                }
            }
        } catch (Exception e) {
            log.warning("Could not enumerate interfaces: " + e.getMessage());
        }
        try { result.add(InetAddress.getByName("255.255.255.255")); } catch (Exception ignored) {}
        return result;
    }

    // ── Strategy 2: TCP subnet scan ───────────────────────────────────────────

    private static String tryTcpSubnetScan() {
        String localIp = getLocalIp();
        if (localIp == null) {
            log.warning("Could not determine local IP — skipping subnet scan.");
            return null;
        }

        // Derive /24 subnet prefix (e.g. "10.40.18.")
        String subnet = localIp.substring(0, localIp.lastIndexOf('.') + 1);
        log.info("  TCP scan of " + subnet + "0/24 on port " + MASTER_HTTP_PORT + " (this may take ~10s)...");

        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 1; i <= 254; i++) {
            final String ip = subnet + i;
            futures.add(pool.submit(() -> probeMaster(ip)));
        }

        pool.shutdown();
        String found = null;
        try {
            pool.awaitTermination(15, TimeUnit.SECONDS);
            for (Future<String> f : futures) {
                if (f.isDone()) {
                    String result = f.get();
                    if (result != null) { found = result; break; }
                }
            }
        } catch (Exception e) {
            log.warning("Subnet scan interrupted: " + e.getMessage());
        } finally {
            pool.shutdownNow();
        }

        if (found != null) log.info("  ✓ Master found via TCP scan: " + found);
        return found;
    }

    /**
     * Returns the master URL if this IP responds to the IdleGrid /nodes/summary
     * endpoint, otherwise null.
     */
    private static String probeMaster(String ip) {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URL("http://" + ip + ":" + MASTER_HTTP_PORT + "/nodes/summary").openConnection();
            conn.setConnectTimeout(300);
            conn.setReadTimeout(300);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code == 200) return "http://" + ip + ":" + MASTER_HTTP_PORT;
        } catch (Exception ignored) {}
        return null;
    }

    private static String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }
}

