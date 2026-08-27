package fudp.stream;

import fudp.connection.PeerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
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
    private final AtomicLong dataReceived = new AtomicLong(0);
    private final AtomicLong dataSent = new AtomicLong(0);

    // Local stream ID parity (bit 0): keeps the two endpoints' allocators in
    // DISJOINT ID spaces. Historically both sides allocated 0,4,8,... and only
    // stayed collision-free while their allocators advanced in lockstep (one
    // response per request). Any desync (a rebuilt connection restarting one
    // allocator, a retried request, ...) made one side allocate an ID the
    // other side had already used and RETIRED — its tombstone then silently
    // swallowed the new stream (field failure: responses never delivered).
    // The parity is derived deterministically on both ends from comparing
    // FIDs, so no negotiation is needed: lower FID uses even IDs, higher odd.
    private volatile int localStreamParity = 0;
    private volatile boolean parityInitialized = false;

    public StreamManager(PeerConnection connection) {
        this.connection = connection;
        this.streams = new ConcurrentHashMap<>();
        // Stream ID bit 0: 0 = initiated by lower FID, 1 = initiated by higher
        // (set via initLocalStreamParity once the local FID is known).
        this.nextLocalStreamId = new AtomicLong(0);
    }

    /**
     * Set the local allocator's ID parity (0 or 1). Idempotent; must be called
     * before the first openStream() — Protocol wires it when the connection is
     * first used. No-op once streams have been allocated.
     */
    public void initLocalStreamParity(int parity) {
        if (parityInitialized) return;
        synchronized (this) {
            if (parityInitialized) return;
            localStreamParity = parity & 0x01;
            nextLocalStreamId.compareAndSet(0, localStreamParity);
            parityInitialized = true;
        }
    }

    /**
     * Open a new bidirectional stream.
     * Skips IDs already occupied by remote-initiated streams (created via getOrCreateStream)
     * to avoid overwriting their receive state.
     */
    public Stream openStream() {
        if (streams.size() >= maxLocalStreams) {
            throw new IllegalStateException("Local stream limit exceeded: " + maxLocalStreams);
        }
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
        stream.setConnection(this.connection);
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
        if (!streams.containsKey(streamId) && streams.size() >= maxRemoteStreams) {
            log.warn("Remote stream limit exceeded (max={}), rejecting stream {}", maxRemoteStreams, streamId);
            return null;
        }
        return streams.computeIfAbsent(streamId, id -> {
            Stream s = new Stream(id);
            s.setConnection(this.connection);
            return s;
        });
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
     * Remove a stream unconditionally.
     * Used to clean up streams after their message has been fully delivered.
     */
    public void removeStream(long streamId) {
        streams.remove(streamId);
    }

    // Retired remote streams: FIN received and message fully delivered.
    // A late/retransmitted frame for a retired stream must be DROPPED, not
    // re-create the stream via getOrCreateStream — otherwise the entire message
    // is reassembled and processed a second time (duplicate request/response;
    // for requests this means the server re-executes and re-charges the call).
    // A remote peer never reuses a stream ID on the same connection (its
    // allocator is monotonic), so retirement is permanent; the set is bounded
    // to cap memory. Only remote-completed streams are retired — sender-side
    // cleanup keeps using removeStream, since local and remote stream IDs share
    // the same number space and a tombstone on a local ID could block a
    // legitimate future remote stream.
    private static final int MAX_RETIRED_STREAMS = 4096;
    private final Set<Long> retiredStreams = ConcurrentHashMap.newKeySet();
    private final Deque<Long> retiredOrder = new ConcurrentLinkedDeque<>();

    /**
     * Retire a remote stream after its message has been fully delivered.
     * Subsequent frames for this stream ID are dropped by the frame handler.
     */
    public void retireStream(long streamId) {
        streams.remove(streamId);
        log.debug("[StreamManager] retiring stream {} (conn={})", streamId,
                connection != null ? connection.getConnectionId() : -1);
        if (retiredStreams.add(streamId)) {
            retiredOrder.addLast(streamId);
            while (retiredOrder.size() > MAX_RETIRED_STREAMS) {
                Long oldest = retiredOrder.pollFirst();
                if (oldest != null) {
                    retiredStreams.remove(oldest);
                }
            }
        }
    }

    /**
     * @return true if this remote stream already delivered its message and
     *         incoming frames for it should be dropped.
     */
    public boolean isRetired(long streamId) {
        return retiredStreams.contains(streamId);
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
        return dataSent.get() + bytes <= maxData;
    }

    /**
     * Record sent data
     */
    public void onDataSent(int bytes) {
        dataSent.addAndGet(bytes);
    }

    /**
     * Record received data
     */
    public void onDataReceived(int bytes) {
        dataReceived.addAndGet(bytes);
    }

    /**
     * Reset stream state after peer restart (drop all streams).
     */
    public void resetForRestart() {
        streams.clear();
        nextLocalStreamId.set(localStreamParity);
        dataReceived.set(0);
        dataSent.set(0);
        // The restarted peer's stream IDs start over — old tombstones
        // would wrongly drop its new streams.
        retiredStreams.clear();
        retiredOrder.clear();
    }

    /**
     * Check if connection flow control update needed
     */
    public boolean needsFlowControlUpdate() {
        return dataReceived.get() > maxData / 2;
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
        return dataReceived.get();
    }

    public long getDataSent() {
        return dataSent.get();
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
