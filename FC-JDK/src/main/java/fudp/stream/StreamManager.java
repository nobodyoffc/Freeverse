package fudp.stream;

import fudp.connection.PeerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages streams within a connection
 */
public class StreamManager {
    private static final Logger log = LoggerFactory.getLogger(StreamManager.class);

    private final PeerConnection connection;
    private final Map<Long, Stream> streams;
    private final AtomicLong nextLocalStreamId;
    private long maxLocalStreams = 100;
    private long maxRemoteStreams = 100;

    // Flow control
    private long maxData = 10485760; // 10 MB connection level
    private long dataReceived = 0;
    private long dataSent = 0;

    public StreamManager(PeerConnection connection) {
        this.connection = connection;
        this.streams = new ConcurrentHashMap<>();
        // Stream ID bit 0: 0 = initiated by lower public key, 1 = initiated by higher
        // For now, we'll use even numbers for local streams
        this.nextLocalStreamId = new AtomicLong(0);
    }

    /**
     * Open a new bidirectional stream.
     * Skips IDs already occupied by remote-initiated streams (created via getOrCreateStream)
     * to avoid overwriting their receive state.
     */
    public Stream openStream() {
        long streamId;
        int skipped = 0;
        do {
            streamId = nextLocalStreamId.getAndAdd(4); // Increment by 4 (bits 0-1 are flags)
            if (!streams.containsKey(streamId)) break;
            skipped++;
            if (skipped <= 3) {
                log.debug("[StreamManager] Skipping stream ID {} (occupied by remote), trying next", streamId);
            }
        } while (skipped < 1000); // safety bound

        Stream stream = new Stream(streamId);
        streams.put(streamId, stream);
        return stream;
    }

    /**
     * Open a new unidirectional stream
     */
    public Stream openUnidirectionalStream() {
        long streamId = nextLocalStreamId.getAndAdd(4) | 0x02; // Set unidirectional bit
        Stream stream = new Stream(streamId);
        streams.put(streamId, stream);
        return stream;
    }

    /**
     * Get or create a stream (for receiving)
     */
    public Stream getOrCreateStream(long streamId) {
        return streams.computeIfAbsent(streamId, Stream::new);
    }

    /**
     * Get an existing stream
     */
    public Stream getStream(long streamId) {
        return streams.get(streamId);
    }

    /**
     * Close a stream
     */
    public void closeStream(long streamId) {
        Stream stream = streams.get(streamId);
        if (stream != null) {
            stream.closeSend();
            if (stream.isClosed()) {
                streams.remove(streamId);
            }
        }
    }

    /**
     * Get all streams
     */
    public Collection<Stream> getAllStreams() {
        return streams.values();
    }

    /**
     * Get stream count
     */
    public int getStreamCount() {
        return streams.size();
    }

    /**
     * Connection-level flow control: record sent data
     */
    public boolean canSendData(int bytes) {
        return dataSent + bytes <= maxData;
    }

    /**
     * Record sent data
     */
    public void onDataSent(int bytes) {
        dataSent += bytes;
    }

    /**
     * Record received data
     */
    public void onDataReceived(int bytes) {
        dataReceived += bytes;
    }

    /**
     * Reset stream state after peer restart (drop all streams).
     */
    public void resetForRestart() {
        streams.clear();
        nextLocalStreamId.set(0);
        dataReceived = 0;
        dataSent = 0;
    }

    /**
     * Check if connection flow control update needed
     */
    public boolean needsFlowControlUpdate() {
        return dataReceived > maxData / 2;
    }

    /**
     * Get new max data for flow control update
     */
    public long getNewMaxData() {
        maxData *= 2;
        return maxData;
    }

    /**
     * Update max data (received MAX_DATA)
     */
    public void updateMaxData(long newMax) {
        if (newMax > maxData) {
            maxData = newMax;
        }
    }

    // Getters
    public long getMaxData() {
        return maxData;
    }

    public long getDataReceived() {
        return dataReceived;
    }

    public long getDataSent() {
        return dataSent;
    }

    public long getMaxLocalStreams() {
        return maxLocalStreams;
    }

    public void setMaxLocalStreams(long maxLocalStreams) {
        this.maxLocalStreams = maxLocalStreams;
    }

    public long getMaxRemoteStreams() {
        return maxRemoteStreams;
    }

    public void setMaxRemoteStreams(long maxRemoteStreams) {
        this.maxRemoteStreams = maxRemoteStreams;
    }
}
