package fudp.stream;

import fudp.connection.PeerConnection;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Represents a bidirectional stream within a connection
 */
public class Stream {

    private final long streamId;
    private PeerConnection connection;
    private StreamState sendState;
    private StreamState recvState;

    // Send state
    private long sendOffset = 0;
    private long sentData = 0;
    private long maxSendData;

    // Receive state
    private final TreeMap<Long, byte[]> recvBuffer;
    private long recvOffset = 0;
    private long recvData = 0;
    private long maxRecvData;

    // Received data queue for application
    private final LinkedBlockingQueue<byte[]> receivedData;

    // Flow control - generous limit to allow large file transfers on a single stream.
    // Connection-level flow control (StreamManager.maxData) provides the real limit.
    private static final long INITIAL_MAX_STREAM_DATA = 100_000_000; // 100 MB

    public Stream(long streamId) {
        this.streamId = streamId;
        this.sendState = StreamState.OPEN;
        this.recvState = StreamState.OPEN;
        this.recvBuffer = new TreeMap<>();
        this.receivedData = new LinkedBlockingQueue<>();
        this.maxSendData = INITIAL_MAX_STREAM_DATA;
        this.maxRecvData = INITIAL_MAX_STREAM_DATA;
    }

    /**
     * Get the current send offset and increment
     */
    public synchronized long consumeSendOffset(int length) {
        long offset = sendOffset;
        sendOffset += length;
        sentData += length;
        return offset;
    }

    /**
     * Check if we can send data
     */
    public boolean canSend(int length) {
        if (sendState != StreamState.OPEN) return false;
        return sentData + length <= maxSendData;
    }

    /**
     * Process received stream data
     * @return assembled data if continuous, null otherwise
     */
    public synchronized byte[] onDataReceived(long offset, byte[] data, boolean fin) {
        // Duplicate detection: ignore data that has already been processed
        // (offset is before current recvOffset)
        if (offset < recvOffset) {
            // This is duplicate/retransmitted data we've already processed
            if (fin) {
                recvState = StreamState.HALF_CLOSED_REMOTE;
            }
            return null;
        }

        // Check if this exact offset is already in buffer (retransmission of pending data)
        if (recvBuffer.containsKey(offset)) {
            // Already have this data, ignore duplicate
            if (fin) {
                recvState = StreamState.HALF_CLOSED_REMOTE;
            }
            return null;
        }

        // Store in receive buffer
        recvBuffer.put(offset, data);
        recvData += data.length;

        // Try to assemble continuous data
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        while (!recvBuffer.isEmpty()) {
            Map.Entry<Long, byte[]> entry = recvBuffer.firstEntry();

            if (entry.getKey() == recvOffset) {
                output.write(entry.getValue(), 0, entry.getValue().length);
                recvOffset += entry.getValue().length;
                recvData -= entry.getValue().length;
                recvBuffer.pollFirstEntry();
            } else {
                break; // Gap in data
            }
        }

        if (fin) {
            recvState = StreamState.HALF_CLOSED_REMOTE;
        }

        byte[] assembled = output.toByteArray();
        if (assembled.length > 0) {
            receivedData.offer(assembled);
            return assembled;
        }
        return null;
    }

    /**
     * Read data from the stream (blocking)
     */
    public byte[] read() throws InterruptedException {
        return receivedData.take();
    }

    /**
     * Read data from the stream (non-blocking)
     */
    public byte[] poll() {
        return receivedData.poll();
    }

    /**
     * Update max send data (received MAX_STREAM_DATA)
     */
    public void updateMaxSendData(long newMax) {
        if (newMax > maxSendData) {
            maxSendData = newMax;
        }
    }

    /**
     * Check if send window needs update
     */
    public boolean needsFlowControlUpdate() {
        return recvData > maxRecvData / 2;
    }

    /**
     * Get new max receive data for flow control update
     */
    public long getNewMaxRecvData() {
        maxRecvData *= 2;
        return maxRecvData;
    }

    /**
     * Close the send side
     */
    public void closeSend() {
        sendState = StreamState.HALF_CLOSED_LOCAL;
        if (recvState == StreamState.HALF_CLOSED_REMOTE) {
            sendState = StreamState.CLOSED;
            recvState = StreamState.CLOSED;
        }
    }

    /**
     * Check if stream is closed
     */
    public boolean isClosed() {
        return sendState == StreamState.CLOSED && recvState == StreamState.CLOSED;
    }

    // Stream ID helpers
    public boolean isInitiatedByPeer() {
        return (streamId & 0x01) == 1;
    }

    public boolean isUnidirectional() {
        return (streamId & 0x02) == 2;
    }

    public long getStreamNumber() {
        return streamId >> 2;
    }

    // Getters
    public long getStreamId() {
        return streamId;
    }

    public StreamState getSendState() {
        return sendState;
    }

    public StreamState getRecvState() {
        return recvState;
    }

    public long getSendOffset() {
        return sendOffset;
    }

    public long getRecvOffset() {
        return recvOffset;
    }

    public long getMaxSendData() {
        return maxSendData;
    }

    public long getMaxRecvData() {
        return maxRecvData;
    }

    public PeerConnection getConnection() {
        return connection;
    }

    public void setConnection(PeerConnection connection) {
        this.connection = connection;
    }

    @Override
    public String toString() {
        return String.format("Stream[id=%d, send=%s, recv=%s]", streamId, sendState, recvState);
    }
}
