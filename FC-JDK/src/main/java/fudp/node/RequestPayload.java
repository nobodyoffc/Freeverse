package fudp.node;

import fudp.message.RequestMessage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Read access to a received REQUEST's data that was left in a reassembly spill
 * file on disk, as a large upload is.
 *
 * <p>Handed to {@link NodeEventListener#onRequestStream} so a listener can consume
 * the upload without it ever becoming a single {@code byte[]}. Unlike a
 * {@link NotifyPayload}, the listener owns the backing file: a request is usually
 * answered on another thread, so the payload may outlive the callback. The
 * listener must call {@link #release()} once it is done, whatever the outcome;
 * the spill sweeper reclaims a file that is never released, but only later.
 */
public final class RequestPayload {

    private final RequestMessage message;

    private RequestPayload(RequestMessage message) {
        this.message = message;
    }

    /** For the node's message handler, which hands a spilled request over. */
    public static RequestPayload of(RequestMessage message) {
        return new RequestPayload(message);
    }

    /** Data length in bytes, known without reading it. */
    public long length() {
        return message.dataLength();
    }

    /** Open a stream over the data. The caller closes it; it may be opened more than once. */
    public InputStream open() throws IOException {
        return message.openData();
    }

    /**
     * Read the whole data into memory. For a listener that finds, after reading
     * the header, that this request is not one it streams; it allocates
     * {@link #length()} bytes at once.
     */
    public byte[] readAll() {
        return message.getData();
    }

    /** Delete the backing file. Call once, when done with the data. */
    public void release() {
        message.deleteBackingFile();
    }

    @Override
    public String toString() {
        return "RequestPayload{length=" + length() + '}';
    }
}
