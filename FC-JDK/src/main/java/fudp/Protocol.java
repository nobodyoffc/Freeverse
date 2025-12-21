package fudp;

import core.crypto.KeyTools;
import fudp.connection.*;
import fudp.crypto.*;
import fudp.packet.*;
import fudp.packet.frames.*;
import fudp.security.ReplayProtection;
import fudp.stream.Stream;
import fudp.transport.SentPacket;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.*;
import java.util.concurrent.*;

/**
 * Main entry point for FUDP protocol.
 * 
 * Simplified version using only AsyTwoWay (ECDH) encryption.
 * No symmetric key negotiation required.
 */
public class Protocol {

    private final CryptoManager cryptoManager;
    private final ConnectionManager connectionManager;
    private final PacketCrypto packetCrypto;
    private final ReplayProtection replayProtection;

    private final DatagramChannel channel;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> ackTask;
    private ScheduledFuture<?> retransmitTask;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<CompletableFuture<byte[]>>> pendingPublicKeyRequests;

    private volatile boolean running = false;

    // Protocol settings
    private static final int MAX_PACKET_SIZE = 1350;
    private static final int HEADER_OVERHEAD = PacketHeader.HEADER_SIZE + 52; // Header + crypto bundle overhead
    private static final int MAX_PAYLOAD_SIZE = MAX_PACKET_SIZE - HEADER_OVERHEAD;

    // Plaintext control message types
    private static final byte CONTROL_HELLO = 0x01;
    private static final byte CONTROL_PUBLIC_KEY = 0x02;
    private static final int CONTROL_PAYLOAD_MAX = 256;

    // Public key response rate limit (per remote socket address)
    private static final long PUBKEY_WINDOW_MS = 2000;
    private static final int PUBKEY_MAX_PER_WINDOW = 3;
    private final ConcurrentHashMap<String, PublicKeyResponseBucket> publicKeyResponseBuckets = new ConcurrentHashMap<>();

    // Event listeners
    private final List<PacketListener> packetListeners;

