package fudp.message;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * InputStream over a bounded {@code [offset, offset+length)} region of a file.
 * Used to stream a file-backed message payload (a large upload/download spilled to
 * a temp file during reassembly) without materialising it in memory.
 */
public final class FileRegionInputStream extends InputStream {
    private final RandomAccessFile raf;
    private long remaining;

    public FileRegionInputStream(File file, long offset, long length) throws IOException {
        this.raf = new RandomAccessFile(file, "r");
        this.raf.seek(offset);
        this.remaining = length;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) return -1;
        int b = raf.read();
        if (b >= 0) remaining--;
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) return -1;
        int toRead = (int) Math.min(len, remaining);
        int n = raf.read(b, off, toRead);
        if (n > 0) remaining -= n;
        return n;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
