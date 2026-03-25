package ui;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.LongConsumer;

/**
 * An InputStream wrapper that reports progress as bytes are read.
 * <p>
 * Wraps any InputStream and invokes a callback with the cumulative
 * number of bytes read so far. This is useful for tracking upload
 * progress when the InputStream is consumed by a transport layer.
 * <p>
 * Example usage:
 * <pre>
 *   ProgressBar progressBar = new ProgressBar("Uploading", fileSize);
 *   try (ProgressInputStream pis = new ProgressInputStream(fileStream, progressBar::update)) {
 *       transport.send(pis);
 *   }
 *   progressBar.finish();
 * </pre>
 *
 * Thread-safe: the progress callback may be invoked from any thread.
 */
public class ProgressInputStream extends FilterInputStream {

    private final LongConsumer progressCallback;
    private long totalBytesRead;

    /**
     * Create a progress-tracking InputStream wrapper.
     *
     * @param in               The underlying InputStream to read from
     * @param progressCallback Callback invoked with cumulative bytes read.
     *                         Can be null (no-op).
     */
    public ProgressInputStream(InputStream in, LongConsumer progressCallback) {
        super(in);
        this.progressCallback = progressCallback;
        this.totalBytesRead = 0;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            totalBytesRead++;
            reportProgress();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytesRead = super.read(b, off, len);
        if (bytesRead > 0) {
            totalBytesRead += bytesRead;
            reportProgress();
        }
        return bytesRead;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        if (skipped > 0) {
            totalBytesRead += skipped;
            reportProgress();
        }
        return skipped;
    }

    /**
     * Get the total number of bytes read so far.
     *
     * @return cumulative bytes read
     */
    public long getTotalBytesRead() {
        return totalBytesRead;
    }

    private void reportProgress() {
        if (progressCallback != null) {
            progressCallback.accept(totalBytesRead);
        }
    }
}
