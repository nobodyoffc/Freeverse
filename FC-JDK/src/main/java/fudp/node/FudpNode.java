package fudp.node;

import fudp.Protocol;
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
    private final AtomicLong messageIdGenerator;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> cleanupTask;
    private final Map<String, Long> lastPongInfoSent;
    private final List<MeterListener> meterListeners = new CopyOnWriteArrayList<>();

    private NodeEventListener eventListener;
    private volatile boolean running = false;
    
    public FudpNode(byte[] privateKey, NodeConfig config) throws IOException {
        this.config = config;
        this.protocol = new Protocol(privateKey, config.getPort(), config.getResolvedDataDir());
        this.protocol.addPacketListener(this);

        String dataDir = config.getResolvedDataDir();
        String localFid = protocol.getLocalFid();
        this.peerBook = new PeerBook(dataDir, config.getAddressCacheTtlMs(), localFid);

        // Create message handler
        this.messageHandler = createMessageHandler(null);
        
        this.messageIdGenerator = new AtomicLong(System.currentTimeMillis());
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
                log.debug("Meter listener threw exception: {}", e.getMessage());
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

        // Schedule periodic cleanup (every 5 minutes)
        // - cleanupOldMessages: removes old received message IDs and sent ACK cache
        cleanupTask = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        messageHandler.getChatHandler().cleanupOldMessages();
                        log.debug("[FudpNode] Cleanup completed: receivedMsgIds={}, sentAckCache={}, pendingAcks={}",
                                messageHandler.getChatHandler().getReceivedMessageIdCount(),
                                messageHandler.getChatHandler().getSentAckCacheSize(),
                                messageHandler.getChatHandler().getPendingAckCount());
                    } catch (Exception e) {
                        log.warn("[FudpNode] Cleanup failed: {}", e.getMessage());
                    }
                },
                5, 5, TimeUnit.MINUTES
        );
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
     * Generate next message ID.
     */
    public long generateMessageId() {
        return messageIdGenerator.incrementAndGet();
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

        byte[] encoded = MessageCodec.encode(request);
        protocol.send(stream, encoded);

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
        ping.setMessageId(nextMessageId());
        ping.setWantInfo(wantInfo);

        byte[] encoded = MessageCodec.encode(ping);
        protocol.sendAndClose(stream, encoded);
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

        CompletableFuture<PongMessage> future = messageHandler.awaitPong(msgId);
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                messageHandler.cancelPong(msgId);
                future.completeExceptionally(new TimeoutException("Ping timeout"));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        byte[] encoded = MessageCodec.encode(ping);
        protocol.sendAndClose(stream, encoded);
        return future;
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
                        // Poll all available data chunks from the queue
                        byte[] data;
                        while ((data = stream.poll()) != null) {
                            if (data.length > 0) {
                                log.trace("[FudpNode] Stream data from {} streamId={} len={}", 
                                        peerId, sf.getStreamId(), data.length);
                                handleIncomingData(peerId, data);
                            }
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
        long startTime = System.currentTimeMillis();
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
                    log.debug("[FudpNode] Duplicate CHAT message from {} messageId={}", peerId, msgId);
                    try {
                        AppMessage message = MessageCodec.decode(data);
                        if (message.hasFlag(AppMessage.FLAG_NEED_ACK)) {
                            // Use tryMarkAckSent to avoid sending duplicate ACKs
                            sendChatAckOnce(peerId, msgId);
                        }
                    } catch (Exception ignore) {}
                    return;
                }
                // First time seeing this message
                log.info("[FudpNode] New CHAT message from {} messageId={} len={}", peerId, msgId, data.length);
            } else if (type == MessageType.CHAT_ACK) {
                // Deduplication for CHAT_ACK - only log and process once
                // The actual dedup is done in ChatHandler.handleChatAck via pendingAcks.remove()
                // Here we use a similar mechanism to avoid duplicate logs
                if (!messageHandler.getChatHandler().tryMarkAckReceived(peerId, msgId)) {
                    log.trace("[FudpNode] Duplicate CHAT_ACK from {} messageId={}", peerId, msgId);
                    return;
                }
                log.debug("[FudpNode] Received CHAT_ACK from {} messageId={} len={}", peerId, msgId, data.length);
            } else if (type != null) {
                log.debug("[FudpNode] Received {} from {} messageId={} len={}", type, peerId, msgId, data.length);
            }
            
            AppMessage message = MessageCodec.decode(data);

            // Special handling for messages that need immediate response
            if (message.getType() == MessageType.PING) {
                handlePing(peerId, (PingMessage) message);
                return;
            }

            // Handle chat ACK - use sendChatAckOnce to avoid duplicate ACKs
            if (message.getType() == MessageType.CHAT && message.hasFlag(AppMessage.FLAG_NEED_ACK)) {
                long ackStart = System.currentTimeMillis();
                sendChatAckOnce(peerId, message.getMessageId());
                log.debug("[TIMING] sendChatAckOnce took {}ms", System.currentTimeMillis() - ackStart);
            }

            // Route to message handler
            long handlerStart = System.currentTimeMillis();
            messageHandler.handleIncomingData(peerId, data);
            log.debug("[TIMING] messageHandler took {}ms, total handleIncomingData {}ms", 
                    System.currentTimeMillis() - handlerStart, System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            if (eventListener != null) {
                eventListener.onError(peerId, 1, "Error processing message: " + e.getMessage());
            }
        }
    }

    /**
     * Handle ping message by sending pong.
     */
    private void handlePing(String peerId, PingMessage ping) {
        try {
            PeerConnection conn = protocol.getConnectionManager().getByPeerId(peerId);
            if (conn == null) return;

            Stream stream = conn.openStream();
            PongMessage pong = new PongMessage(ping.getTimestamp());
            pong.setMessageId(ping.getMessageId());
            if (ping.isWantInfo()) {
                byte[] pongData = buildPongData(peerId, true);
                if (pongData.length > 0) {
                    pong.setData(pongData);
                }
                log.debug("handlePing: built pong data {} bytes for {}", pongData.length, peerId);
            } else {
                byte[] pongData = buildPongData(peerId, false);
                if (pongData.length > 0) {
                    pong.setData(pongData);
                }
            }

            byte[] encoded = MessageCodec.encode(pong);
            protocol.sendAndClose(stream, encoded);
        } catch (IOException e) {
            // Ignore
        } catch (Throwable t) {
            log.warn("handlePing: failed to respond pong for peer {}: {}", peerId, t.getMessage());
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
            log.debug("[FudpNode] ACK already sent for {} messageId={}, skipping", peerId, messageId);
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
            log.debug("[FudpNode] Sent CHAT_ACK for messageId={} to {}", messageId, peerId);
        } catch (IOException e) {
            log.debug("[FudpNode] Failed to send CHAT_ACK for messageId={}: {}", messageId, e.getMessage());
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
            conn = protocol.getConnectionManager().getByPeerId(peer.getId());
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
            log.debug("buildPongData: no provider configured, send empty pong for {}", peerId);
            return new byte[0];
        }

        long now = System.currentTimeMillis();
        long minInterval = config.getPongInfoMinIntervalMs();
        Long last = lastPongInfoSent.get(peerId);
        if (!forceInfo && last != null && now - last < minInterval) {
            log.debug("buildPongData: throttled for {} ({} ms < {} ms)", peerId, now - last, minInterval);
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
            log.debug("buildPongData: truncated pong data for {} to {} bytes (max {})", peerId, raw.length, max);
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
     * Generate next message ID.
     */
    private long nextMessageId() {
        return messageIdGenerator.incrementAndGet();
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
