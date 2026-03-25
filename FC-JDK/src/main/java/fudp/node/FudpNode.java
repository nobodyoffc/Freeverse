package fudp.node;

import core.crypto.Hash;
import core.crypto.KeyTools;
import fudp.Protocol;
import fudp.connection.ConnectionState;
import fudp.connection.PeerConnection;
import fudp.handler.FileHandler;
import fudp.handler.MessageHandler;
import fudp.handler.RelayHandler;
import fudp.message.*;
import fudp.metrics.MeterListener;
import fudp.metrics.MeterRecord;
import fudp.packet.Packet;
import fudp.packet.frames.StreamFrame;
import fudp.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import java.io.IOException;
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

    private final Protocol protocol;
    private final NodeConfig config;
    private final PeerBook peerBook;
    private MessageHandler messageHandler;
    private FileHandler fileHandler;
    private RelayHandler relayHandler;
    private static final long EPOCH_2024 = 1704067200L; // 2024-01-01 00:00:00 UTC in seconds

    private final int fidHash;
    private final AtomicLong messageIdGenerator;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> cleanupTask;
    private final Map<String, Long> lastPongInfoSent;
    private final List<MeterListener> meterListeners = new CopyOnWriteArrayList<>();
    
    /** Pending bytes ACKs for RTT measurement */
    private final Map<Long, Long> pendingBytesAcks = new ConcurrentHashMap<>();

    /** Pending ACK futures for blocking sendBytesWaitAck calls */
    private final Map<Long, CompletableFuture<Boolean>> pendingAckFutures = new ConcurrentHashMap<>();

    /** Per-stream message assemblers for reassembling chunked messages */
    private final Map<Long, MessageFrameAssembler> streamAssemblers = new ConcurrentHashMap<>();

    private NodeEventListener eventListener;
    private volatile boolean running = false;
    
    public FudpNode(byte[] privateKey, NodeConfig config) throws IOException {
        this.config = config;
        this.protocol = new Protocol(privateKey, config.getPort(), config.getResolvedDataDir(),
                config.getMaxPacketSize());
        // Share the NodeConfig's DDoSConfig with Protocol so runtime toggling takes effect immediately
        this.protocol.initDDoSDefense(config.getDdosConfig());
        this.protocol.addPacketListener(this);

        String dataDir = config.getResolvedDataDir();
        String localFid = protocol.getLocalFid();
        this.peerBook = new PeerBook(dataDir, config.getAddressCacheTtlMs(), localFid);

        // Create message handler
        this.messageHandler = createMessageHandler(null);
        
        // Create file handler (with bulk-send support for file transfers)
        this.fileHandler = new FileHandler(null, createFileMessageSender());
        
        // Create relay handler
        this.relayHandler = new RelayHandler(localFid, peerBook, null, this::sendMessageToPeer);
        
        byte[] fidHashBytes = Hash.sha256(localFid.getBytes());
        this.fidHash = ((fidHashBytes[30] & 0xFF) << 8) | (fidHashBytes[31] & 0xFF);
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

    /**
     * Send pre-encoded bytes on a single stream.
     * Used for file transfers to avoid per-chunk stream creation overhead.
     */
    private void sendRawOnSingleStream(String peerId, byte[] concatenatedData) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();
        protocol.sendAndClose(stream, concatenatedData);
    }

    /**
     * Create a FileHandler.MessageSender that supports both per-message and bulk sending.
     */
    private FileHandler.MessageSender createFileMessageSender() {
        return new FileHandler.MessageSender() {
            @Override
            public void send(String peerId, AppMessage message) throws IOException {
                sendMessageToPeer(peerId, message);
            }

            @Override
            public void sendRawOnSingleStream(String peerId, byte[] concatenatedEncodedMessages) throws IOException {
                FudpNode.this.sendRawOnSingleStream(peerId, concatenatedEncodedMessages);
            }
        };
    }

    // Lifecycle

    /**
     * Start the node.
     */
    public void start() {
        if (running) return;
        running = true;

        protocol.start();

        // Schedule periodic cleanup (every 5 minutes)
        // - cleanupOldMessages: removes old received message IDs and sent ACK cache
        cleanupTask = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        messageHandler.getChatHandler().cleanupOldMessages();
                    } catch (Exception e) {
                        log.warn("[FudpNode] Cleanup failed: {}", e.getMessage());
                    }
                },
                5, 5, TimeUnit.MINUTES
        );
        
        // Start relay handler cleanup
        relayHandler.startCleanup(scheduler);
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

        // Shutdown file handler
        if (fileHandler != null) {
            fileHandler.shutdown();
        }
        
        // Stop relay handler cleanup
        if (relayHandler != null) {
            relayHandler.stopCleanup();
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
     * Send a chat message to a peer.
     */
    public long sendChat(String peerId, String message) throws IOException {
        return sendChat(peerId, message, generateMessageId());
    }

    /**
     * Send a chat message to a peer with specific ID.
     */
    public long sendChat(String peerId, String message, long messageId) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        ChatMessage chat = new ChatMessage(message);
        chat.setMessageId(messageId);

        byte[] encoded = MessageCodec.encode(chat);
        protocol.sendAndClose(stream, encoded);
        return messageId;
    }

    /**
     * Send a chat message with delivery confirmation.
     */
    public long sendChatWithAck(String peerId, String message) throws IOException {
        return sendChatWithAck(peerId, message, generateMessageId());
    }

    /**
     * Send a chat message with delivery confirmation and specific ID.
     */
    public long sendChatWithAck(String peerId, String message, long messageId) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        ChatMessage chat = new ChatMessage(message);
        chat.setMessageId(messageId);
        chat.setFlag(AppMessage.FLAG_NEED_ACK);

        byte[] encoded = MessageCodec.encode(chat);
        protocol.sendAndClose(stream, encoded);
        
        // Register AFTER sending for accurate RTT measurement
        messageHandler.getChatHandler().registerPendingAck(chat);
        return messageId;
    }

    /**
     * Send a request and wait for response.
     */
    public CompletableFuture<ResponseMessage> request(String peerId, String serviceName, byte[] data) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        long messageId = nextMessageId();
        RequestMessage request = new RequestMessage(messageId, serviceName, data);

        CompletableFuture<ResponseMessage> future = new CompletableFuture<>();
        messageHandler.registerPendingRequest(messageId, future);

        log.debug("[FudpNode] Sending request to {} (messageId={}, streamId={}, service={}, dataLen={})",
                peerId, messageId, stream.getStreamId(), serviceName, data.length);

        byte[] encoded = MessageCodec.encode(request);
        protocol.sendAndClose(stream, encoded);

        emitMeter(MeterRecord.builder()
                .peerId(peerId)
                .streamId(stream.getStreamId())
                .messageType(MessageType.REQUEST)
                .direction(fudp.metrics.MeterDirection.OUTBOUND)
                .payloadBytes(encoded.length)
                .sendTimestampMillis(System.currentTimeMillis())
                .receiveTimestampMillis(0)
                .retransmitCount(0)
                .build());

        // Timeout
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                messageHandler.cancelPendingRequest(messageId);
                future.completeExceptionally(new TimeoutException("Request timed out"));
            }
        }, config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * Send a response to a request.
     */
    public void respond(String peerId, long requestId, int statusCode, byte[] data) throws IOException {
        messageHandler.sendResponse(peerId, requestId, statusCode, data);
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

        // Timeout
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                messageHandler.cancelPendingRequest(messageId);
                future.completeExceptionally(new TimeoutException("Request timed out"));
            }
        }, config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * Send a streaming response to a request.
     * The headerData is the UnifiedCodec response header (4-byte length + JSON),
     * and dataStream provides the binary payload which is streamed without full memory load.
     *
     * @param peerId          Target peer FID
     * @param requestId       The request ID to respond to
     * @param statusCode      FUDP status code (0=success)
     * @param headerData      The small header bytes (UnifiedCodec response header)
     * @param dataStream      InputStream for the binary data to stream
     * @param dataStreamLength Number of bytes to read from dataStream
     * @throws IOException if connection or sending fails
     */
    public void respondWithStream(
            String peerId, long requestId, int statusCode,
            byte[] headerData, java.io.InputStream dataStream, long dataStreamLength) throws IOException {

        PeerConnection conn = getOrConnectPeer(peerId);
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
                .peerId(peerId)
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

    // Bytes Transfer

    /**
     * Send raw bytes to a peer (fire-and-forget).
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @return the message ID
     */
    public long sendBytes(String peerId, byte[] data) throws IOException {
        return sendBytes(peerId, data, BytesMessage.DATA_TYPE_RAW);
    }

    /**
     * Send bytes to a peer with type hint (fire-and-forget).
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @param dataType the data type hint (0=raw, 1=json, 2=protobuf, etc.)
     * @return the message ID
     */
    public long sendBytes(String peerId, byte[] data, int dataType) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        long messageId = nextMessageId();
        BytesMessage msg = new BytesMessage(data, dataType);
        msg.setMessageId(messageId);

        byte[] encoded = MessageCodec.encode(msg);
        protocol.sendAndClose(stream, encoded);
        return messageId;
    }

    /**
     * Send bytes to a peer with delivery confirmation.
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @return the message ID
     */
    public long sendBytesWithAck(String peerId, byte[] data) throws IOException {
        return sendBytesWithAck(peerId, data, BytesMessage.DATA_TYPE_RAW);
    }

    /**
     * Send bytes to a peer with delivery confirmation and type hint.
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @param dataType the data type hint
     * @return the message ID
     */
    public long sendBytesWithAck(String peerId, byte[] data, int dataType) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();

        long messageId = nextMessageId();
        BytesMessage msg = new BytesMessage(data, dataType);
        msg.setMessageId(messageId);
        msg.setFlag(AppMessage.FLAG_NEED_ACK);

        byte[] encoded = MessageCodec.encode(msg);
        protocol.sendAndClose(stream, encoded);
        
        // Register for ACK tracking
        pendingBytesAcks.put(messageId, System.currentTimeMillis());
        return messageId;
    }

    /**
     * Send bytes and block until the peer ACKs or timeout expires.
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
    public boolean sendBytesWaitAck(String peerId, byte[] data, long timeoutMs) throws IOException {
        return sendBytesWaitAck(peerId, data, BytesMessage.DATA_TYPE_RAW, timeoutMs);
    }

    /**
     * Send bytes with type hint and block until the peer ACKs or timeout expires.
     * @param peerId the peer ID or alias
     * @param data the byte array to send
     * @param dataType the data type hint
     * @param timeoutMs max milliseconds to wait for ACK
     * @return true if ACK received within timeout, false otherwise
     */
    public boolean sendBytesWaitAck(String peerId, byte[] data, int dataType, long timeoutMs) throws IOException {
        PeerConnection conn = getOrConnectPeer(peerId);
        Stream stream = conn.openStream();
        long streamId = stream.getStreamId();

        long messageId = nextMessageId();
        BytesMessage msg = new BytesMessage(data, dataType);
        msg.setMessageId(messageId);
        msg.setFlag(AppMessage.FLAG_NEED_ACK);

        CompletableFuture<Boolean> ackFuture = new CompletableFuture<>();
        pendingAckFutures.put(messageId, ackFuture);
        pendingBytesAcks.put(messageId, System.currentTimeMillis());

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
            pendingBytesAcks.remove(messageId);
        }
    }

    // Relay

    /**
     * Send a message via relay node (privacy-preserving: target won't know origin).
     * @param relayPeerId the relay node peer ID or alias
     * @param targetFid the target FID (final destination)
     * @param innerMessage the message to relay
     * @return the relay message ID
     */
    public long sendViaRelay(String relayPeerId, String targetFid, AppMessage innerMessage) throws IOException {
        return sendViaRelay(relayPeerId, targetFid, innerMessage, RelayMessage.MAX_HOP_COUNT);
    }

    /**
     * Send a message via relay node with custom hop count.
     * @param relayPeerId the relay node peer ID or alias
     * @param targetFid the target FID (final destination)
     * @param innerMessage the message to relay
     * @param hopCount maximum number of hops
     * @return the relay message ID
     */
    public long sendViaRelay(String relayPeerId, String targetFid, AppMessage innerMessage, int hopCount) throws IOException {
        PeerConnection conn = getOrConnectPeer(relayPeerId);
        Stream stream = conn.openStream();

        // Encode inner message
        byte[] innerPayload = MessageCodec.encode(innerMessage);
        
        // Check payload size
        if (innerPayload.length > RelayMessage.MAX_RELAY_PAYLOAD) {
            throw new IOException("Relay payload too large: " + innerPayload.length + " > " + RelayMessage.MAX_RELAY_PAYLOAD);
        }

        long messageId = nextMessageId();
        RelayMessage relay = new RelayMessage(targetFid, hopCount, innerPayload);
        relay.setMessageId(messageId);

        byte[] encoded = MessageCodec.encode(relay);
        protocol.sendAndClose(stream, encoded);
        
        // Register for ACK tracking
        relayHandler.registerPendingRelayAck(messageId);
        return messageId;
    }

    /**
     * Get relay statistics.
     */
    public RelayHandler.RelayStats getRelayStats() {
        return relayHandler.getStats();
    }

    /**
     * Send an identified relay message (sender identity revealed).
     * Used for bidirectional protocols like file transfer.
     * @param relayPeerId the relay node peer ID or alias
     * @param targetFid the target FID (final destination)
     * @param sessionId the session ID (groups related messages)
     * @param innerMessage the message to relay
     * @return the relay message ID
     */
    public long sendViaRelayIdentified(String relayPeerId, String targetFid, long sessionId, 
            AppMessage innerMessage) throws IOException {
        PeerConnection conn = getOrConnectPeer(relayPeerId);
        Stream stream = conn.openStream();

        // Encode inner message
        byte[] innerPayload = MessageCodec.encode(innerMessage);
        
        // Check payload size
        if (innerPayload.length > RelayMessage.MAX_RELAY_PAYLOAD) {
            throw new IOException("Relay payload too large: " + innerPayload.length + " > " + RelayMessage.MAX_RELAY_PAYLOAD);
        }

        long messageId = nextMessageId();
        RelayMessage relay = RelayMessage.createIdentified(targetFid, getLocalFid(), sessionId, innerPayload);
        relay.setMessageId(messageId);

        byte[] encoded = MessageCodec.encode(relay);
        protocol.sendAndClose(stream, encoded);
        
        // Register for ACK tracking
        relayHandler.registerPendingRelayAck(messageId);
        return messageId;
    }

    /**
     * Generate a new relay session ID.
     * @return unique session ID
     */
    public long generateRelaySessionId() {
        return nextMessageId();
    }

    /**
     * Send a file offer via relay node.
     * Uses identified relay with session for bidirectional communication.
     * @param relayPeerId the relay node peer ID or alias
     * @param targetFid the target FID (final destination)
     * @param file the file to send
     * @return the relay session ID (use this for subsequent file messages)
     */
    public long sendFileOfferViaRelay(String relayPeerId, String targetFid, File file) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File not found or not a file: " + file.getAbsolutePath());
        }

        // Generate session ID for this file transfer
        long sessionId = generateRelaySessionId();
        
        // Create file offer message
        String transferId = "relay-" + sessionId;
        FileOfferMessage offer = new FileOfferMessage();
        offer.setMessageId(nextMessageId());
        offer.setTransferId(transferId);
        offer.setFileName(file.getName());
        offer.setFileSize(file.length());
        offer.setChunkSize(fileHandler.getDefaultChunkSize());
        
        // Calculate file hash
        try {
            String hash = FileHandler.calculateFileHash(file);
            offer.setFileHash(hash);
        } catch (Exception e) {
            log.warn("[FudpNode] Failed to calculate file hash: {}", e.getMessage());
            offer.setFileHash("");
        }
        
        // Send via identified relay
        sendViaRelayIdentified(relayPeerId, targetFid, sessionId, offer);
        
        // Register transfer with file handler for tracking
        fileHandler.registerRelayedFileOffer(transferId, sessionId, relayPeerId, targetFid, file);
        
        log.info("[FudpNode] Sent file offer via relay {} to {} (session={})", 
                relayPeerId, targetFid, sessionId);
        
        return sessionId;
    }

    /**
     * Accept a relayed file offer.
     * @param relayPeerId the relay node to use for response
     * @param senderFid the sender's FID
     * @param sessionId the relay session ID
     * @param transferId the transfer ID from the file offer
     * @param saveDir the directory to save the file
     */
    public void acceptRelayedFile(String relayPeerId, String senderFid, long sessionId, 
            String transferId, String saveDir) throws IOException {
        // Create accept message
        FileAcceptMessage accept = new FileAcceptMessage();
        accept.setMessageId(nextMessageId());
        accept.setTransferId(transferId);
        
        // Send via identified relay (response to sender)
        sendViaRelayIdentified(relayPeerId, senderFid, sessionId, accept);
        
        // Register with file handler for receiving chunks
        fileHandler.registerRelayedFileReceive(transferId, sessionId, relayPeerId, senderFid, saveDir);
        
        log.info("[FudpNode] Accepted relayed file {} (session={})", transferId, sessionId);
    }

    /**
     * Reject a relayed file offer.
     * @param relayPeerId the relay node to use for response
     * @param senderFid the sender's FID
     * @param sessionId the relay session ID
     * @param transferId the transfer ID from the file offer
     * @param reason rejection reason
     */
    public void rejectRelayedFile(String relayPeerId, String senderFid, long sessionId, 
            String transferId, String reason) throws IOException {
        // Create reject message
        FileRejectMessage reject = new FileRejectMessage();
        reject.setMessageId(nextMessageId());
        reject.setTransferId(transferId);
        reject.setReason(reason != null ? reason : "User rejected");
        
        // Send via identified relay (response to sender)
        sendViaRelayIdentified(relayPeerId, senderFid, sessionId, reject);
        
        log.info("[FudpNode] Rejected relayed file {} (session={})", transferId, sessionId);
    }

    // File Transfer

    /**
     * Send a file to a peer.
     * @param peerId the peer ID or alias
     * @param file the file to send
     * @return transfer ID for tracking
     */
    public String sendFile(String peerId, File file) throws IOException {
        Peer peer = peerBook.getByIdOrAlias(peerId);
        if (peer == null) {
            throw new IOException("Unknown peer: " + peerId);
        }
        return fileHandler.sendFile(peer.getId(), file);
    }

    /**
     * Send a file to a peer with custom chunk size.
     * @param peerId the peer ID or alias
     * @param file the file to send
     * @param chunkSize the chunk size in bytes
     * @return transfer ID for tracking
     */
    public String sendFile(String peerId, File file, int chunkSize) throws IOException {
        Peer peer = peerBook.getByIdOrAlias(peerId);
        if (peer == null) {
            throw new IOException("Unknown peer: " + peerId);
        }
        return fileHandler.sendFile(peer.getId(), file, chunkSize);
    }

    /**
     * Accept a pending file offer.
     * @param transferId the transfer ID from the file offer
     * @param saveDir the directory to save the file
     */
    public void acceptFile(String transferId, String saveDir) throws IOException {
        fileHandler.acceptFile(transferId, saveDir);
    }

    /**
     * Reject a pending file offer.
     * @param transferId the transfer ID from the file offer
     * @param reason optional rejection reason
     */
    public void rejectFile(String transferId, String reason) throws IOException {
        fileHandler.rejectFile(transferId, reason);
    }

    /**
     * Cancel an ongoing file transfer.
     * @param transferId the transfer ID
     * @param reason optional cancellation reason
     */
    public void cancelTransfer(String transferId, String reason) throws IOException {
        fileHandler.cancelTransfer(transferId, reason);
    }

    /**
     * Get pending file offers.
     */
    public java.util.Map<String, FileHandler.PendingOffer> getPendingFileOffers() {
        return fileHandler.getPendingOffers();
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
        Peer peer = new Peer(peerId, publicKey, host, port);
        peer.setAlias(alias);
        peerBook.add(peer);
    }

    /**
     * Add a currently connected peer to the peer book.
     */
    public boolean addConnectedPeer(String peerId, String alias) {
        PeerConnection conn = protocol.getConnectionManager().getByPeerId(peerId);
        if (conn != null && conn.getState() == ConnectionState.ESTABLISHED) {
            SocketAddress addr = conn.getPeerAddress();
            if (addr instanceof InetSocketAddress inet) {
                addPeer(peerId, conn.getPeerPublicKey(), inet.getHostString(), inet.getPort(), alias);
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
        // Also close connection if exists
        PeerConnection conn = protocol.getConnectionManager().getByPeerId(peerId);
        if (conn != null) {
            try {
                protocol.close(peerId, 0, "Peer removed");
            } catch (IOException e) {
                // Ignore
            }
        }
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
             PeerConnection conn = protocol.getConnectionManager().getByPeerId(peerId);
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
        // Recreate message handler with the new listener and balance management components
        this.messageHandler = createMessageHandler(listener);
        // Recreate file handler with the new listener (with bulk-send support)
        this.fileHandler = new FileHandler(listener, createFileMessageSender());
        // Recreate relay handler with the new listener
        this.relayHandler = new RelayHandler(getLocalFid(), peerBook, listener, this::sendMessageToPeer);
    }

    // Protocol.PacketListener implementation

    @Override
    public void onPacketReceived(PeerConnection connection, Packet packet) {
        String peerId = connection.getPeerId();

        // Update peer book with current address
        peerBook.updateFromConnection(peerId, connection.getPeerPublicKey(), connection.getPeerAddress());

        // Process stream data
        for (var frame : packet.getFrames()) {
            if (frame instanceof StreamFrame sf) {
                Stream stream = connection.getStream(sf.getStreamId());
                if (stream != null) {
                    // Only poll if stream is still receiving data (not closed)
                    if (stream.getRecvState() != fudp.stream.StreamState.CLOSED) {
                        // Get or create the assembler for this stream
                        long streamId = sf.getStreamId();
                        MessageFrameAssembler assembler = streamAssemblers
                                .computeIfAbsent(streamId, k -> new MessageFrameAssembler());

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

                            // Extract and handle all complete messages
                            completeMessages = assembler.extractMessages();

                            // Clean up assembler when stream is finished and buffer is empty
                            if (sf.isFin() && !assembler.hasPendingData()) {
                                cleanUp = true;
                            }
                        }

                        // Handle messages outside the synchronized block to avoid holding
                        // the lock during potentially slow message processing
                        if (!completeMessages.isEmpty()) {
                            log.debug("[FudpNode] Assembled {} message(s) from stream {} (peer={})",
                                    completeMessages.size(), streamId, peerId);
                        }
                        for (byte[] message : completeMessages) {
                            handleIncomingData(peerId, message);
                        }

                        if (cleanUp) {
                            streamAssemblers.remove(streamId);
                        }
                    }
                } else {
                    log.warn("[FudpNode] Missing stream for peer {} streamId={}", peerId, sf.getStreamId());
                }
            }
        }
    }

    /**
     * Handle incoming data from a peer.
     */
    private void handleIncomingData(String peerId, byte[] data) {
        try {
            MessageType type = null;
            long msgId = 0;
            try {
                type = MessageCodec.peekType(data);
                msgId = MessageCodec.peekMessageId(data);
            } catch (Exception ignore) {
                // Best-effort peek only
            }
            
            // Early atomic deduplication for CHAT messages - prevents race conditions
            // when multiple threads process the same message concurrently
            // Uses composite key (peerId + messageId) to avoid conflicts between different peers
            if (type == MessageType.CHAT) {
                // tryMarkAsProcessed atomically checks and marks - returns false if already processed
                if (!messageHandler.getChatHandler().tryMarkAsProcessed(peerId, msgId)) {
                    // Already processed by another thread
                    // Only send ACK if we haven't sent one for this message yet
                    try {
                        AppMessage message = MessageCodec.decode(data);
                        if (message.hasFlag(AppMessage.FLAG_NEED_ACK)) {
                            // Use tryMarkAckSent to avoid sending duplicate ACKs
                            sendChatAckOnce(peerId, msgId);
                        }
                    } catch (Exception ignore) {}
                    return;
                }
            } else if (type == MessageType.CHAT_ACK) {
                // Deduplication for CHAT_ACK - only log and process once
                // The actual dedup is done in ChatHandler.handleChatAck via pendingAcks.remove()
                // Here we use a similar mechanism to avoid duplicate logs
                if (!messageHandler.getChatHandler().tryMarkAckReceived(peerId, msgId)) {
                    return;
                }
            }
            
            AppMessage message = MessageCodec.decode(data);

            // Special handling for messages that need immediate response
            if (message.getType() == MessageType.PING) {
                handlePing(peerId, (PingMessage) message);
                return;
            }

            // Handle chat ACK - use sendChatAckOnce to avoid duplicate ACKs
            if (message.getType() == MessageType.CHAT && message.hasFlag(AppMessage.FLAG_NEED_ACK)) {
                sendChatAckOnce(peerId, message.getMessageId());
            }

            // Handle file transfer messages
            switch (message.getType()) {
                case FILE_OFFER -> {
                    fileHandler.handleFileOffer(peerId, (FileOfferMessage) message);
                    return;
                }
                case FILE_ACCEPT -> {
                    fileHandler.handleFileAccept(peerId, (FileAcceptMessage) message);
                    return;
                }
                case FILE_REJECT -> {
                    fileHandler.handleFileReject(peerId, (FileRejectMessage) message);
                    return;
                }
                case FILE_CHUNK -> {
                    fileHandler.handleFileChunk(peerId, (FileChunkMessage) message);
                    return;
                }
                case FILE_COMPLETE -> {
                    fileHandler.handleFileComplete(peerId, (FileCompleteMessage) message);
                    return;
                }
                case FILE_CANCEL -> {
                    fileHandler.handleFileCancel(peerId, (FileCancelMessage) message);
                    return;
                }
                default -> {}
            }

            // Handle bytes messages
            switch (message.getType()) {
                case BYTES -> {
                    handleBytesMessage(peerId, (BytesMessage) message);
                    return;
                }
                case BYTES_ACK -> {
                    handleBytesAck(peerId, (BytesAckMessage) message);
                    return;
                }
                default -> {}
            }

            // Handle relay messages
            switch (message.getType()) {
                case RELAY -> {
                    relayHandler.handleRelayMessage(peerId, (RelayMessage) message);
                    return;
                }
                case RELAY_ACK -> {
                    relayHandler.handleRelayAck(peerId, (RelayAckMessage) message);
                    return;
                }
                case RELAY_FAIL -> {
                    relayHandler.handleRelayFail(peerId, (RelayFailMessage) message);
                    return;
                }
                default -> {}
            }

            // Route to message handler (REQUEST, RESPONSE, PONG routed here)
            if (type == MessageType.RESPONSE) {
                log.debug("[FudpNode] Routing RESPONSE from {} to messageHandler (messageId={})", peerId, msgId);
            }
            messageHandler.handleIncomingData(peerId, data);

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
    private void handlePing(String peerId, PingMessage ping) {
        long messageId = ping.getMessageId();
//        log.debug("[FudpNode] Received ping from peer {} (messageId={}, wantInfo={})", peerId, messageId, ping.isWantInfo());
        try {
            PeerConnection conn = protocol.getConnectionManager().getByPeerId(peerId);
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
//            log.debug("[FudpNode] Pong sent to peer {} (messageId={})", peerId, messageId);
        } catch (IOException e) {
            log.warn("[FudpNode] Failed to send pong to peer {} (messageId={}): {}", peerId, messageId, e.getMessage());
        } catch (Throwable t) {
            log.warn("[FudpNode] Failed to respond pong for peer {} (messageId={}): {}", peerId, messageId, t.getMessage());
        }
    }

    /**
     * Send chat acknowledgment only if not already sent for this message.
     * This prevents sending multiple ACKs for the same message due to UDP retransmissions.
     * Uses composite key (peerId + messageId) to avoid conflicts between different peers.
     * 
     * @param peerId the peer ID
     * @param messageId the message ID to acknowledge
     */
    private void sendChatAckOnce(String peerId, long messageId) {
        // Check if ACK already sent for this (peerId, messageId) pair
        if (!messageHandler.getChatHandler().tryMarkAckSent(peerId, messageId)) {
            return;
        }
        sendChatAck(peerId, messageId);
    }

    /**
     * Send chat acknowledgment (internal, always sends).
     */
    private void sendChatAck(String peerId, long messageId) {
        try {
            PeerConnection conn = protocol.getConnectionManager().getByPeerId(peerId);
            if (conn == null) return;

            Stream stream = conn.openStream();
            ChatAckMessage ack = new ChatAckMessage(messageId);
            ack.setMessageId(nextMessageId());

            byte[] encoded = MessageCodec.encode(ack);
            protocol.sendAndClose(stream, encoded);
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Handle incoming bytes message.
     */
    private void handleBytesMessage(String peerId, BytesMessage message) {
        // Send ACK if requested
        if (message.hasFlag(AppMessage.FLAG_NEED_ACK)) {
            sendBytesAck(peerId, message.getMessageId());
        }
        
        // Notify listener
        if (eventListener != null) {
            eventListener.onBytesReceived(peerId, message.getMessageId(), 
                    message.getDataType(), message.getData());
        }
    }

    /**
     * Handle bytes acknowledgment.
     */
    private void handleBytesAck(String peerId, BytesAckMessage ack) {
        long ackedId = ack.getAckedMessageId();
        Long sendTime = pendingBytesAcks.remove(ackedId);
        
        if (sendTime != null) {
            long rttMs = System.currentTimeMillis() - sendTime;
            if (eventListener != null) {
                eventListener.onBytesAck(peerId, ackedId, rttMs);
            }
            log.debug("[FudpNode] Bytes ACK received for {}, RTT={}ms", ackedId, rttMs);
        }

        CompletableFuture<Boolean> future = pendingAckFutures.remove(ackedId);
        if (future != null) {
            future.complete(true);
        }
    }

    /**
     * Send bytes acknowledgment.
     */
    private void sendBytesAck(String peerId, long messageId) {
        try {
            PeerConnection conn = protocol.getConnectionManager().getByPeerId(peerId);
            if (conn == null) return;

            Stream stream = conn.openStream();
            BytesAckMessage ack = new BytesAckMessage(messageId);
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

        SocketAddress address = new InetSocketAddress(peer.getHost(), peer.getPort());
        PeerConnection conn = protocol.getConnectionManager().getByPeerId(peer.getId());
        if (conn != null) {
            ConnectionState state = conn.getState();
            if (state == ConnectionState.CLOSED || state == ConnectionState.CLOSING) {
                protocol.getConnectionManager().removeConnection(peer.getId());
                conn = null;
            }
        }

        byte[] publicKey = null;
        if (conn != null && conn.getPeerPublicKey() != null) {
            publicKey = conn.getPeerPublicKey();
        } else if (peer.getPublicKey() != null) {
            publicKey = peer.getPublicKey();
        }

        if (publicKey == null) {
            long timeoutMs = Math.max(1000L, config.getConnectionTimeoutMs());
            try {
                publicKey = discoverPublicKey(peer.getHost(), peer.getPort(), timeoutMs)
                        .get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for peer public key: " + peerId, e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IOException("Failed to discover peer public key: " + peerId, e);
            }
            peerBook.updateFromConnection(peer.getId(), publicKey, address);
        }

        if (conn == null) {
            protocol.connect(publicKey, address);
            // Derive peer ID from the public key we used to connect (must match what protocol.connect() used)
            String actualPeerId = KeyTools.pubkeyToFchAddr(publicKey);
            conn = protocol.getConnectionManager().getByPeerId(actualPeerId);
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
        return ((long) fidHash << 48) | ((epochSeconds & 0xFFFFFFFFL) << 16) | seq;
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

        PeerConnection conn = protocol.getConnectionManager().getByPeerId(peer.getId());
        if (conn == null) return null;

        return NodeStats.PeerStats.from(conn);
    }
}
