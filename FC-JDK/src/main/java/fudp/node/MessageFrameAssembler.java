package fudp.node;

import fudp.util.Varint;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reassembles complete FUDP messages from chunked stream data.
 * <p>
 * When Protocol.send() splits large data into multiple StreamFrames,
 * the receiver's Stream delivers data incrementally via poll().
 * This assembler buffers incoming chunks and extracts complete messages
 * using the MessageCodec framing format:
 * <pre>
 *   type(1 byte) + messageId(8 bytes) + flags(1 byte) + payloadLength(varint) + payload
 * </pre>
 * <p>
 * Memory: messages whose declared total is at or below {@code spillThreshold} are
 * buffered entirely in RAM (fast path). Larger messages are streamed to a temp file
 * as their chunks arrive, so a multi-MB transfer never materialises fully on the
 * heap. Either way a complete message is delivered as an {@link AssembledMessage}
 * (bytes-backed or file-backed). {@code maxBufferSize} is the hard ceiling on a
 * single message (RAM + spill combined).
 * <p>
 * Performance: appending an in-RAM chunk is amortized O(chunk). The message length is
 * parsed from the header ONCE and cached; completeness checks compare plain integers.
 * <p>
 * Thread-safety: Not thread-safe. Each instance should be used by a single thread
 * or externally synchronized.
 */
public class MessageFrameAssembler {

    private static final int FIXED_HEADER_SIZE = 10; // type(1) + messageId(8) + flags(1)
    private static final int INITIAL_CAPACITY = 8 * 1024;
    /** Varint length field is at most 10 bytes on the wire. */
    private static final int MAX_VARINT_BYTES = 10;

    /** Default cap on a single assembled message when no explicit limit is given. */
    public static final long DEFAULT_MAX_BUFFER_SIZE = 64L * 1024 * 1024; // 64 MB
    /** Default threshold above which a message spills to a temp file instead of RAM. */
    public static final long DEFAULT_SPILL_THRESHOLD = 16L * 1024 * 1024; // 16 MB

    private final long maxBufferSize;
    private final long spillThreshold;
    private final File spillDir;

    // ── RAM path ────────────────────────────────────────────────────────────
    private byte[] buf = new byte[INITIAL_CAPACITY];
    private int size = 0;
    /** Total length of the in-progress message, parsed once from its header; -1 = unknown yet. */
    private int expectedTotal = -1;

    // ── Spill path (active only for the current large message) ──────────────
    private File spillFile;
    private BufferedOutputStream spillOut;
    private long spilledBytes;

    // Receive-progress tracking, managed by the owner (FudpNode) under its
    // per-assembler lock: when the first chunk arrived and when progress /
    // gap status was last logged, so large-transfer logging can be throttled.
    private long firstDataMs;
    private long lastProgressLogMs;

    public MessageFrameAssembler() {
        this(DEFAULT_MAX_BUFFER_SIZE, DEFAULT_SPILL_THRESHOLD, null);
    }

    public MessageFrameAssembler(long maxBufferSize) {
        this(maxBufferSize, DEFAULT_SPILL_THRESHOLD, null);
    }

    /**
     * @param maxBufferSize  hard ceiling on a single assembled message (RAM + spill).
     * @param spillThreshold messages whose declared total exceeds this are spilled to
     *                       a temp file rather than buffered in RAM.
     * @param spillDir       directory for temp spill files; if null, the JVM default
     *                       temp directory is used.
     */
    public MessageFrameAssembler(long maxBufferSize, long spillThreshold, File spillDir) {
        this.maxBufferSize = maxBufferSize;
        this.spillThreshold = Math.min(spillThreshold, maxBufferSize);
        this.spillDir = spillDir;
        if (spillDir != null) {
            //noinspection ResultOfMethodCallIgnored
            spillDir.mkdirs();
        }
    }

    /**
     * Add received data chunk to the assembler.
     *
     * @param data The data chunk from stream.poll()
     * @throws IllegalStateException if the message would exceed {@code maxBufferSize},
     *         or the header declares a length beyond it; the assembler is reset and
     *         the in-flight message is unrecoverable.
     */
    public void addData(byte[] data) {
        if (data == null || data.length == 0) return;

        if (spillFile != null) {
            if (spilledBytes + (long) data.length > maxBufferSize) {
                long buffered = spilledBytes;
                reset();
                throw new IllegalStateException("Assembler spill exceeded max size: buffered=" + buffered
                        + " + chunk=" + data.length + " > max=" + maxBufferSize);
            }
            try {
                spillOut.write(data);
            } catch (IOException e) {
                reset();
                throw new IllegalStateException("Failed to write spill file: " + e.getMessage(), e);
            }
            spilledBytes += data.length;
            return;
        }

        if (size + (long) data.length > maxBufferSize) {
            int buffered = size;
            reset();
            throw new IllegalStateException("Assembler buffer exceeded max size: buffered=" + buffered
                    + " + chunk=" + data.length + " > max=" + maxBufferSize);
        }
        ensureCapacity(size + data.length);
        System.arraycopy(data, 0, buf, size, data.length);
        size += data.length;

        // As soon as the header reveals a large message, switch to spill mode so the
        // bulk of it never accumulates in RAM.
        maybeStartSpilling();
    }

    private void ensureCapacity(int needed) {
        if (buf.length >= needed) return;
        int newCap = Math.max(needed, buf.length * 2);
        if (newCap > maxBufferSize) newCap = (int) Math.min(Integer.MAX_VALUE, maxBufferSize);
        buf = Arrays.copyOf(buf, Math.max(newCap, needed));
    }

