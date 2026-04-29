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
import fudp.packet.frames.StreamFrame;
import fudp.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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

    private NodeEventListener eventListener;
    private volatile boolean running = false;
    
    public FudpNode(byte[] privateKey, NodeConfig config) throws IOException {
        this.config = config;
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

        // Dynamic timeout: base config timeout + extra time for large payloads
        // Add 1 second per 100KB of data to account for transmission time
        long timeoutMs = config.getRequestTimeoutMs()
                + (encoded.length / (100L * 1024)) * 1000L;

        // Timeout
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                messageHandler.cancelPendingRequest(messageId);
                future.completeExceptionally(new TimeoutException("Request timed out"));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        return future;
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

        // Dynamic timeout: base config timeout + extra time for large payloads
        // Add 1 second per 100KB of data to account for transmission time
        long timeoutMs = config.getRequestTimeoutMs()
                + (totalOnWire / (100L * 1024)) * 1000L;

        // Timeout
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                messageHandler.cancelPendingRequest(messageId);
                future.completeExceptionally(new TimeoutException("Request timed out"));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

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
        SocketAddress addr = new InetSocketAddress(host, port);
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
                                connAssemblers.computeIfAbsent(streamId, k -> new MessageFrameAssembler());

                        // Synchronize on the assembler: MessageFrameAssembler is NOT thread-safe,
                        // and onPacketReceived can be called from multiple threads concurrently
                        // for frames belonging to the same stream.
                        List<byte[]> completeMessages;
                        boolean cleanUp = false;
                        synchronized (assembler) {
                            // Poll all available data chunks and feed into assembler
                            byte[] chunk;
                            while ((chunk = stream.poll()) != null) {
                                if (chunk.length > 0) {
                                    assembler.addData(chunk);
                                }
                            }

                            // Fire assembly progress callback for large transfer tracking
                            if (eventListener != null && assembler.getBufferSize() > 0) {
                                eventListener.onStreamAssemblyProgress(peerId, streamId, assembler.getBufferSize());
                            }

                            // Extract and handle all complete messages
                            completeMessages = assembler.extractMessages();

                            // Clean up assembler when stream is finished and buffer is empty
                            if (sf.isFin() && !assembler.hasPendingData()) {
                                cleanUp = true;
                            }
                        }

                        // Handle messages outside the synchronized block to avoid holding
                        // the lock during potentially slow message processing
//                        if (!completeMessages.isEmpty()) {
//                            log.debug("[FudpNode] Assembled {} message(s) from stream {} (peer={}, conn={})",
//                                    completeMessages.size(), streamId, peerId, connId);
//                        }
                        for (byte[] message : completeMessages) {
                            handleIncomingData(ctx, message);
                        }

                        if (cleanUp) {
                            connAssemblers.remove(streamId);
                            // Clean up connection entry if no more assemblers
                            if (connAssemblers.isEmpty()) {
                                streamAssemblers.remove(connId);
                            }
                            // Remove the stream from StreamManager to prevent stream leak.
                            // Each stream carries exactly one message; once FIN is received
                            // and the message is fully delivered, the stream is no longer needed.
                            connection.getStreamManager().removeStream(streamId);
                        }
                    }
                } else {
                    log.warn("[FudpNode] Missing stream for peer {} streamId={} conn={}", peerId, sf.getStreamId(), connId);
                }
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