    public Protocol(byte[] privateKey, int port, String dataDir) throws IOException {
        this.cryptoManager = new CryptoManager(privateKey);
        this.connectionManager = new ConnectionManager();
        this.packetCrypto = new PacketCrypto(cryptoManager);
        this.replayProtection = new ReplayProtection();

        this.channel = DatagramChannel.open();
        this.channel.bind(new InetSocketAddress(port));
        this.channel.configureBlocking(false);

        this.executor = Executors.newFixedThreadPool(4);
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.pendingPublicKeyRequests = new ConcurrentHashMap<>();

        this.packetListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Start the protocol.
     */
    public void start() {
        if (running) return;
        running = true;

        // Start receive loop
        executor.submit(this::receiveLoop);

        // Start ACK timer (reduced from 25ms to 5ms for lower latency)
        ackTask = scheduler.scheduleAtFixedRate(this::ackTimerTask, 5, 5, TimeUnit.MILLISECONDS);

        // Start retransmission check (reduced from 100ms to 50ms for faster recovery)
        retransmitTask = scheduler.scheduleAtFixedRate(this::retransmitTask, 50, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop the protocol.
     */
    public void stop() {
        running = false;
        if (ackTask != null) {
            ackTask.cancel(true);
        }
        if (retransmitTask != null) {
            retransmitTask.cancel(true);
        }

        executor.shutdown();
        scheduler.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        connectionManager.closeAll();

        try {
            channel.close();
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Connect to a peer and open a stream.
     */
    public Stream connect(byte[] peerPublicKey, SocketAddress address) throws IOException {
        String peerId = KeyTools.pubkeyToFchAddr(peerPublicKey);
        PeerConnection conn = connectionManager.getOrCreate(peerId, address);
        conn.setPeerPublicKey(peerPublicKey);

        // Open a stream
        return conn.openStream();
    }

    /**
     * Send data on a stream.
     */
    public void send(Stream stream, byte[] data) throws IOException {
        PeerConnection conn = getConnectionForStream(stream);
        if (conn == null) {
            throw new IOException("No connection for stream");
        }

        // Check flow control
        if (!stream.canSend(data.length)) {
            throw new IOException("Flow control limit reached");
        }

        // Create STREAM frame
        long offset = stream.consumeSendOffset(data.length);
        StreamFrame frame = new StreamFrame(stream.getStreamId(), offset, data, false);

        // Build and send packet
        sendFrame(conn, frame);
    }

    /**
     * Send data and close the stream.
     */
    public void sendAndClose(Stream stream, byte[] data) throws IOException {
        PeerConnection conn = getConnectionForStream(stream);
        if (conn == null) {
            throw new IOException("No connection for stream");
        }

        // Create STREAM frame with FIN
        long offset = stream.consumeSendOffset(data.length);
        StreamFrame frame = new StreamFrame(stream.getStreamId(), offset, data, true);

        // Build and send packet
        sendFrame(conn, frame);
        stream.closeSend();
    }

    /**
     * Close a connection.
     */
    public void close(String peerId, int errorCode, String reason) throws IOException {
        PeerConnection conn = connectionManager.getByPeerId(peerId);
        if (conn == null) return;

        ConnectionCloseFrame frame = new ConnectionCloseFrame(errorCode, reason);
        sendFrame(conn, frame);
        conn.setState(ConnectionState.CLOSING);
    }

    /**
     * Send a single frame.
     */
    private void sendFrame(PeerConnection conn, Frame frame) throws IOException {
        List<Frame> frames = new ArrayList<>();
        frames.add(frame);

        // Check if ACK needed
        if (conn.getAckManager().hasPendingAcks()) {
            AckFrame ackFrame = conn.getAckManager().generateAckFrame();
            if (ackFrame != null) {
                frames.add(0, ackFrame);
            }
        }

        sendPacket(conn, frames);
    }

    /**
     * Send a packet with frames.
     */
    private void sendPacket(PeerConnection conn, List<Frame> frames) throws IOException {
        // Check if channel is still open
        if (!running || !channel.isOpen()) {
            return;
        }

        // Allocate packet number
        long packetNumber = conn.allocatePacketNumber();

        // Create packet
        Packet packet = new Packet(conn.getConnectionId(), packetNumber);
        for (Frame frame : frames) {
            packet.addFrame(frame);
        }

        // Encrypt using AsyTwoWay
        packetCrypto.encryptPacket(packet, conn.getPeerId(), conn.getPeerPublicKey());

        // Send
        byte[] data = packet.toBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        try {
            channel.send(buffer, conn.getPeerAddress());
        } catch (java.nio.channels.ClosedChannelException e) {
            // Channel closed during shutdown, ignore
            return;
        }

        // Record for ACK tracking
        boolean ackEliciting = packet.isAckEliciting();
        conn.recordSentPacket(packetNumber, frames, data.length, ackEliciting);
        conn.getCongestionControl().onSend(data.length);
    }

    /**
     * Receive loop.
     */
    private void receiveLoop() {
        ByteBuffer buffer = ByteBuffer.allocate(65536);

        while (running) {
            try {
                buffer.clear();
                SocketAddress sender = channel.receive(buffer);

                if (sender != null) {
                    buffer.flip();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);

                    // Process in thread pool
                    executor.submit(() -> handleIncomingPacket(data, sender));
                } else {
                    // No data, sleep briefly
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Handle an incoming packet.
     */
    private void handleIncomingPacket(byte[] data, SocketAddress from) {
        try {
            // Parse packet header
            Packet packet = Packet.fromBytes(data);

            // Plaintext control handling (HELLO / PUBLIC_KEY)
            if (packet.getHeader().isControlPacket()) {
                handlePlaintextControl(packet, from);
                return;
            }

            // Check for plaintext error
            if (packet.getHeader().isErrorPacket()) {
                // Error packets are ignored in simplified protocol
                return;
            }

            // Decrypt and identify sender
            String senderId;
            try {
                senderId = packetCrypto.decryptPacket(packet);
            } catch (Exception e) {
                // Cannot decrypt, offer our public key to allow retry
                sendPublicKeyResponse(from);
                return;
            }

            // Get or create connection
            PeerConnection conn = connectionManager.getOrCreate(senderId, from);
            if (packet.getPeerPublicKey() != null) {
                conn.setPeerPublicKey(packet.getPeerPublicKey());
            }

            // Replay protection with session epoch for peer restart detection
            ReplayProtection.CheckResult result = replayProtection.checkAndRecord(
                    senderId, packet.getPacketNumber(), packet.getTimestamp(), packet.getSessionEpoch());

            if (result == ReplayProtection.CheckResult.INVALID_TIMESTAMP) {
                System.err.println("[Protocol] Invalid timestamp from " + senderId);
                close(senderId, ConnectionCloseFrame.INTERNAL_ERROR, "Invalid timestamp");
                return;
            }

            // Handle peer restart: reset connection state when peer's session epoch changes
            // Use tryMarkPeerRestartHandled to avoid duplicate processing in multi-threaded scenarios
            if (result == ReplayProtection.CheckResult.PEER_RESTART) {
                if (conn.tryMarkPeerRestartHandled()) {
                    // First thread to detect restart - do the actual reset
                    System.err.println("[Protocol] Peer restart detected for " + senderId + 
                            " (session epoch changed), resetting connection state");
                    conn.resetForPeerRestart();
                }
                // All threads continue processing this packet as valid
            }

            boolean ackEliciting = packet.isAckEliciting();
            if (result == ReplayProtection.CheckResult.DUPLICATE) {
                // Record for ACK and send immediately to stop retransmissions
                if (ackEliciting) {
                    conn.getAckManager().onPacketReceived(packet.getPacketNumber());
                    sendAck(conn);
                }
                return;
            }

            conn.onPacketReceived(data.length);

            // Record for ACK only if the packet is ACK-eliciting
            if (ackEliciting) {
                conn.getAckManager().onPacketReceived(packet.getPacketNumber());
            }

            // Process frames
            for (Frame frame : packet.getFrames()) {
                handleFrame(conn, frame);
            }

            // Send immediate ACK if needed
            if (ackEliciting && conn.getAckManager().shouldSendAckImmediately()) {
                sendAck(conn);
            }

            // Notify listeners
            for (PacketListener listener : packetListeners) {
                listener.onPacketReceived(conn, packet);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handle plaintext control packets (HELLO / PUBLIC_KEY).
     */
    private void handlePlaintextControl(Packet packet, SocketAddress from) {
        byte[] payload = packet.getEncryptedPayload();
        if (payload == null || payload.length == 0 || payload.length > CONTROL_PAYLOAD_MAX) {
            return; // Drop malformed/oversized control payloads
        }

        byte controlType = payload[0];
        switch (controlType) {
            case CONTROL_HELLO -> sendPublicKeyResponse(from);
            case CONTROL_PUBLIC_KEY -> {
                if (payload.length < 2) {
                    return;
                }
                byte[] pubkey = Arrays.copyOfRange(payload, 1, payload.length);
                String key = from.toString();
                CopyOnWriteArrayList<CompletableFuture<byte[]>> waiters = pendingPublicKeyRequests.remove(key);
                if (waiters != null) {
                    for (CompletableFuture<byte[]> waiter : waiters) {
                        waiter.complete(pubkey);
                    }
                }
            }
            default -> {
                // Unknown control type, ignore
            }
        }
    }

    /**
     * Send our public key as plaintext CONTROL packet with basic rate limiting.
     */
    private void sendPublicKeyResponse(SocketAddress to) {
        if (!allowPublicKeyResponse(to)) {
            return;
        }

        try {
            Packet packet = new Packet(0, 0);
            packet.getHeader().setPacketType(PacketHeader.PACKET_TYPE_CONTROL);

            byte[] pubkey = cryptoManager.getLocalPublicKey();
            byte[] payload = new byte[1 + pubkey.length];
            payload[0] = CONTROL_PUBLIC_KEY;
            System.arraycopy(pubkey, 0, payload, 1, pubkey.length);

            packet.setEncryptedPayload(payload); // plaintext payload in control packet

            byte[] data = packet.toBytes();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            channel.send(buffer, to);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sliding window limiter to prevent PUBLIC_KEY spam per remote address.
     */
    private boolean allowPublicKeyResponse(SocketAddress to) {
        String key = to.toString();
        long now = System.currentTimeMillis();
        PublicKeyResponseBucket bucket = publicKeyResponseBuckets.computeIfAbsent(
                key, k -> new PublicKeyResponseBucket(now, 0));

        synchronized (bucket) {
            if (now - bucket.windowStart > PUBKEY_WINDOW_MS) {
                bucket.windowStart = now;
                bucket.count = 0;
            }

            if (bucket.count >= PUBKEY_MAX_PER_WINDOW) {
                return false;
            }

            bucket.count++;
            return true;
        }
    }

    private static class PublicKeyResponseBucket {
        long windowStart;
        int count;

        PublicKeyResponseBucket(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }

    /**
     * Handle a single frame.
     */
    private void handleFrame(PeerConnection conn, Frame frame) throws IOException {
        switch (frame.getType()) {
            case STREAM -> {
                StreamFrame streamFrame = (StreamFrame) frame;
                Stream stream = conn.getStreamManager().getOrCreateStream(streamFrame.getStreamId());
                stream.onDataReceived(streamFrame.getOffset(), streamFrame.getData(), streamFrame.isFin());
            }

            case ACK -> {
                AckFrame ackFrame = (AckFrame) frame;
                List<Long> ackedPackets = ackFrame.getAcknowledgedPackets();
                conn.onAckReceived(ackFrame.getLargestAcknowledged(), ackFrame.getAckDelay(), ackedPackets);
                if (conn.getState() == ConnectionState.ESTABLISHING) {
                    conn.setState(ConnectionState.ESTABLISHED);
                }
            }

            case CONNECTION_CLOSE -> {
                ConnectionCloseFrame closeFrame = (ConnectionCloseFrame) frame;
                conn.setState(ConnectionState.CLOSED);
                connectionManager.removeConnection(conn.getPeerId());
            }

            case MAX_DATA -> {
                MaxDataFrame maxData = (MaxDataFrame) frame;
                conn.getStreamManager().updateMaxData(maxData.getMaxData());
            }

            case MAX_STREAM_DATA -> {
                MaxStreamDataFrame maxStreamData = (MaxStreamDataFrame) frame;
                Stream stream = conn.getStream(maxStreamData.getStreamId());
                if (stream != null) {
                    stream.updateMaxSendData(maxStreamData.getMaxStreamData());
                }
            }

            case MAX_STREAMS -> {
                MaxStreamsFrame maxStreams = (MaxStreamsFrame) frame;
                conn.getStreamManager().setMaxLocalStreams(maxStreams.getMaxStreams());
            }

            default -> {
                // Ignore unknown frames
            }
        }
    }

    /**
     * Send ACK for a connection.
     */
    private void sendAck(PeerConnection conn) throws IOException {
        AckFrame ackFrame = conn.getAckManager().generateAckFrame();
        if (ackFrame != null) {
            sendFrame(conn, ackFrame);
        }
    }

    /**
     * ACK timer task.
     */
    private void ackTimerTask() {
        for (PeerConnection conn : connectionManager.getAllConnections()) {
            if (conn.getAckManager().hasPendingAcks()) {
                try {
                    sendAck(conn);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Retransmission task.
     */
    private void retransmitTask() {
        for (PeerConnection conn : connectionManager.getAllConnections()) {
            List<SentPacket> lost = conn.detectLostPackets();

            for (SentPacket packet : lost) {
                if (packet.retransmitCount >= 10) {
                    // Too many retransmits, close connection
                    try {
                        close(conn.getPeerId(), ConnectionCloseFrame.INTERNAL_ERROR, "Too many retransmits");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    continue;
                }

                // Retransmit frames
                List<Frame> framesToRetransmit = new ArrayList<>();
                for (Frame frame : packet.frames) {
                    if (frame.shouldRetransmit()) {
                        framesToRetransmit.add(frame);
                    }
                }

                if (!framesToRetransmit.isEmpty()) {
                    try {
                        sendPacket(conn, framesToRetransmit);
                        conn.getCongestionControl().onLoss();
                        conn.recordRetransmit();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Get connection for a stream.
     */
    private PeerConnection getConnectionForStream(Stream stream) {
        for (PeerConnection conn : connectionManager.getAllConnections()) {
            if (conn.getStream(stream.getStreamId()) == stream) {
                return conn;
            }
        }
        return null;
    }

    /**
     * Add packet listener.
     */
    public void addPacketListener(PacketListener listener) {
        packetListeners.add(listener);
    }

    /**
     * Remove packet listener.
     */
    public void removePacketListener(PacketListener listener) {
        packetListeners.remove(listener);
    }

    // Getters
    public String getLocalFid() {
        return cryptoManager.getLocalFid();
    }

    public byte[] getLocalPublicKey() {
        return cryptoManager.getLocalPublicKey();
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    /**
     * Packet listener interface.
     */
    public interface PacketListener {
        void onPacketReceived(PeerConnection connection, Packet packet);
    }

    /**
     * Send HELLO control packet to ask peer for its public key.
     */
    public CompletableFuture<byte[]> sendHelloForPublicKey(SocketAddress to, long timeoutMs) throws IOException {
        Objects.requireNonNull(to, "target address");
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        String key = to.toString();
        pendingPublicKeyRequests.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(future);

        Packet packet = new Packet(0, 0);
        packet.getHeader().setPacketType(PacketHeader.PACKET_TYPE_CONTROL);
        packet.setEncryptedPayload(new byte[]{CONTROL_HELLO});

        byte[] data = packet.toBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        channel.send(buffer, to);

        scheduler.schedule(() -> {
            CopyOnWriteArrayList<CompletableFuture<byte[]>> list = pendingPublicKeyRequests.get(key);
            if (list != null) {
                list.remove(future);
                if (list.isEmpty()) {
                    pendingPublicKeyRequests.remove(key);
                }
            }
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Timeout waiting PUBLIC_KEY from " + key));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        return future;
    }
}