    /**
     * Parse the message header (once) to learn its total length. Returns true if
     * {@link #expectedTotal} is now known, false if more header bytes are needed.
     *
     * @throws IllegalStateException if the declared length exceeds {@code maxBufferSize}
     */
    private boolean parseExpectedTotal() {
        if (expectedTotal >= 0) return true;
        if (size < FIXED_HEADER_SIZE) return false;
        try {
            int headerEnd = (int) Math.min(size, (long) FIXED_HEADER_SIZE + MAX_VARINT_BYTES);
            Varint.DecodeResult varintResult =
                    Varint.decode(Arrays.copyOfRange(buf, 0, headerEnd), FIXED_HEADER_SIZE);
            long payloadLength = varintResult.value;
            long total = FIXED_HEADER_SIZE + varintResult.bytesConsumed + payloadLength;
            if (payloadLength < 0 || total > maxBufferSize) {
                int buffered = size;
                reset();
                throw new IllegalStateException("Declared message length " + total
                        + " exceeds max buffer size " + maxBufferSize + " (buffered=" + buffered + ")");
            }
            expectedTotal = (int) total;
            return true;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // Varint incomplete or corrupted — wait for more data.
            return false;
        }
    }

    /** If the current message is large, move whatever is buffered in RAM to a temp file. */
    private void maybeStartSpilling() {
        if (spillFile != null) return;
        if (!parseExpectedTotal()) return;
        if (expectedTotal <= spillThreshold) return;

        try {
            File f = File.createTempFile("fudp-recv-", ".spill", spillDir);
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
            if (size > 0) {
                out.write(buf, 0, size);
            }
            spillFile = f;
            spillOut = out;
            spilledBytes = size;
        } catch (IOException e) {
            reset();
            throw new IllegalStateException("Failed to create spill file: " + e.getMessage(), e);
        }
        // Release the RAM buffer; the message now lives on disk.
        size = 0;
        buf = new byte[INITIAL_CAPACITY];
    }

    /**
     * Try to extract all complete messages from the buffer.
     *
     * @return complete messages (may be empty if none are complete yet)
     */
    public List<AssembledMessage> extractMessages() {
        List<AssembledMessage> messages = new ArrayList<>();
        while (true) {
            AssembledMessage msg = tryExtractOneMessage();
            if (msg == null) break;
            messages.add(msg);
        }
        return messages;
    }

    private AssembledMessage tryExtractOneMessage() {
        // Spill path: the whole message is being streamed to a temp file.
        if (spillFile != null) {
            if (expectedTotal < 0 || spilledBytes < expectedTotal) {
                return null;
            }
            File completed = spillFile;
            int total = expectedTotal;
            try {
                spillOut.flush();
                spillOut.close();
            } catch (IOException e) {
                // Best-effort: still hand off the file; consumer bounds reads by length.
            }
            spillFile = null;
            spillOut = null;
            spilledBytes = 0;
            expectedTotal = -1;
            return AssembledMessage.ofFile(completed, total);
        }

        // RAM path.
        if (!parseExpectedTotal()) {
            return null;
        }
        if (size < expectedTotal) {
            return null;
        }

        byte[] message = Arrays.copyOfRange(buf, 0, expectedTotal);
        int remainder = size - expectedTotal;
        if (remainder > 0) {
            System.arraycopy(buf, expectedTotal, buf, 0, remainder);
        }
        size = remainder;
        expectedTotal = -1;
        shrinkIfIdle();
        return AssembledMessage.ofBytes(message);
    }

    /** Release a large backing array once the buffer is (nearly) empty. */
    private void shrinkIfIdle() {
        if (size <= INITIAL_CAPACITY && buf.length > 1024 * 1024) {
            buf = Arrays.copyOf(buf, Math.max(INITIAL_CAPACITY, size));
        }
    }

    /**
     * @return true if there is buffered data (RAM or spilled) waiting for more chunks
     */
    public boolean hasPendingData() {
        return size > 0 || spillFile != null;
    }

    /**
     * @return bytes currently buffered for the in-progress message (RAM or spilled)
     */
    public long getBufferedBytes() {
        return spillFile != null ? spilledBytes : size;
    }

    /** @deprecated use {@link #getBufferedBytes()} */
    @Deprecated
    public int getBufferSize() {
        return (int) Math.min(Integer.MAX_VALUE, getBufferedBytes());
    }

    /**
     * Reset the assembler, discarding any buffered data and deleting an in-progress
     * spill file.
     */
    public void reset() {
        size = 0;
        expectedTotal = -1;
        if (buf.length > 1024 * 1024) {
            buf = new byte[INITIAL_CAPACITY];
        }
        if (spillOut != null) {
            try { spillOut.close(); } catch (IOException ignored) {}
            spillOut = null;
        }
        if (spillFile != null) {
            //noinspection ResultOfMethodCallIgnored
            spillFile.delete();
            spillFile = null;
        }
        spilledBytes = 0;
    }

    public long getFirstDataMs() {
        return firstDataMs;
    }

    public void setFirstDataMs(long firstDataMs) {
        this.firstDataMs = firstDataMs;
    }

    public long getLastProgressLogMs() {
        return lastProgressLogMs;
    }

    public void setLastProgressLogMs(long lastProgressLogMs) {
        this.lastProgressLogMs = lastProgressLogMs;
    }
}
