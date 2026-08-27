package fudp.node;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A complete FUDP message extracted by {@link MessageFrameAssembler}, held either
 * in RAM (small messages) or spilled to a temp file (large messages).
 * <p>
 * The file-backed variant lets the receive path decode a multi-MB message without
 * ever materialising it fully on the heap: the small framing headers are read from
 * the front of the file, and the bulk payload is streamed on to its final
 * destination (e.g. a download's output file, or delivered as a file-backed
 * request/response).
 * <p>
 * Ownership: for the file-backed variant the caller that consumes the message owns
 * the temp file and must call {@link #deleteBackingFile()} once done. The file is
 * also registered for {@code deleteOnExit} as a backstop.
 */
public final class AssembledMessage {

    private final byte[] bytes;   // non-null when in-memory
    private final File file;      // non-null when spilled to disk
    private final long length;    // total message length in bytes

    private AssembledMessage(byte[] bytes, File file, long length) {
        this.bytes = bytes;
        this.file = file;
        this.length = length;
    }

    public static AssembledMessage ofBytes(byte[] bytes) {
        return new AssembledMessage(bytes, null, bytes.length);
    }

    public static AssembledMessage ofFile(File file, long length) {
        file.deleteOnExit();
        return new AssembledMessage(null, file, length);
    }

    /** Total message length (framing header + payload). */
    public long length() {
        return length;
    }

    public boolean isFileBacked() {
        return file != null;
    }

    /** The in-memory bytes, or {@code null} when file-backed. */
    public byte[] bytes() {
        return bytes;
    }

    /** The backing temp file, or {@code null} when in-memory. */
    public File file() {
        return file;
    }

    /**
     * Read the first {@code n} bytes of the message (the small framing headers).
     * Never allocates more than {@code n} bytes.
     */
    public byte[] readHeader(int n) throws IOException {
        if (n < 0 || n > length) {
            throw new IllegalArgumentException("readHeader out of range: n=" + n + " length=" + length);
        }
        byte[] head = new byte[n];
        if (bytes != null) {
            System.arraycopy(bytes, 0, head, 0, n);
            return head;
        }
        try (InputStream in = new FileInputStream(file)) {
            int off = 0;
            while (off < n) {
                int r = in.read(head, off, n - off);
                if (r < 0) throw new IOException("Unexpected EOF reading header from spill file");
                off += r;
            }
        }
        return head;
    }

    /** Open a stream over the entire message (headers + payload). */
    public InputStream openStream() throws IOException {
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        return new BufferedInputStream(new FileInputStream(file));
    }

    /** Delete the backing temp file if this message is file-backed; no-op otherwise. */
    public void deleteBackingFile() {
        if (file != null) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
