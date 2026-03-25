package ui;

import java.io.PrintStream;

/**
 * Console-based progress bar for file transfers and long-running operations.
 * <p>
 * Displays a visual progress bar on a single line using carriage return (\r),
 * showing percentage, transferred/total bytes, speed, and estimated time remaining.
 * <p>
 * Example output:
 * <pre>
 *   Uploading  [████████████░░░░░░░░] 62%  1.8MB/2.9MB  520.3KB/s  ETA 2s
 * </pre>
 * <p>
 * For indeterminate mode (total size unknown):
 * <pre>
 *   Downloading  [◀▶◀▶◀▶              ]  1.8MB  520.3KB/s
 * </pre>
 *
 * Thread-safe: can be called from any thread.
 */
public class ProgressBar {

    // ==================== Constants ====================

    private static final int BAR_WIDTH = 30;
    private static final char FILLED_CHAR = '█';
    private static final char EMPTY_CHAR = '░';
    private static final long MIN_UPDATE_INTERVAL_MS = 80;  // ~12 FPS max refresh rate

    // ==================== State ====================

    private final String label;
    private final long totalBytes;
    private final PrintStream out;
    private final long startTimeMs;

    private volatile long currentBytes;
    private volatile long lastUpdateMs;
    private volatile boolean finished;

    // ==================== Constructors ====================

    /**
     * Create a progress bar with a known total size.
     *
     * @param label      Label to display (e.g. "Uploading", "Downloading")
     * @param totalBytes Total number of bytes expected (-1 for indeterminate)
     */
    public ProgressBar(String label, long totalBytes) {
        this(label, totalBytes, System.out);
    }

    /**
     * Create a progress bar with a specific output stream.
     *
     * @param label      Label to display
     * @param totalBytes Total number of bytes expected (-1 for indeterminate)
     * @param out        Output stream (usually System.out)
     */
    public ProgressBar(String label, long totalBytes, PrintStream out) {
        this.label = label != null ? label : "";
        this.totalBytes = totalBytes;
        this.out = out;
        this.startTimeMs = System.currentTimeMillis();
        this.currentBytes = 0;
        this.lastUpdateMs = 0;
        this.finished = false;
    }

    // ==================== Public API ====================

    /**
     * Update the progress bar with the current number of bytes transferred.
     * Throttles rendering to avoid excessive console I/O.
     *
     * @param bytesTransferred Total bytes transferred so far
     */
    public void update(long bytesTransferred) {
        this.currentBytes = bytesTransferred;

        long now = System.currentTimeMillis();
        if (now - lastUpdateMs < MIN_UPDATE_INTERVAL_MS) {
            return; // Throttle updates
        }
        lastUpdateMs = now;

        render();
    }

    /**
     * Increment progress by a delta.
     *
     * @param delta Additional bytes transferred since last call
     */
    public void advance(long delta) {
        update(this.currentBytes + delta);
    }

    /**
     * Mark the transfer as complete and render the final state.
     * Moves to the next line so subsequent output is not overwritten.
     */
    public void finish() {
        if (finished) return;
        finished = true;

        // Force final render at 100% (or current state for indeterminate)
        if (totalBytes > 0) {
            this.currentBytes = totalBytes;
        }
        render();
        out.println(); // Move to next line
    }

    /**
     * Mark the transfer as failed and display an error indicator.
     */
    public void fail() {
        if (finished) return;
        finished = true;
        render();
        out.println("  FAILED");
    }

    // ==================== Rendering ====================

    private void render() {
        long elapsed = System.currentTimeMillis() - startTimeMs;
        double elapsedSec = elapsed / 1000.0;

        StringBuilder sb = new StringBuilder();
        sb.append('\r');

        // Label
        if (!label.isEmpty()) {
            sb.append(label).append("  ");
        }

        if (totalBytes > 0) {
            // Determinate mode
            double fraction = Math.min(1.0, (double) currentBytes / totalBytes);
            int percent = (int) (fraction * 100);

            // Bar: [████████░░░░░░░░]
            sb.append('[');
            int filled = (int) (fraction * BAR_WIDTH);
            for (int i = 0; i < BAR_WIDTH; i++) {
                sb.append(i < filled ? FILLED_CHAR : EMPTY_CHAR);
            }
            sb.append(']');

            // Percentage
            sb.append(String.format(" %3d%%", percent));

            // Bytes transferred / total
            sb.append("  ").append(formatBytes(currentBytes)).append('/').append(formatBytes(totalBytes));

            // Speed
            if (elapsedSec > 0.1) {
                double bytesPerSec = currentBytes / elapsedSec;
                sb.append("  ").append(formatBytes((long) bytesPerSec)).append("/s");

                // ETA
                if (fraction > 0 && fraction < 1.0) {
                    double remainingBytes = totalBytes - currentBytes;
                    double etaSec = remainingBytes / bytesPerSec;
                    sb.append("  ETA ").append(formatDuration((long) etaSec));
                }
            }
        } else {
            // Indeterminate mode
            int spinnerIdx = (int) ((elapsed / 200) % 4);
            char[] spinner = {'◐', '◓', '◑', '◒'};

            sb.append('[').append(spinner[spinnerIdx]).append(']');

            // Bytes transferred
            sb.append("  ").append(formatBytes(currentBytes));

            // Speed
            if (elapsedSec > 0.1) {
                double bytesPerSec = currentBytes / elapsedSec;
                sb.append("  ").append(formatBytes((long) bytesPerSec)).append("/s");
            }
        }

        // Pad with spaces to clear any leftover characters from previous render
        int targetWidth = 100;
        while (sb.length() < targetWidth) {
            sb.append(' ');
        }

        out.print(sb);
        out.flush();
    }

    // ==================== Formatting Helpers ====================

    /**
     * Format byte count to human-readable string (e.g. "1.5MB", "320KB", "64B").
     */
    public static String formatBytes(long bytes) {
        if (bytes < 0) return "0B";

        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2fGB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Format seconds to human-readable duration (e.g. "3s", "1m 23s", "2h 5m").
     */
    public static String formatDuration(long seconds) {
        if (seconds < 0) return "?";
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            return hours + "h " + mins + "m";
        }
    }
}
