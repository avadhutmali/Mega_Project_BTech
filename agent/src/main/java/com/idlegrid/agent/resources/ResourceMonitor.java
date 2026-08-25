package com.idlegrid.agent.resources;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Reports current system resource availability to the heartbeat payload.
 *
 * <h3>CPU</h3>
 * Uses {@code com.sun.management.OperatingSystemMXBean#getCpuLoad()} which returns
 * the system-wide CPU fraction across all logical processors.  First call after JVM
 * startup can return {@code -1.0}; we treat that as "fully free".
 *
 * <h3>RAM</h3>
 * Prefers {@code wmic OS get FreePhysicalMemory} on Windows.  The JVM's own MXBean
 * view is inaccurate on Windows because it doesn't account for page-cache reclaim.
 * wmic reports what the OS considers "available" which is the number students care about.
 *
 * <h3>Disk</h3>
 * Uses {@link java.nio.file.FileStore} on the working directory's volume.
 */
public class ResourceMonitor {

    private static final Logger LOG = Logger.getLogger(ResourceMonitor.class.getName());

    private final com.sun.management.OperatingSystemMXBean osBean;

    public ResourceMonitor() {
        this.osBean = (com.sun.management.OperatingSystemMXBean)
                ManagementFactory.getOperatingSystemMXBean();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CPU
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns free CPU as a percentage of total capacity across all cores (0–100).
     * The value naturally accounts for all cores; e.g. if one of four cores is
     * at 100%, this returns ~75 (not 0).
     */
    public double getCpuFreePercent() {
        double load = osBean.getCpuLoad();
        if (load < 0) {
            return 100.0; // not yet measurable (JVM just started) — report fully free
        }
        return Math.max(0.0, Math.min(100.0, (1.0 - load) * 100.0));
    }

    /** Total logical CPU cores (for heartbeat metadata). */
    public int getCpuCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // RAM
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns free physical RAM in MB.
     * Tries {@code wmic} first; falls back to MXBean if wmic is unavailable
     * (e.g. when testing on Linux/macOS).
     */
    public long getRamFreeMb() {
        long wmicResult = getRamFreeMbViaWmic();
        if (wmicResult >= 0) return wmicResult;

        // Fallback: MXBean (may be slightly off on Windows due to page-cache accounting)
        return osBean.getFreeMemorySize() / (1024L * 1024L);
    }

    /** Total physical RAM in MB. */
    public long getRamTotalMb() {
        return osBean.getTotalMemorySize() / (1024L * 1024L);
    }

    /**
     * Shells out to {@code wmic OS get FreePhysicalMemory /value}.
     * Output format: {@code FreePhysicalMemory=1234567} (value in KB).
     *
     * @return free RAM in MB, or {@code -1} on failure
     */
    private long getRamFreeMbViaWmic() {
        try {
            Process proc = new ProcessBuilder(
                    "wmic", "OS", "get", "FreePhysicalMemory", "/value")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("FreePhysicalMemory=")) {
                        String kbStr = line.substring("FreePhysicalMemory=".length()).trim();
                        if (!kbStr.isEmpty()) {
                            return Long.parseLong(kbStr) / 1024L; // KB → MB
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.fine("wmic not available, falling back to MXBean for RAM: " + e.getMessage());
        }
        return -1;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Disk
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns usable disk space in MB on the volume that contains the working directory.
     * Returns {@code -1} on error.
     */
    public long getDiskFreeMb() {
        try {
            var store = Files.getFileStore(Paths.get(System.getProperty("user.dir")));
            return store.getUsableSpace() / (1024L * 1024L);
        } catch (Exception e) {
            LOG.warning("Could not read disk free space: " + e.getMessage());
            return -1;
        }
    }
}
