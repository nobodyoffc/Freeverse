package fudp.node;

import fudp.message.NotifyMessage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Read access to a received NOTIFY's payload, whether it is held in memory or
 * left in a reassembly spill file on disk.
 *
 * <p>Handed to {@link NodeEventListener#onNotifyStream} so a listener can consume
 * a large notify without it ever becoming a single {@code byte[]}. The node keeps
 * ownership of the backing file and reclaims it once the callback returns, so the
 * payload must be consumed inside the callback: neither this object nor a stream
 * opened from it may be retained past it.
 */
public final class NotifyPayload {

    private final NotifyMessage message;

    NotifyPayload(NotifyMessage message) {
        this.message = message;
    }

    /** Payload length in bytes, known without reading it. */
    public long length() {
        return message.dataLength();
    }

    /** True when the payload is on disk rather than in memory. */
    public boolean isFileBacked() {
        return message.isFileBacked();
    }

    /**
     * Open a stream over the payload. The caller closes it. Works the same for an
     * in-memory and a file-backed notify, so a listener needs only this one path.
     */
    public InputStream open() throws IOException {
        return message.openData();
    }

    /**
     * Read the whole payload into memory. Provided for the cases where a listener
     * knows the payload is small; for anything that may be large, prefer
     * {@link #open()}, since this allocates {@link #length()} bytes at once and is
     * not subject to the node's materialisation limit.
     */
    public byte[] readAll() {
        return message.getData();
    }

    @Override
    public String toString() {
        return "NotifyPayload{length=" + length() + ", fileBacked=" + isFileBacked() + '}';
    }
}
