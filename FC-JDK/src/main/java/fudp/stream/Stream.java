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
    // End offset of the stream (offset + length of the FIN frame), or -1 until
    // a FIN frame has been seen. The FIN frame may arrive out of order, so
    // seeing it does NOT mean all preceding data has arrived — receive is only
    // complete once recvOffset has advanced to this offset (see isRecvComplete).
    private long finOffset = -1;

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
     * @throws FlowControlViolationException if the peer would push more buffered
     *         out-of-order data than {@code maxRecvData} permits. Caller should
     *         drop the frame and may close the connection.
     */
    public synchronized byte[] onDataReceived(long offset, byte[] data, boolean fin) {
        // Duplicate detection: ignore data that has already been processed
        // (offset is before current recvOffset)
        if (offset < recvOffset) {
            // This is duplicate/retransmitted data we've already processed
            if (fin) {
                recvState = StreamState.HALF_CLOSED_REMOTE;
                finOffset = offset + data.length;
            }
            return null;
        }

        // Check if this exact offset is already in buffer (retransmission of pending data)
        if (recvBuffer.containsKey(offset)) {
            // Already have this data, ignore duplicate
            if (fin) {
                recvState = StreamState.HALF_CLOSED_REMOTE;
                finOffset = offset + data.length;
            }
            return null;
        }

        // Receiver-side flow control. recvData tracks bytes currently *buffered*
        // (out-of-order data waiting for gap fill). A misbehaving peer that
        // ignores MAX_STREAM_DATA could otherwise balloon receiver memory by
        // sending unending out-of-order chunks; cap the buffered amount and
        // signal a flow-control violation. Note: in-order data does not stay
        // in recvBuffer — it is drained into receivedData below — so legitimate
        // bulk transfers do not hit this limit.
        //
        // The cap applies ONLY to out-of-order frames (offset > recvOffset),
        // which are what actually get buffered. An in-order frame
        // (offset == recvOffset) is the gap-filling frame: it is drained
        // immediately and can trigger a large contiguous drain of the buffer,
        // so it can only *reduce* recvData, never grow it. It must never be
        // rejected — doing so deadlocks the transfer, because once the buffer
        // is near maxRecvData behind a lost early frame, the retransmitted
        // frame that would fill the gap and free the whole buffer would itself
        // be rejected here, and the connection torn down as a flow-control
        // violation (killing every other stream on it too).
        if (offset > recvOffset && recvData + data.length > maxRecvData) {
            throw new FlowControlViolationException(
                    "Stream " + streamId + " buffered " + recvData
                    + " bytes, +" + data.length + " would exceed maxRecvData=" + maxRecvData);
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
            finOffset = offset + data.length;
        }

        byte[] assembled = output.toByteArray();
        if (assembled.length > 0) {
            receivedData.offer(assembled);
            return assembled;
        }
        return null;
    }

    /**
     * @return true once the FIN frame has been seen AND every byte up to the
     *         FIN offset has been assembled in order. Only then has the peer's
     *         message been fully received — a FIN frame alone is not enough,
     *         since it can arrive ahead of lost/reordered earlier frames.
     */
    public synchronized boolean isRecvComplete() {
        return finOffset >= 0 && recvOffset >= finOffset;
    }

    /**
     * @return true once a FIN frame has been seen (regardless of whether all
     *         preceding data has arrived — see {@link #isRecvComplete()}).
     */
    public synchronized boolean isFinSeen() {
        return finOffset >= 0;
    }

    /** @return the end offset of the stream, or -1 until a FIN frame has been seen. */
    public synchronized long getFinOffset() {
        return finOffset;
    }

    /** @return bytes currently buffered out-of-order, waiting for a gap to be filled. */
    public synchronized long getBufferedBytes() {
        return recvData;
    }

    /** @return number of out-of-order chunks currently buffered. */
    public synchronized int getBufferedChunkCount() {
        return recvBuffer.size();
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
