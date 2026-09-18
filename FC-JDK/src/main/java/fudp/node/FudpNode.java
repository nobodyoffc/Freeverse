package fudp.node;

import core.crypto.Hash;
import core.crypto.KeyTools;
import fudp.Protocol;
import fudp.connection.ConnectionContext;
import fudp.connection.ConnectionState;
import fudp.connection.PeerConnection;
import fudp.handler.MessageHandler;
import fudp.message.*;
import fudp.metrics.MeterListener;
import fudp.metrics.MeterRecord;
import fudp.packet.Packet;
import fudp.packet.frames.AckFrame;
import fudp.packet.frames.StreamFrame;
import fudp.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main entry point for FUDP Node application layer.
 * Wraps the low-level Protocol and provides high-level messaging APIs.
 */
public class FudpNode implements Protocol.PacketListener {
    private static final Logger log = LoggerFactory.getLogger(FudpNode.class);

    private record RequestEntry(long connectionId, long createdAt) {}

    private final Protocol protocol;
    private final NodeConfig config;
    private final PeerBook peerBook;
    private MessageHandler messageHandler;
    private static final long EPOCH_2024 = 1704067200L; // 2024-01-01 00:00:00 UTC in seconds

    private final int instanceId;  // random 16-bit ID unique per JVM instance (prevents messageId collision between same-FID nodes)
    private final AtomicLong messageIdGenerator;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> cleanupTask;
    private final Map<String, Long> lastPongInfoSent;
    private final List<MeterListener> meterListeners = new CopyOnWriteArrayList<>();
    
    /** Pending notify ACKs for RTT measurement */
    private final Map<Long, Long> pendingNotifyAcks = new ConcurrentHashMap<>();

    /** Pending ACK futures for blocking sendNotifyWaitAck calls */
    private final Map<Long, CompletableFuture<Boolean>> pendingAckFutures = new ConcurrentHashMap<>();

    /** Per-connection, per-stream message assemblers for reassembling chunked messages.
     *  Outer key = connectionId, inner key = streamId.
     *  Scoped per connection to prevent stream ID collisions between multiple connections from the same FID. */
    private final Map<Long, Map<Long, MessageFrameAssembler>> streamAssemblers = new ConcurrentHashMap<>();

    /** Maps inbound requestId to the connectionId it arrived on, for response routing affinity.
     *  Entries are cleaned up when the response is sent or the connection is closed. */
    private final Map<Long, RequestEntry> requestIdToConnectionId = new ConcurrentHashMap<>();

    /**
     * Outbound requests awaiting responses, keyed by messageId. Inbound stream data
     * from the target peer refreshes the entry's idle deadline, so a slow but
     * progressing transfer is never killed mid-flight; only silence times out.
     */
    private final Map<Long, PendingRequest> pendingRequestWatch = new ConcurrentHashMap<>();

    private static final class PendingRequest {
        final String peerId;
        final CompletableFuture<ResponseMessage> future;
        final java.util.function.LongConsumer receiveProgress; // may be null
        volatile long lastActivityMs;

        PendingRequest(String peerId, CompletableFuture<ResponseMessage> future,
                       java.util.function.LongConsumer receiveProgress) {
            this.peerId = peerId;
            this.future = future;
            this.receiveProgress = receiveProgress;
            this.lastActivityMs = System.currentTimeMillis();
        }
    }

    private NodeEventListener eventListener;
    private volatile boolean running = false;

    /** Hard ceiling on a single assembled incoming message (RAM + spilled-to-disk combined). */
    private final long maxAssembledMessageBytes;
    /** Messages whose declared length exceeds this are spilled to a temp file during reassembly. */
    private final long maxInMemoryMessageBytes;
    private final long maxMaterializedMessageBytes;
    /** Directory for spilled reassembly temp files. */
    private final File recvSpillDir;

    // Throttling for large-transfer receive logging
    /** Error code returned to a sender whose NOTIFY is too large to deliver as a byte[]. */
    public static final int ERROR_CODE_PAYLOAD_TOO_LARGE = 2002;

    private static final long RECEIVE_LOG_MIN_BYTES = 1024 * 1024;   // only log transfers > 1MB
    private static final long RECEIVE_LOG_INTERVAL_MS = 5000;        // at most one progress line per 5s per stream

    public FudpNode(byte[] privateKey, NodeConfig config) throws IOException {
        this.config = config;
        // The assembler cap must exceed the largest message the app layer accepts
        // (a full file, plus header slack). Large messages are now spilled to disk
        // during reassembly, so this ceiling no longer sizes the heap — the RAM
        // footprint is bounded by maxInMemoryMessageBytes instead.
        this.maxAssembledMessageBytes = Math.max(
                config.getMaxAssembledMessageBytes(),
                config.getMaxFileSize() + 1024 * 1024);
        this.maxInMemoryMessageBytes = config.getMaxInMemoryMessageBytes();
        this.maxMaterializedMessageBytes = config.getMaxMaterializedMessageBytes();
        this.recvSpillDir = new File(config.getResolvedDataDir(), "recv-spill");
        //noinspection ResultOfMethodCallIgnored
        this.recvSpillDir.mkdirs();
        purgeSpillDir(); // clear any temp files orphaned by a previous run/crash
        this.protocol = new Protocol(privateKey, config.getPort(), config.getResolvedDataDir(),
                config.getMaxPacketSize(), config.getPacingBurstOverride(),
                config.getPacingIntervalNanos(), config.getSocketBufferSize());
        // Share the NodeConfig's DDoSConfig with Protocol so runtime toggling takes effect immediately
        this.protocol.initDDoSDefense(config.getDdosConfig());
        this.protocol.addPacketListener(this);

        String dataDir = config.getResolvedDataDir();
        String localFid = protocol.getLocalFid();
        this.peerBook = new PeerBook(dataDir, config.getAddressCacheTtlMs(), localFid);

        // Create message handler
        this.messageHandler = createMessageHandler(null);

        // Use a random 16-bit instance ID instead of fidHash to prevent messageId
        // collisions when multiple JVM instances run with the same FID (same private key)
        // on different ports. fidHash was deterministic and caused identical IDs.
        this.instanceId = new java.security.SecureRandom().nextInt() & 0xFFFF;
        this.messageIdGenerator = new AtomicLong(0);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.lastPongInfoSent = new ConcurrentHashMap<>();
    }
    
    /**
     * Create MessageHandler with balance management components.
     */
    private MessageHandler createMessageHandler(NodeEventListener listener) {
        MessageHandler.MessageSender messageSender = this::sendMessageToPeer;
        return new MessageHandler(listener, messageSender, this::emitMeter);
    }

    /**
     * Register a meter listener (upper-layer economics or monitoring).
     */
    public void addMeterListener(MeterListener listener) {
        if (listener != null) {
            meterListeners.add(listener);
        }
    }

    /**
     * Remove a meter listener.
     */
    public void removeMeterListener(MeterListener listener) {
        meterListeners.remove(listener);
    }

    /**
     * Emit a metering record to listeners. Transport facts only; no economics.
     */
    private void emitMeter(MeterRecord record) {
        if (record == null || meterListeners.isEmpty()) {
            return;
        }
        for (MeterListener listener : meterListeners) {
            try {
                listener.onMeter(record);
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    /**
     * Send message to peer (implements MessageSender interface).
     */
    private void sendMessageToPeer(String peerId, AppMessage message) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();
        
        byte[] encoded = MessageCodec.encode(message);
        
        // Close stream after sending to indicate message completion
        // This prevents endless retransmissions and signals the request/response cycle is complete
        protocol.sendAndClose(stream, encoded);
        emitMeter(MeterRecord.builder()
                .peerId(peerId)
                .streamId(stream.getStreamId())
                .messageType(message.getType())
                .direction(fudp.metrics.MeterDirection.OUTBOUND)
                .payloadBytes(encoded.length)
                .sendTimestampMillis(System.currentTimeMillis())
                .receiveTimestampMillis(0)
                .retransmitCount(0)
                .build());
    }

    // Lifecycle

    /**
     * Start the node.
     */
    public void start() {
        if (running) return;
        running = true;

        protocol.start();

        // Schedule idle connection cleanup
        long cleanupIntervalMs = config.getIdleConnectionCleanupIntervalMs();
        if (cleanupIntervalMs > 0) {
            cleanupTask = scheduler.scheduleAtFixedRate(
                    this::cleanupIdleConnections,
                    cleanupIntervalMs, cleanupIntervalMs, TimeUnit.MILLISECONDS
            );
        }

        // Apply max connections per FID config
        protocol.getConnectionManager().setMaxConnectionsPerFid(config.getMaxConnectionsPerFid());
        protocol.getConnectionManager().setLossDetectionMinThresholdMs(config.getLossDetectionMinThresholdMs());
    }

    /**
     * Stop the node.
     */
    public void stop() {
        if (!running) return;
        running = false;

        if (cleanupTask != null) {
            cleanupTask.cancel(true);
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        protocol.stop();
        peerBook.save();
    }

    /**
     * Check if node is running.
     */
    public boolean isRunning() {
        return running;
    }

    // Messaging

    /**
     * Generate a globally unique message ID (public API).
     */
    public long generateMessageId() {
        return nextMessageId();
    }

    /**
     * Send a request and wait for response.
     */
    public CompletableFuture<ResponseMessage> request(String peerId, String serviceName, byte[] data) throws IOException {
        return request(peerId, serviceName, data, null);
    }

    /**
     * Send a request with an optional send-progress callback.
     * @param peerId      target peer
     * @param serviceName service identifier
     * @param data        request payload
     * @param progress    callback receiving (bytesSent, totalBytes) — may be null
     */
    public CompletableFuture<ResponseMessage> request(String peerId, String serviceName, byte[] data,
            java.util.function.BiConsumer<Long, Long> progress) throws IOException {
        return request(peerId, serviceName, data, progress, null);
    }

    /**
     * Send a request with optional send-progress and receive-progress callbacks.
     * @param peerId          target peer
     * @param serviceName     service identifier
     * @param data            request payload
     * @param progress        callback receiving (bytesSent, totalBytes) — may be null
     * @param receiveProgress callback receiving cumulative response bytes assembled so far — may be null
     */
    public CompletableFuture<ResponseMessage> request(String peerId, String serviceName, byte[] data,
            java.util.function.BiConsumer<Long, Long> progress,
            java.util.function.LongConsumer receiveProgress) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        long messageId = nextMessageId();
        RequestMessage request = new RequestMessage(messageId, serviceName, data);

        CompletableFuture<ResponseMessage> future = new CompletableFuture<>();
        messageHandler.registerPendingRequest(messageId, future);

        log.debug("[FudpNode] Sending request to {} (messageId={}, streamId={}, service={}, dataLen={})",
                peerId, messageId, stream.getStreamId(), serviceName, data.length);

        byte[] encoded = MessageCodec.encode(request);
        long streamId = stream.getStreamId();
        protocol.sendAndClose(stream, encoded, progress);

        // Clean up the request stream immediately after sending.
        // The server sends its response on a NEW stream, so this client-initiated
        // stream will never receive data. Without cleanup, it leaks in StreamManager.
        conn.getStreamManager().removeStream(streamId);

        emitMeter(MeterRecord.builder()
                .peerId(peerId)
                .streamId(streamId)
                .messageType(MessageType.REQUEST)
                .direction(fudp.metrics.MeterDirection.OUTBOUND)
                .payloadBytes(encoded.length)
                .sendTimestampMillis(System.currentTimeMillis())
                .receiveTimestampMillis(0)
                .retransmitCount(0)
                .build());

        // Idle deadline: base config timeout + extra time for large payloads
        // (1 second per 100KB). Extended whenever response data arrives.
        long idleTimeoutMs = config.getRequestTimeoutMs()
                + (encoded.length / (100L * 1024)) * 1000L;
        watchPendingRequest(messageId, peerId, future, idleTimeoutMs, receiveProgress);

        return future;
    }

    /**
     * Watch a pending request with an idle-based deadline: the timeout fires only after
     * {@code idleTimeoutMs} of no inbound stream data from the peer (see
     * {@link #touchPendingRequests}), never while a response is still arriving.
     */
    private void watchPendingRequest(long messageId, String peerId,
            CompletableFuture<ResponseMessage> future, long idleTimeoutMs,
            java.util.function.LongConsumer receiveProgress) {
        PendingRequest pending = new PendingRequest(peerId, future, receiveProgress);
        pendingRequestWatch.put(messageId, pending);
        future.whenComplete((r, e) -> pendingRequestWatch.remove(messageId));
        scheduleIdleCheck(messageId, pending, idleTimeoutMs);
    }

    private void scheduleIdleCheck(long messageId, PendingRequest pending, long idleTimeoutMs) {
        long delay = Math.max(50, pending.lastActivityMs + idleTimeoutMs - System.currentTimeMillis());
        try {
            scheduler.schedule(() -> {
                if (pending.future.isDone()) {
                    pendingRequestWatch.remove(messageId);
                    return;
                }
                long idle = System.currentTimeMillis() - pending.lastActivityMs;
                if (idle >= idleTimeoutMs) {
                    pendingRequestWatch.remove(messageId);
                    messageHandler.cancelPendingRequest(messageId);
                    pending.future.completeExceptionally(new TimeoutException(
                            "Request timed out after " + idle + "ms without response data"));
                } else {
                    scheduleIdleCheck(messageId, pending, idleTimeoutMs);
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Scheduler stopped (node shutting down) — fail the request rather than leak it
            pendingRequestWatch.remove(messageId);
            messageHandler.cancelPendingRequest(messageId);
            pending.future.completeExceptionally(e);
        }
    }

    /**
     * Activity from a peer refreshes the idle deadline of that peer's pending requests.
     * Two kinds of activity count:
     * - Inbound stream data (a response arriving): bytesAssembled > 0, also drives the
     *   receive-progress callback.
     * - Inbound ACK frames (bytesAssembled == 0): the peer is acknowledging data we are
     *   sending — a large REQUEST still being uploaded. Without this, any upload that
     *   takes longer than the idle budget times out even while transferring fine,
     *   because an upload produces no inbound stream data until it completes.
     * The receiveProgress consumer is invoked in both cases (with 0 for ACK-only
     * activity) so higher-level idle timers (FapiClient) are refreshed too; callers
     * must treat 0 as a keepalive, not as receive progress.
     * Attribution is per-peer (a response arrives on a new stream, so it cannot be
     * matched to a messageId until fully assembled); concurrent requests to the same
     * peer share activity, which errs on the side of waiting.
     */
    private void touchPendingRequests(String peerId, long bytesAssembled) {
        if (pendingRequestWatch.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (PendingRequest pending : pendingRequestWatch.values()) {
            if (pending.peerId.equals(peerId)) {
                pending.lastActivityMs = now;
                if (pending.receiveProgress != null) {
                    try {
                        pending.receiveProgress.accept(bytesAssembled);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /**
     * Send a response on a specific connection (connection-affine routing).
     * This is the preferred method — ensures the response goes back on the same
     * connection the request arrived on.
     *
     * @param connectionId the connection to send the response on
     * @param requestId    the request ID being responded to
     * @param statusCode   status code
     * @param data         response data
     */
    public void respond(long connectionId, long requestId, int statusCode, byte[] data) throws IOException {
        respond(null, connectionId, requestId, statusCode, data);
    }

    /**
     * Send a response on a specific connection, with peerId fallback.
     * If the original connection was evicted, falls back to any connection for the peer.
     */
    public void respond(String peerId, long connectionId, long requestId, int statusCode, byte[] data) throws IOException {
        requestIdToConnectionId.remove(requestId);
        PeerConnection conn = protocol.getConnectionManager().getByConnectionId(connectionId);
        if (conn != null) {
            sendResponseOnConnection(conn, requestId, statusCode, data);
        } else if (peerId != null) {
            messageHandler.sendResponse(peerId, requestId, statusCode, data);
        } else {
            log.warn("[FudpNode] RESP_DROP connId={} reqId={} (no peerId, no fallback)", connectionId, requestId);
        }
    }

    /**
     * Send a response, using the requestId-to-connection mapping for affinity.
     * Falls back to any connection for the peer if the original connection is gone.
     *
     * @param peerId    the peer's FID (fallback routing)
     * @param requestId the request ID being responded to
     * @param statusCode status code
     * @param data       response data
     */
    public void respond(String peerId, long requestId, int statusCode, byte[] data) throws IOException {
        // Try connection-affine routing first
        RequestEntry entry = requestIdToConnectionId.remove(requestId);
        if (entry != null) {
            PeerConnection conn = protocol.getConnectionManager().getByConnectionId(entry.connectionId());
            if (conn != null) {
                sendResponseOnConnection(conn, requestId, statusCode, data);
                return;
            }
            log.debug("[FudpNode] Original connection {} gone for requestId={}, falling back to any connection for {}",
                    entry.connectionId(), requestId, peerId);
        }
        // Fallback: pick any connection for this peer
        messageHandler.sendResponse(peerId, requestId, statusCode, data);
    }

    /**
     * Send a response directly on a specific PeerConnection.
     */
    private void sendResponseOnConnection(PeerConnection conn, long requestId, int statusCode, byte[] data) throws IOException {
        Stream stream = conn.openStream();
        long responseStreamId = stream.getStreamId();
        log.debug("[FudpNode] Sending response requestId={} on streamId={} (conn={})",
                requestId, responseStreamId, conn.getConnectionId());
        ResponseMessage response = new ResponseMessage(requestId, statusCode, data);
        byte[] encoded = MessageCodec.encode(response);
        protocol.sendAndClose(stream, encoded);
        // Clean up the response stream after sending — the receiver creates its own
        // stream object via getOrCreateStream, so this sender-side stream will never
        // receive data and would leak in StreamManager.
        conn.getStreamManager().removeStream(responseStreamId);
        emitMeter(MeterRecord.builder()
                .peerId(conn.getPeerId())
                .streamId(responseStreamId)
                .messageType(MessageType.RESPONSE)
                .direction(fudp.metrics.MeterDirection.OUTBOUND)
                .payloadBytes(encoded.length)
                .sendTimestampMillis(System.currentTimeMillis())
                .receiveTimestampMillis(0)
                .retransmitCount(0)
                .build());
    }

    /**
     * Send a request with streaming binary data.
     * The headerData is the UnifiedCodec header (4-byte length + JSON),
     * and dataStream provides the binary payload which is streamed without full memory load.
     *
     * @param peerId      Target peer FID
     * @param serviceName Service name (SID)
     * @param headerData  The small header bytes (UnifiedCodec header, no binary payload)
     * @param dataStream  InputStream for the binary data to stream
     * @param dataStreamLength Number of bytes to read from dataStream
     * @return CompletableFuture for the response
     * @throws IOException if connection or sending fails
     */
    public CompletableFuture<ResponseMessage> requestWithStream(
            String peerId, String serviceName, byte[] headerData,
            java.io.InputStream dataStream, long dataStreamLength) throws IOException {
        return requestWithStream(peerId, serviceName, headerData, dataStream, dataStreamLength, null);
    }

    /**
     * Send a request with streaming binary data and an activity callback.
     * The receiveProgress consumer doubles as an idle keepalive: it is invoked with
     * assembled response byte counts when the response arrives, and with 0 whenever
     * the peer shows signs of life (ACKs for our outbound upload). Callers running
     * their own idle timers should refresh on every invocation but treat only
     * values &gt; 0 as receive progress.
     */
    public CompletableFuture<ResponseMessage> requestWithStream(
            String peerId, String serviceName, byte[] headerData,
            java.io.InputStream dataStream, long dataStreamLength,
            java.util.function.LongConsumer receiveProgress) throws IOException {

        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        long messageId = nextMessageId();

        // Build the message envelope manually for streaming.
        // Format: type(1) + messageId(8) + flags(1) + payloadLen(varint) + payload
        // payload = serviceNameLen(varint) + serviceName + headerData + streamedBinaryData
        byte[] serviceNameBytes = serviceName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] serviceNameLenVarint = fudp.util.Varint.encode(serviceNameBytes.length);

        long payloadLength = serviceNameLenVarint.length + serviceNameBytes.length
                + headerData.length + dataStreamLength;
        byte[] payloadLenVarint = fudp.util.Varint.encode(payloadLength);

        // Build the fixed envelope + header part
        java.io.ByteArrayOutputStream envelopeOut = new java.io.ByteArrayOutputStream();
        envelopeOut.write(MessageType.REQUEST.getCode());
        java.nio.ByteBuffer idBuf = java.nio.ByteBuffer.allocate(8);
        idBuf.putLong(messageId);
        envelopeOut.write(idBuf.array());
        envelopeOut.write(0); // flags
        envelopeOut.write(payloadLenVarint);
        envelopeOut.write(serviceNameLenVarint);
        envelopeOut.write(serviceNameBytes);
        envelopeOut.write(headerData);
        byte[] envelopeBytes = envelopeOut.toByteArray();

        // Register pending request
        CompletableFuture<ResponseMessage> future = new CompletableFuture<>();
        messageHandler.registerPendingRequest(messageId, future);

        // Combine envelope + binary stream into a single SequenceInputStream
        long totalOnWire = envelopeBytes.length + dataStreamLength;
        java.io.SequenceInputStream combined = new java.io.SequenceInputStream(
                new java.io.ByteArrayInputStream(envelopeBytes),
                dataStream
        );
        protocol.sendAndCloseFromInputStream(stream, combined, totalOnWire);

        emitMeter(MeterRecord.builder()
                .peerId(peerId)
                .streamId(stream.getStreamId())
                .messageType(MessageType.REQUEST)
                .direction(fudp.metrics.MeterDirection.OUTBOUND)
                .payloadBytes((int) Math.min(totalOnWire, Integer.MAX_VALUE))
                .sendTimestampMillis(System.currentTimeMillis())
                .receiveTimestampMillis(0)
                .retransmitCount(0)
                .build());

        // Idle deadline: base config timeout + extra time for large payloads
        // (1 second per 100KB, covering server-side hashing/storing of the upload).
        // Extended whenever response data arrives or the peer ACKs our upload.
        long idleTimeoutMs = config.getRequestTimeoutMs()
                + (totalOnWire / (100L * 1024)) * 1000L;
        watchPendingRequest(messageId, peerId, future, idleTimeoutMs, receiveProgress);

        return future;
    }

    /**
     * Send a streaming response on a specific connection (connection-affine).
     */
    public void respondWithStream(
            long connectionId, long requestId, int statusCode,
            byte[] headerData, java.io.InputStream dataStream, long dataStreamLength) throws IOException {
        respondWithStream(null, connectionId, requestId, statusCode, headerData, dataStream, dataStreamLength);
    }

    /**
     * Send a streaming response on a specific connection, with peerId fallback.
     */
    public void respondWithStream(
            String peerId, long connectionId, long requestId, int statusCode,
            byte[] headerData, java.io.InputStream dataStream, long dataStreamLength) throws IOException {

        requestIdToConnectionId.remove(requestId);
        PeerConnection conn = protocol.getConnectionManager().getByConnectionId(connectionId);
        if (conn == null && peerId != null) {
            log.warn("[FudpNode] Connection {} gone for streaming requestId={}, falling back to any connection for {}",
                    connectionId, requestId, peerId);
            conn = protocol.getConnectionManager().getAnyConnection(peerId);
        }
        if (conn == null) {
            throw new IOException("Connection " + connectionId + " not found for streaming response");
        }
        sendStreamResponseOnConnection(conn, requestId, statusCode, headerData, dataStream, dataStreamLength);
    }

    /**
     * Send a streaming response, using requestId-to-connection mapping for affinity.
     * Falls back to any connection for the peer.
     */
    public void respondWithStream(
            String peerId, long requestId, int statusCode,
            byte[] headerData, java.io.InputStream dataStream, long dataStreamLength) throws IOException {

        RequestEntry entry = requestIdToConnectionId.remove(requestId);
        PeerConnection conn = null;
        if (entry != null) {
            conn = protocol.getConnectionManager().getByConnectionId(entry.connectionId());
        }
        if (conn == null) {
            conn = getOrConnectPeer(peerId);
        }
        sendStreamResponseOnConnection(conn, requestId, statusCode, headerData, dataStream, dataStreamLength);
    }

    /**
     * Internal: send streaming response on a specific connection.
     */
    private void sendStreamResponseOnConnection(
            PeerConnection conn, long requestId, int statusCode,
            byte[] headerData, java.io.InputStream dataStream, long dataStreamLength) throws IOException {
        Stream stream = conn.openStream();

        // Build the response message envelope manually.
        // Format: type(1) + messageId(8) + flags(1) + payloadLen(varint) + payload
        // payload = statusCode(2) + headerData + streamedBinaryData
        long payloadLength = 2 + headerData.length + dataStreamLength;
        byte[] payloadLenVarint = fudp.util.Varint.encode(payloadLength);

        // Build the envelope + header part
        java.io.ByteArrayOutputStream envelopeOut = new java.io.ByteArrayOutputStream();
        envelopeOut.write(MessageType.RESPONSE.getCode());
        java.nio.ByteBuffer idBuf = java.nio.ByteBuffer.allocate(8);
        idBuf.putLong(requestId);
        envelopeOut.write(idBuf.array());
        envelopeOut.write(0); // flags
        envelopeOut.write(payloadLenVarint);
        // Status code (2 bytes, big-endian)
        envelopeOut.write((statusCode >> 8) & 0xFF);
        envelopeOut.write(statusCode & 0xFF);
        envelopeOut.write(headerData);
        byte[] envelopeBytes = envelopeOut.toByteArray();

        // Combine envelope + binary stream
        long totalOnWire = envelopeBytes.length + dataStreamLength;
        java.io.SequenceInputStream combined = new java.io.SequenceInputStream(
                new java.io.ByteArrayInputStream(envelopeBytes),
                dataStream
        );
        protocol.sendAndCloseFromInputStream(stream, combined, totalOnWire);

        emitMeter(MeterRecord.builder()
                .peerId(conn.getPeerId())
                .streamId(stream.getStreamId())
                .messageType(MessageType.RESPONSE)
                .direction(fudp.metrics.MeterDirection.OUTBOUND)
                .payloadBytes((int) Math.min(totalOnWire, Integer.MAX_VALUE))
                .sendTimestampMillis(System.currentTimeMillis())
                .receiveTimestampMillis(0)
                .retransmitCount(0)
                .build());
    }

    /**
     * Send a ping to measure latency.
     */
    public void ping(String peerId) throws IOException {
        ping(peerId, false);
    }

    /**
     * Send a ping, optionally requesting pong info data.
     */
    public void ping(String peerId, boolean wantInfo) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        PingMessage ping = new PingMessage();
        long msgId = nextMessageId();
        ping.setMessageId(msgId);
        ping.setWantInfo(wantInfo);

        log.debug("[FudpNode] Sending ping to peer {} (messageId={}, wantInfo={})", peerId, msgId, wantInfo);
        byte[] encoded = MessageCodec.encode(ping);
        protocol.sendAndClose(stream, encoded);
        log.debug("[FudpNode] Ping sent to peer {} (messageId={})", peerId, msgId);
    }

    /**
     * Send ping and await pong (optionally with info).
     */
    public CompletableFuture<PongMessage> pingAwaitPong(String peerId, boolean wantInfo, long timeoutMs) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        PingMessage ping = new PingMessage();
        long msgId = nextMessageId();
        ping.setMessageId(msgId);
        ping.setWantInfo(wantInfo);

        log.debug("[FudpNode] Sending ping to peer {} (messageId={}, wantInfo={}, timeoutMs={})", peerId, msgId, wantInfo, timeoutMs);
        CompletableFuture<PongMessage> future = messageHandler.awaitPong(msgId);
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                log.warn("[FudpNode] Ping timeout for peer {} (messageId={}, timeoutMs={})", peerId, msgId, timeoutMs);
                messageHandler.cancelPong(msgId);
                // A connection that never became ESTABLISHED points at an
                // unreachable address; drop it so the next attempt reconnects
                // instead of reusing it.
                if (conn.getState() != ConnectionState.ESTABLISHED) {
                    log.debug("[FudpNode] Removing unestablished connection {} to {} after ping timeout",
                            conn.getConnectionId(), conn.getPeerAddress());
                    protocol.getConnectionManager().removeConnection(conn.getConnectionId());
                }
                future.completeExceptionally(new TimeoutException("Ping timeout"));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        byte[] encoded = MessageCodec.encode(ping);
        protocol.sendAndClose(stream, encoded);
        log.debug("[FudpNode] Ping sent to peer {} (messageId={}), waiting for pong", peerId, msgId);
        return future;
    }

    // Notify Transfer

    /**
     * Send a notification to a peer (fire-and-forget).
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @return the message ID
     */
    public long sendNotify(String peerId, byte[] data) throws IOException {
        return sendNotify(peerId, data, NotifyMessage.DATA_TYPE_RAW);
    }

    /**
     * Send a notification to a peer with type hint (fire-and-forget).
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @param dataType the data type hint (0=raw, 1=json, 2=protobuf, etc.)
     * @return the message ID
     */
    public long sendNotify(String peerId, byte[] data, int dataType) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        long messageId = nextMessageId();
        NotifyMessage msg = new NotifyMessage(data, dataType);
        msg.setMessageId(messageId);

        byte[] encoded = MessageCodec.encode(msg);
        protocol.sendAndClose(stream, encoded);
        return messageId;
    }

    /**
     * Send a notification to a peer with delivery confirmation.
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @return the message ID
     */
    public long sendNotifyWithAck(String peerId, byte[] data) throws IOException {
        return sendNotifyWithAck(peerId, data, NotifyMessage.DATA_TYPE_RAW);
    }

    /**
     * Send a notification to a peer with delivery confirmation and type hint.
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @param dataType the data type hint
     * @return the message ID
     */
    public long sendNotifyWithAck(String peerId, byte[] data, int dataType) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        long messageId = nextMessageId();
        NotifyMessage msg = new NotifyMessage(data, dataType);
        msg.setMessageId(messageId);
        msg.setFlag(AppMessage.FLAG_NEED_ACK);

        byte[] encoded = MessageCodec.encode(msg);
        protocol.sendAndClose(stream, encoded);

        // Register for ACK tracking
        pendingNotifyAcks.put(messageId, System.currentTimeMillis());
        return messageId;
    }

    /**
     * Send a notification and block until the peer ACKs or timeout expires.
     * <p>
     * WARNING: Do NOT call from the FUDP receive thread. The ACK arrives on
     * the same receive thread, so blocking it here causes a deadlock where
     * the ACK can never be processed. Use this only from application threads.
     *
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @param timeoutMs max milliseconds to wait for ACK
     * @return true if ACK received within timeout, false otherwise
     */
    public boolean sendNotifyWaitAck(String peerId, byte[] data, long timeoutMs) throws IOException {
        return sendNotifyWaitAck(peerId, data, NotifyMessage.DATA_TYPE_RAW, timeoutMs);
    }

    /**
     * Send a notification with type hint and block until the peer ACKs or timeout expires.
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @param dataType the data type hint
     * @param timeoutMs max milliseconds to wait for ACK
     * @return true if ACK received within timeout, false otherwise
     */
    public boolean sendNotifyWaitAck(String peerId, byte[] data, int dataType, long timeoutMs) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();
        long streamId = stream.getStreamId();

        long messageId = nextMessageId();
        NotifyMessage msg = new NotifyMessage(data, dataType);
        msg.setMessageId(messageId);
        msg.setFlag(AppMessage.FLAG_NEED_ACK);

        CompletableFuture<Boolean> ackFuture = new CompletableFuture<>();
        pendingAckFutures.put(messageId, ackFuture);
        pendingNotifyAcks.put(messageId, System.currentTimeMillis());

        byte[] encoded = MessageCodec.encode(msg);
        protocol.sendAndClose(stream, encoded);

        try {
            return ackFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.debug("[FudpNode] ACK timeout for messageId={} to {}, abandoning retransmissions", messageId, peerId);
            int abandoned = conn.abandonPacketsForStream(streamId);
            if (abandoned > 0) {
                log.debug("[FudpNode] Abandoned {} packets for stream {} to {}", abandoned, streamId, peerId);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            conn.abandonPacketsForStream(streamId);
            return false;
        } catch (ExecutionException e) {
            conn.abandonPacketsForStream(streamId);
            return false;
        } finally {
            pendingAckFutures.remove(messageId);
            pendingNotifyAcks.remove(messageId);
        }
    }

    // Peer Management

    /**
     * Add a peer.
     */
    public void addPeer(String peerId, byte[] publicKey, String host, int port) {
        peerBook.addWithAddress(peerId, publicKey, host, port);
    }

    /**
     * Add a peer with alias.
     */
    public void addPeer(String peerId, byte[] publicKey, String host, int port, String alias) {
        peerBook.addWithAddress(peerId, publicKey, host, port);
        Peer peer = peerBook.get(peerId);
        if (peer != null && alias != null) {
            peerBook.setAlias(peerId, alias);
        }
    }

    /**
     * Promote a verified address to the peer's primary endpoint.
     * Call after the address answered a HELLO so subsequent pings/connects
     * target it instead of a stale persisted endpoint.
     */
    public void promotePeerEndpoint(String peerId, String host, int port) {
        peerBook.promoteEndpoint(peerId, host, port);
    }

    /**
     * Add a currently connected peer to the peer book.
     */
    public boolean addConnectedPeer(String peerId, String alias) {
        PeerConnection conn = protocol.getConnectionManager().getAnyConnection(peerId);
        if (conn != null && conn.getState() == ConnectionState.ESTABLISHED) {
            SocketAddress addr = conn.getPeerAddress();
            if (addr instanceof InetSocketAddress inet) {
                String hostAddr = inet.getAddress() != null ? inet.getAddress().getHostAddress() : inet.getHostString();
                addPeer(peerId, conn.getPeerPublicKey(), hostAddr, inet.getPort(), alias);
                return true;
            }
        }
        return false;
    }

    /**
     * Remove a peer.
     */
    public void removePeer(String peerId) {
        peerBook.remove(peerId);
        // Close all connections for this peer
        try {
            protocol.closeAll(peerId, 0, "Peer removed");
        } catch (IOException e) {
            // Ignore
        }
        for (PeerConnection c : protocol.getConnectionManager().getConnectionsByPeerId(peerId)) {
            cleanupConnectionState(c.getConnectionId());
        }
        protocol.getConnectionManager().removeAllConnections(peerId);
    }

    /**
     * List all known peers.
     */
    public List<Peer> listPeers() {
        return peerBook.list();
    }

    /**
     * Get peer info by FID or alias.
     */
    public Peer getPeer(String identifier) {
        return peerBook.getByIdOrAlias(identifier);
    }

    /**
     * Discover peer public key via HELLO/PUBLIC_KEY.
     */
    public CompletableFuture<byte[]> discoverPublicKey(String host, int port, long timeoutMs) throws IOException {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        if (addr.isUnresolved()) {
            throw new UnknownHostException("Cannot resolve FUDP host " + host + ":" + port);
        }
        return protocol.sendHelloForPublicKey(addr, timeoutMs);
    }

    /**
     * Set alias for a peer.
     */
    public void setAlias(String peerId, String alias) {
        peerBook.setAlias(peerId, alias);
    }

    /**
     * Get public key of a peer if known.
     */
    public byte[] getPeerPublicKey(String peerId) {
        // Try active connection first
        if (protocol.getConnectionManager() != null) {
             PeerConnection conn = protocol.getConnectionManager().getAnyConnection(peerId);
             if (conn != null && conn.getPeerPublicKey() != null) {
                 return conn.getPeerPublicKey();
             }
        }
        // Try peer book
        if (peerBook != null) {
            Peer peer = peerBook.get(peerId);
            if (peer != null) {
                return peer.getPublicKey();
            }
        }
        return null;
    }

    // Events

    /**
     * Set the event listener.
     */
    public void setEventListener(NodeEventListener listener) {
        this.eventListener = listener;
        // Update the existing message handler's listener instead of recreating it,
        // so that in-flight pending requests are not lost.
        this.messageHandler.setEventListener(listener);
    }

    // Protocol.PacketListener implementation

    @Override
    public void onPacketReceived(PeerConnection connection, Packet packet) {
        String peerId = connection.getPeerId();
        long connId = connection.getConnectionId();
        ConnectionContext ctx = ConnectionContext.of(connection);

        // Update peer book with current address
        peerBook.updateFromConnection(peerId, connection.getPeerPublicKey(), connection.getPeerAddress());

        // ACKs from the peer mean our outbound data (e.g. a large request still being
        // uploaded) is getting through — refresh pending-request idle deadlines so a
        // slow upload is not killed mid-transfer. 0 = keepalive, no receive progress.
        for (var frame : packet.getFrames()) {
            if (frame instanceof AckFrame) {
                touchPendingRequests(peerId, 0);
                break;
            }
        }

        // Process stream data
        for (var frame : packet.getFrames()) {
            if (frame instanceof StreamFrame sf) {
                Stream stream = connection.getStream(sf.getStreamId());
                if (stream != null) {
                    // Only poll if stream is still receiving data (not closed)
                    if (stream.getRecvState() != fudp.stream.StreamState.CLOSED) {
                        // Get or create the assembler for this stream, scoped by connectionId
                        // to prevent stream ID collisions between different connections from the same FID
                        long streamId = sf.getStreamId();
                        Map<Long, MessageFrameAssembler> connAssemblers =
                                streamAssemblers.computeIfAbsent(connId, k -> new ConcurrentHashMap<>());
                        MessageFrameAssembler assembler =
                                connAssemblers.computeIfAbsent(streamId,
                                        k -> new MessageFrameAssembler(
                                                maxAssembledMessageBytes, maxInMemoryMessageBytes, recvSpillDir));

                        // Synchronize on the assembler: MessageFrameAssembler is NOT thread-safe,
                        // and onPacketReceived can be called from multiple threads concurrently
                        // for frames belonging to the same stream.
                        List<AssembledMessage> completeMessages = java.util.Collections.emptyList();
                        boolean cleanUp = false;
                        boolean overflow = false;
                        long bufferedBytes = 0;
                        long assembleElapsedMs = 0;
                        synchronized (assembler) {
                            long now = System.currentTimeMillis();
                            if (assembler.getFirstDataMs() == 0) {
                                assembler.setFirstDataMs(now);
                            }
                            // Poll all available data chunks and feed into assembler.
                            // Both addData (buffer overrun) and extractMessages (header
                            // declaring an impossible length) throw IllegalStateException
                            // for messages that can never be assembled.
                            try {
                                byte[] chunk;
                                while ((chunk = stream.poll()) != null) {
                                    if (chunk.length > 0) {
                                        assembler.addData(chunk);
                                    }
                                }
                                completeMessages = assembler.extractMessages();
                            } catch (IllegalStateException e) {
                                // Message larger than the assembler cap — it can never be
                                // delivered. Drop it cleanly (the sender's request will
                                // time out) instead of silently corrupting the assembler.
                                overflow = true;
                                log.error("[FudpNode] Incoming message on stream {} from peer {} exceeded the "
                                        + "assembler limit and is dropped — the sender will get no response: {}",
                                        streamId, peerId, e.getMessage());
                            }

                            if (!overflow) {
                                bufferedBytes = assembler.getBufferedBytes();
                                assembleElapsedMs = now - assembler.getFirstDataMs();

                                // Throttled progress log so large incoming transfers are
                                // visible in the server log while they are still running.
                                if (bufferedBytes >= RECEIVE_LOG_MIN_BYTES
                                        && now - assembler.getLastProgressLogMs() >= RECEIVE_LOG_INTERVAL_MS) {
                                    assembler.setLastProgressLogMs(now);
                                    log.info("[FudpNode] Receiving message on stream {} from {}: {} bytes assembled, {}s elapsed",
                                            streamId, peerId, bufferedBytes, assembleElapsedMs / 1000);
                                }

                                // Fire assembly progress callback for large transfer tracking
                                if (eventListener != null && bufferedBytes > 0) {
                                    eventListener.onStreamAssemblyProgress(peerId, streamId, bufferedBytes);
                                }

                                // Clean up only when the stream's receive side is truly complete:
                                // FIN seen AND all bytes up to the FIN offset assembled in order,
                                // AND the assembler holds no partial message. Checking sf.isFin()
                                // here would be wrong — a FIN frame that arrives ahead of lost or
                                // reordered earlier frames would retire (tombstone) the stream
                                // while data is still missing, permanently dropping the peer's
                                // retransmissions and blackholing the message.
                                if (stream.isRecvComplete() && !assembler.hasPendingData()) {
                                    cleanUp = true;
                                }

                                // FIN seen but earlier bytes still missing: the transfer is
                                // waiting on retransmissions. If the sender abandoned a lost
                                // packet, this is the state the stream is stuck in forever —
                                // log it (throttled) so a stall is diagnosable server-side.
                                if (!cleanUp && stream.isFinSeen() && !stream.isRecvComplete()
                                        && now - assembler.getLastProgressLogMs() >= RECEIVE_LOG_INTERVAL_MS) {
                                    assembler.setLastProgressLogMs(now);
                                    log.warn("[FudpNode] Stream {} from {}: FIN at offset {} but contiguous data ends at {} "
                                            + "({} bytes in {} out-of-order chunks buffered) — waiting for retransmission of the gap",
                                            streamId, peerId, stream.getFinOffset(), stream.getRecvOffset(),
                                            stream.getBufferedBytes(), stream.getBufferedChunkCount());
                                }
                            }
                        }

                        if (overflow) {
                            connAssemblers.remove(streamId);
                            if (connAssemblers.isEmpty()) {
                                streamAssemblers.remove(connId);
                            }
                            // Retire so late frames of the oversized message are dropped
                            // instead of re-creating the stream and assembler.
                            connection.getStreamManager().retireStream(streamId);
                            continue;
                        }

                        // Response data arriving keeps this peer's pending requests alive
                        // and drives their receive-progress callbacks.
                        touchPendingRequests(peerId, bufferedBytes);

                        // Handle messages outside the synchronized block to avoid holding
                        // the lock during potentially slow message processing
                        for (AssembledMessage message : completeMessages) {
                            if (message.length() >= RECEIVE_LOG_MIN_BYTES) {
                                log.info("[FudpNode] Received complete {}-byte message on stream {} from {} in {}s{}",
                                        message.length(), streamId, peerId, assembleElapsedMs / 1000,
                                        message.isFileBacked() ? " (spilled to disk)" : "");
                            }
                            handleIncomingData(ctx, message);
                        }

                        if (cleanUp) {
                            connAssemblers.remove(streamId);
                            // Clean up connection entry if no more assemblers
                            if (connAssemblers.isEmpty()) {
                                streamAssemblers.remove(connId);
                            }
                            // Retire the stream: prevents the stream leak AND tombstones the
                            // ID so late/retransmitted frames can't re-create the stream and
                            // deliver the same message again. Each stream carries exactly one
                            // message; once FIN is received and the message is fully delivered,
                            // any further frame for this stream is a duplicate.
                            connection.getStreamManager().retireStream(streamId);
                        }
                    }
                } else if (connection.getStreamManager().isRetired(sf.getStreamId())) {
                    // Late retransmission of a stream whose message was already
                    // delivered — expected traffic, dropped by the frame handler.
                    log.debug("[FudpNode] Dropping late frame for retired stream {} from peer {} (conn={})",
                            sf.getStreamId(), peerId, connId);
                } else {
                    log.warn("[FudpNode] Missing stream for peer {} streamId={} conn={}", peerId, sf.getStreamId(), connId);
                }
            }
        }
    }

    /**
     * Handle a complete assembled message. In-RAM messages take the normal byte[]
     * decode path; large messages that were spilled to a temp file are decoded
     * directly from the file so their payload is never fully materialised in RAM.
     */
    private void handleIncomingData(ConnectionContext ctx, AssembledMessage message) {
        if (!message.isFileBacked()) {
            handleIncomingData(ctx, message.bytes());
            return;
        }
        handleIncomingFileBacked(ctx, message);
    }

    /** Fixed framing header: type(1) + messageId(8) + flags(1). */
    private static final int FUDP_FIXED_HEADER = 10;

    /**
     * Decode and route a large, file-backed message. RESPONSE payloads (large
     * downloads, e.g. server-to-server disk sync) are delivered as a file-backed
     * {@link ResponseMessage} so adopters can stream them; REQUEST payloads (large
     * uploads) are delivered as a file-backed {@link RequestMessage} — the service
     * name is read from the front of the spill file and the bulk data stays on disk
     * until the handler reads it. NOTIFY payloads (large one-way sends) are delivered
     * as a file-backed {@link NotifyMessage} through the same path as an in-memory
     * NOTIFY, so a NOTIFY that asked for one still gets its NOTIFY_ACK. The temp file
     * is deleted on any failure or for a message type that is never legitimately large
     * here.
     */
    private void handleIncomingFileBacked(ConnectionContext ctx, AssembledMessage message) {
        String peerId = ctx.peerId();
        boolean handedOff = false;
        try {
            long total = message.length();
            // Enough leading bytes for: fixed header + varint payloadLen + (status(2)
            // for RESPONSE, or sidLen varint + a bounded service name for REQUEST).
            int headBytes = (int) Math.min(total, FUDP_FIXED_HEADER + 10L + 10L + 1024L);
            byte[] header = message.readHeader(headBytes);

            MessageType type = MessageCodec.peekType(header);
            long msgId = MessageCodec.peekMessageId(header);
            int flags = header[9] & 0xFF;

            fudp.util.Varint.DecodeResult vr = fudp.util.Varint.decode(header, FUDP_FIXED_HEADER);
            long payloadLength = vr.value;
            int payloadOffset = FUDP_FIXED_HEADER + vr.bytesConsumed;

            if (type == MessageType.RESPONSE) {
                if (payloadLength < 2 || payloadOffset + 2 > header.length) {
                    log.error("[FudpNode] Malformed large RESPONSE from {} (len={}, payloadLen={})",
                            peerId, total, payloadLength);
                    return;
                }
                int statusCode = ((header[payloadOffset] & 0xFF) << 8) | (header[payloadOffset + 1] & 0xFF);
                long dataOffset = payloadOffset + 2L;
                long dataLength = payloadLength - 2;

                ResponseMessage response = new ResponseMessage();
                response.setMessageId(msgId);
                response.setFlags(flags);
                response.setStatusCode(statusCode);
                response.setFileBackedData(message.file(), dataOffset, dataLength);

                log.debug("[FudpNode] Routing file-backed RESPONSE from {} (messageId={}, dataLen={}, spill={})",
                        peerId, msgId, dataLength, message.file().getName());
                handedOff = true;
                messageHandler.handleDecodedMessage(peerId, ctx.connectionId(), response,
                        (int) Math.min(Integer.MAX_VALUE, total));
                return;
            }

            if (type == MessageType.REQUEST) {
                // payload = [sidLen varint][sid][data]; sid is small and read from the header.
                fudp.util.Varint.DecodeResult sidVr = fudp.util.Varint.decode(header, payloadOffset);
                int sidLen = (int) sidVr.value;
                int sidOffset = payloadOffset + sidVr.bytesConsumed;
                if (sidLen < 0 || sidOffset + sidLen > header.length || sidLen > payloadLength) {
                    log.error("[FudpNode] Malformed large REQUEST from {} (len={}, sidLen={}) — service name "
                            + "did not fit in the header window; dropping", peerId, total, sidLen);
                    return;
                }
                String sid = new String(header, sidOffset, sidLen, java.nio.charset.StandardCharsets.UTF_8);
                long dataOffset = sidOffset + (long) sidLen;
                long dataLength = payloadLength - sidVr.bytesConsumed - sidLen;

                RequestMessage request = new RequestMessage();
                request.setMessageId(msgId);
                request.setFlags(flags);
                request.setSid(sid);
                request.setFileBackedData(message.file(), dataOffset, dataLength);

                if (msgId != 0) {
                    requestIdToConnectionId.put(msgId, new RequestEntry(ctx.connectionId(), System.currentTimeMillis()));
                }
                log.debug("[FudpNode] Routing file-backed REQUEST from {} (messageId={}, sid={}, dataLen={}, spill={})",
                        peerId, msgId, sid, dataLength, message.file().getName());
                // The handler owns the temp file; it is reaped by the spill sweeper if the
                // handler never materialises/deletes it (byte[]-based handlers self-clean
                // once they call getData(); streaming handlers should delete when done).
                handedOff = true;
                messageHandler.handleDecodedMessage(peerId, ctx.connectionId(), request,
                        (int) Math.min(Integer.MAX_VALUE, total));
                return;
            }

            if (type == MessageType.NOTIFY) {
                // payload = [dataType(1)][dataLen(4)][data]; both scalars sit in the header window.
                if (payloadLength < 5 || payloadOffset + 5 > header.length) {
                    log.error("[FudpNode] Malformed large NOTIFY from {} (len={}, payloadLen={})",
                            peerId, total, payloadLength);
                    return;
                }
                int dataType = header[payloadOffset] & 0xFF;
                long declaredLen = ((long) (header[payloadOffset + 1] & 0xFF) << 24)
                        | ((long) (header[payloadOffset + 2] & 0xFF) << 16)
                        | ((long) (header[payloadOffset + 3] & 0xFF) << 8)
                        | ((long) (header[payloadOffset + 4] & 0xFF));
                long dataOffset = payloadOffset + 5L;
                long dataLength = payloadLength - 5;
                if (declaredLen != dataLength) {
                    log.error("[FudpNode] Malformed large NOTIFY from {} (len={}): declared dataLen={} "
                            + "but payload carries {}", peerId, total, declaredLen, dataLength);
                    return;
                }

                // onNotifyReceived takes a byte[], so delivering this means allocating
                // dataLength bytes in one go. Reassembly can spill far more than the heap
                // can hold, so refuse past the cap instead of trying — and tell the sender,
                // which is otherwise blocked until its ACK timer runs out.
                if (dataLength > maxMaterializedMessageBytes) {
                    log.warn("[FudpNode] Refusing {}-byte NOTIFY from {} (messageId={}): over the {}-byte "
                            + "materialisation limit for byte[] delivery", dataLength, peerId, msgId,
                            maxMaterializedMessageBytes);
                    sendErrorFor(peerId, ctx.connectionId(), msgId, ERROR_CODE_PAYLOAD_TOO_LARGE,
                            "NOTIFY payload " + dataLength + " exceeds the receiver's "
                                    + maxMaterializedMessageBytes + "-byte limit");
                    return;
                }

                NotifyMessage notify = new NotifyMessage();
                notify.setMessageId(msgId);
                notify.setFlags(flags);
                notify.setDataType(dataType);
                notify.setFileBackedData(message.file(), dataOffset, dataLength);

                log.debug("[FudpNode] Routing file-backed NOTIFY from {} (messageId={}, dataType={}, dataLen={}, spill={})",
                        peerId, msgId, dataType, dataLength, message.file().getName());
                // handedOff stays false on purpose: handleNotifyMessage delivers a byte[]
                // to the listener via NotifyMessage.getData(), so nothing downstream owns
                // the spill file and the finally-block below deletes it.
                handleNotifyMessage(peerId, ctx.connectionId(), notify);
                return;
            }

            // NOTIFY_ACK, PING, PONG and ERROR are small by construction — a >16 MB one is
            // malformed or hostile, so drop it and reclaim the spill file.
            log.warn("[FudpNode] Dropping large file-backed {} message from {} (len={}): unexpected type on the "
                    + "streaming receive path", type, peerId, total);
        } catch (Exception e) {
            log.warn("[FudpNode] Error processing file-backed message from {}: {}", peerId, e.getMessage(), e);
            if (eventListener != null) {
                eventListener.onError(peerId, 1, "Error processing message: " + e.getMessage());
            }
        } finally {
            if (!handedOff) {
                message.deleteBackingFile();
            }
        }
    }

    /** Delete any temp spill files left in the receive-spill directory. */
    private void purgeSpillDir() {
        try {
            File[] files = recvSpillDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    /** Delete spill temp files whose last-modified time is older than {@code maxAgeMs}. */
    private void sweepStaleSpillFiles(long now, long maxAgeMs) {
        File[] files = recvSpillDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            try {
                if (now - f.lastModified() > maxAgeMs) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    /**
     * Handle incoming data from a peer.
     *
     * @param ctx  connection context carrying peerId and connectionId
     * @param data the raw message bytes
     */
    private void handleIncomingData(ConnectionContext ctx, byte[] data) {
        String peerId = ctx.peerId();
        try {
            MessageType type = null;
            long msgId = 0;
            try {
                type = MessageCodec.peekType(data);
                msgId = MessageCodec.peekMessageId(data);
            } catch (Exception ignore) {
                // Best-effort peek only
            }
            
            AppMessage message = MessageCodec.decode(data);

            // Special handling for messages that need immediate response
            if (message.getType() == MessageType.PING) {
                handlePing(peerId, ctx.connectionId(), (PingMessage) message);
                return;
            }

            // Handle notify messages
            switch (message.getType()) {
                case NOTIFY -> {
                    handleNotifyMessage(peerId, ctx.connectionId(), (NotifyMessage) message);
                    return;
                }
                case NOTIFY_ACK -> {
                    handleNotifyAck(peerId, (NotifyAckMessage) message);
                    return;
                }
                case ERROR -> {
                    // An ERROR keyed to a NOTIFY we are still waiting on releases that
                    // waiter now; without this the sender sits out its full ACK timeout
                    // even though the receiver has already said no. The message still
                    // goes on to the handler for logging and onError.
                    failPendingAck(peerId, (ErrorMessage) message);
                }
                default -> {}
            }

            // Route to message handler (REQUEST, RESPONSE, PONG routed here)
            // Record requestId-to-connectionId mapping for connection-affine response routing
            if (type == MessageType.REQUEST && msgId != 0) {
                requestIdToConnectionId.put(msgId, new RequestEntry(ctx.connectionId(), System.currentTimeMillis()));
            }
            if (type == MessageType.RESPONSE) {
                log.debug("[FudpNode] Routing RESPONSE from {} to messageHandler (messageId={})", peerId, msgId);
            }
            messageHandler.handleIncomingData(peerId, ctx.connectionId(), data);

        } catch (Exception e) {
            log.warn("[FudpNode] Error processing message from {}: {}", peerId, e.getMessage(), e);
            if (eventListener != null) {
                eventListener.onError(peerId, 1, "Error processing message: " + e.getMessage());
            }
        }
    }

    /**
     * Handle ping message by sending pong.
     * Only includes pong data when the client explicitly requests it (wantInfo=true).
     */
    /**
     * Periodically close connections that have been idle beyond the configured timeout.
     */
    private void cleanupIdleConnections() {
        try {
            long timeoutMs = config.getIdleConnectionTimeoutMs();
            if (timeoutMs <= 0) return;

            long now = System.currentTimeMillis();
            for (PeerConnection conn : protocol.getConnectionManager().getAllConnections()) {
                long idle = now - conn.getLastActivity().toEpochMilli();
                if (idle > timeoutMs) {
                    long connId = conn.getConnectionId();
                    String peerId = conn.getPeerId();
                    log.info("[FudpNode] Closing idle connection {} for peer {} (idle={}ms, timeout={}ms)",
                            connId, peerId, idle, timeoutMs);
                    try {
                        protocol.close(connId,
                                fudp.packet.frames.ConnectionCloseFrame.IDLE_TIMEOUT, "Idle timeout");
                    } catch (Exception e) {
                        log.warn("[FudpNode] Failed to send close frame for idle connection {}: {}",
                                connId, e.getMessage());
                    }
                    cleanupConnectionState(connId);
                    protocol.getConnectionManager().removeConnection(connId);

                    if (eventListener != null) {
                        eventListener.onPeerDisconnected(peerId, connId);
                    }
                }
            }
            // Clean stale request ID mappings
            long requestCutoff = System.currentTimeMillis() - 2 * config.getRequestTimeoutMs();
            requestIdToConnectionId.entrySet().removeIf(e -> e.getValue().createdAt() < requestCutoff);

            // Clean stale lastPongInfoSent entries
            long pongCutoff = System.currentTimeMillis() - 2 * config.getIdleConnectionTimeoutMs();
            lastPongInfoSent.entrySet().removeIf(e -> e.getValue() < pongCutoff);

            // Sweep orphaned spill temp files: a large message whose file-backed
            // handler never materialised/deleted it leaves a temp file behind. Reap
            // files untouched for longer than the transfer timeout; active/handed-off
            // transfers are much newer than this.
            sweepStaleSpillFiles(System.currentTimeMillis(),
                    Math.max(2 * config.getTransferTimeoutMs(), 600_000L));
        } catch (Exception e) {
            log.warn("[FudpNode] Idle connection cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * Clean up all state associated with a connection being removed.
     */
    private void cleanupConnectionState(long connectionId) {
        // Remove stream assemblers for this connection
        streamAssemblers.remove(connectionId);
        // Remove requestId mappings pointing to this connection
        requestIdToConnectionId.values().removeIf(entry -> entry.connectionId() == connectionId);
        // Remove replay protection window
        protocol.getReplayProtection().removeConnection(connectionId);
        // Note: pendingNotifyAcks is keyed by messageId, not connectionId,
        // so we can't selectively clean by connection. But we can age out stale entries.
        long ackCutoff = System.currentTimeMillis() - config.getRequestTimeoutMs();
        pendingNotifyAcks.entrySet().removeIf(e -> e.getValue() < ackCutoff);
        pendingAckFutures.entrySet().removeIf(e -> e.getValue().isDone());
    }

    private void handlePing(String peerId, long connectionId, PingMessage ping) {
        long messageId = ping.getMessageId();
        try {
            // Use the specific connection the ping arrived on (connection-affine routing)
            PeerConnection conn = protocol.getConnectionManager().getByConnectionId(connectionId);
            if (conn == null) {
                // Fallback to any connection if the original was removed
                conn = protocol.getConnectionManager().getAnyConnection(peerId);
            }
            if (conn == null) {
                log.warn("[FudpNode] Cannot send pong to peer {} (messageId={}): connection not found", peerId, messageId);
                return;
            }

            Stream stream = conn.openStream();
            PongMessage pong = new PongMessage(ping.getTimestamp());
            pong.setMessageId(messageId);
            
            // Only include pong data when client explicitly requests info
            if (ping.isWantInfo()) {
                byte[] pongData = buildPongData(peerId, true);
                if (pongData.length > 0) {
                    pong.setData(pongData);
                }
//                log.debug("[FudpNode] Pong data attached for peer {} (messageId={}, dataSize={})", peerId, messageId, pongData.length);
            }
            // When wantInfo=false, don't include any data in pong

            byte[] encoded = MessageCodec.encode(pong);
            protocol.sendAndClose(stream, encoded);
        } catch (IOException e) {
            log.warn("[FudpNode] Failed to send pong to peer {} (messageId={}): {}", peerId, messageId, e.getMessage());
        } catch (Throwable t) {
            log.warn("[FudpNode] Failed to respond pong for peer {} (messageId={}): {}", peerId, messageId, t.getMessage());
        }
    }

    /**
     * Handle incoming notify message.
     */
    private void handleNotifyMessage(String peerId, long connectionId, NotifyMessage message) {
        // Send ACK if requested
        if (message.hasFlag(AppMessage.FLAG_NEED_ACK)) {
            sendNotifyAck(peerId, connectionId, message.getMessageId());
        }

        // Notify listener
        if (eventListener != null) {
            eventListener.onNotifyReceived(peerId, message.getMessageId(),
                    message.getDataType(), message.getData());
        }
    }

    /**
     * Release a notify sender blocked on an ACK that will never come, because the
     * peer rejected the message instead. Returns quietly when the ERROR refers to
     * something other than a pending notify (a request, say), which the normal
     * error handling deals with.
     */
    private void failPendingAck(String peerId, ErrorMessage error) {
        long forId = error.getMessageId();
        CompletableFuture<Boolean> future = pendingAckFutures.remove(forId);
        if (future == null) return;

        pendingNotifyAcks.remove(forId);
        log.debug("[FudpNode] Peer {} rejected notify messageId={} (code={}): {}",
                peerId, forId, error.getErrorCode(), error.getErrorMessage());
        future.complete(false);
    }

    /**
     * Handle notify acknowledgment.
     */
    private void handleNotifyAck(String peerId, NotifyAckMessage ack) {
        long ackedId = ack.getAckedMessageId();
        Long sendTime = pendingNotifyAcks.remove(ackedId);
        
        if (sendTime != null) {
            long rttMs = System.currentTimeMillis() - sendTime;
            if (eventListener != null) {
                eventListener.onNotifyAck(peerId, ackedId, rttMs);
            }
            log.debug("[FudpNode] Notify ACK received for {}, RTT={}ms", ackedId, rttMs);
        }

        CompletableFuture<Boolean> future = pendingAckFutures.remove(ackedId);
        if (future != null) {
            future.complete(true);
        }
    }

    /**
     * Report a failure to the peer, tagged with the message it refers to so the
     * sender can match it to what it sent and stop waiting.
     */
    private void sendErrorFor(String peerId, long connectionId, long forMessageId,
                              int errorCode, String errorText) {
        try {
            PeerConnection conn = protocol.getConnectionManager().getByConnectionId(connectionId);
            if (conn == null) {
                conn = protocol.getConnectionManager().getAnyConnection(peerId);
            }
            if (conn == null) return;

            Stream stream = conn.openStream();
            ErrorMessage err = new ErrorMessage(errorCode, errorText);
            // Keyed to the offending message, not to this reply: that is how the
            // receiving side matches an ERROR to what it is waiting on.
            err.setMessageId(forMessageId);

            byte[] encoded = MessageCodec.encode(err);
            protocol.sendAndClose(stream, encoded);
        } catch (IOException e) {
            log.debug("[FudpNode] Could not report error to {} for messageId={}: {}",
                    peerId, forMessageId, e.getMessage());
        }
    }

    /**
     * Send notify acknowledgment.
     */
    private void sendNotifyAck(String peerId, long connectionId, long messageId) {
        try {
            PeerConnection conn = protocol.getConnectionManager().getByConnectionId(connectionId);
            if (conn == null) {
                conn = protocol.getConnectionManager().getAnyConnection(peerId);
            }
            if (conn == null) return;

            Stream stream = conn.openStream();
            NotifyAckMessage ack = new NotifyAckMessage(messageId);
            ack.setMessageId(nextMessageId());

            byte[] encoded = MessageCodec.encode(ack);
            protocol.sendAndClose(stream, encoded);
        } catch (IOException e) {
            // Ignore
        }
    }

    // Internal helpers

    /**
     * Get or create connection to a peer.
     */
    private PeerConnection getOrConnectPeer(String peerId) throws IOException {
        // Get peer info
        Peer peer = peerBook.getByIdOrAlias(peerId);
        if (peer == null) {
            throw new IOException("Unknown peer: " + peerId);
        }

        if (!peer.hasAddress()) {
            throw new IOException("No address for peer: " + peerId);
        }

        // Try to find an existing viable connection (pick the best one).
        // An ESTABLISHED connection to the right peer is valid regardless of which
        // endpoint/address it came from — this supports multi-endpoint scenarios
        // (e.g., same peer connecting from 2 different ports).
        // Stale connections are cleaned up by idle timeout, not by address mismatch.
        PeerConnection conn = protocol.getConnectionManager().getAnyConnection(peer.getId());
        if (conn != null) {
            ConnectionState state = conn.getState();
            if (state == ConnectionState.CLOSED || state == ConnectionState.CLOSING) {
                protocol.getConnectionManager().removeConnection(conn.getConnectionId());
                conn = null;
            }
        }

        byte[] publicKey = null;
        if (conn != null && conn.getPeerPublicKey() != null) {
            publicKey = conn.getPeerPublicKey();
        } else if (peer.getPublicKey() != null) {
            publicKey = peer.getPublicKey();
        }

        // Resolve addresses from all known endpoints
        List<Peer.Endpoint> endpoints = peer.getEndpoints();

        if (publicKey == null) {
            // Try to discover public key from each endpoint
            long timeoutMs = Math.max(1000L, config.getConnectionTimeoutMs());
            for (Peer.Endpoint ep : endpoints) {
                try {
                    publicKey = discoverPublicKey(ep.host, ep.port, timeoutMs)
                            .get(timeoutMs, TimeUnit.MILLISECONDS);
                    if (publicKey != null) {
                        SocketAddress addr = new InetSocketAddress(ep.host, ep.port);
                        peerBook.updateFromConnection(peer.getId(), publicKey, addr);
                        // This endpoint just proved reachable; try it first below
                        peerBook.promoteEndpoint(peer.getId(), ep.host, ep.port);
                        endpoints = peer.getEndpoints();
                        break;
                    }
                } catch (Exception e) {
                    // Try next endpoint
                }
            }
            if (publicKey == null) {
                throw new IOException("Failed to discover peer public key: " + peerId);
            }
        }

        if (conn == null) {
            // Try to connect via each known endpoint
            for (Peer.Endpoint ep : endpoints) {
                try {
                    String host = ep.host;
                    InetSocketAddress inetAddr = new InetSocketAddress(host, ep.port);
                    if (inetAddr.isUnresolved()) {
                        InetAddress resolved = InetAddress.getByName(host);
                        inetAddr = new InetSocketAddress(resolved.getHostAddress(), ep.port);
                    }
                    protocol.connect(publicKey, inetAddr);
                    String actualPeerId = KeyTools.pubkeyToFchAddr(publicKey);
                    conn = protocol.getConnectionManager().getAnyConnection(actualPeerId);
                    if (conn != null) break;
                } catch (Exception e) {
                    // Try next endpoint
                }
            }
            if (conn == null) {
                throw new IOException("Failed to connect to peer: " + peerId);
            }
        } else if (conn.getPeerPublicKey() == null && publicKey != null) {
            conn.setPeerPublicKey(publicKey);
        }

        return conn;
    }

    /**
     * Build optional data to include in pong responses when requested.
     */
    private byte[] buildPongData(String peerId, boolean forceInfo) {
        NodeConfig.PongDataProvider provider = config.getPongDataProvider();
        if (provider == null) {
            return new byte[0];
        }

        long now = System.currentTimeMillis();
        long minInterval = config.getPongInfoMinIntervalMs();
        Long last = lastPongInfoSent.get(peerId);
        if (!forceInfo && last != null && now - last < minInterval) {
            return new byte[0];
        }

        byte[] raw;
        try {
            raw = provider.buildPongData(peerId);
        } catch (Throwable t) {
            log.warn("buildPongData: provider threw for {}: {}", peerId, t.getMessage());
            raw = new byte[0];
        }
        if (raw == null) {
            raw = new byte[0];
        }
        int max = Math.max(0, config.getMaxPongDataBytes());
        if (raw.length > max) {
            raw = Arrays.copyOf(raw, max);
        }

        if (!forceInfo) {
            lastPongInfoSent.put(peerId, now);
        } else if (raw.length > 0) {
            // Record last send time only when we actually attached data; keep info pings from throttling others.
            lastPongInfoSent.put(peerId, now);
        }
        return raw;
    }

    /**
     * Generate a globally unique message ID.
     * Structure (8 bytes / 64 bits):
     *   Bits 63-48: SHA256(localFid)[30..31] (sender hash, 16 bits)
     *   Bits 47-16: seconds since epoch 2024-01-01 (32 bits, ~136 years)
     *   Bits 15-0:  per-second sequence counter (16 bits, up to 65535/sec)
     */
    private long nextMessageId() {
        long epochSeconds = (System.currentTimeMillis() / 1000) - EPOCH_2024;
        int seq = (int) (messageIdGenerator.incrementAndGet() & 0xFFFF);
        return ((long) instanceId << 48) | ((epochSeconds & 0xFFFFFFFFL) << 16) | seq;
    }

    // Getters

    /**
     * Get the underlying protocol.
     */
    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Get the local FID.
     */
    public String getLocalFid() {
        return protocol.getLocalFid();
    }

    /**
     * Get the local public key.
     */
    public byte[] getLocalPublicKey() {
        return protocol.getLocalPublicKey();
    }

    /**
     * Get the peer book.
     */
    public PeerBook getPeerBook() {
        return peerBook;
    }

    /**
     * Get the node configuration.
     */
    public NodeConfig getConfig() {
        return config;
    }

    /**
     * Get aggregated node statistics for performance monitoring.
     * Includes RTT, packet loss, throughput, and connection metrics.
     */
    public NodeStats getNodeStats() {
        return NodeStats.fromConnections(protocol.getConnectionManager().getAllConnections());
    }

    /**
     * Get stats for a specific peer.
     * @param peerId the peer ID or alias
     * @return peer stats or null if not found
     */
    public NodeStats.PeerStats getPeerStats(String peerId) {
        Peer peer = peerBook.getByIdOrAlias(peerId);
        if (peer == null) return null;

        PeerConnection conn = protocol.getConnectionManager().getAnyConnection(peer.getId());
        if (conn == null) return null;

        return NodeStats.PeerStats.from(conn);
    }
}
