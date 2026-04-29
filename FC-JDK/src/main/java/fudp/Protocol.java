package fudp;

import core.crypto.KeyTools;
import fudp.congestion.CongestionControl;
import fudp.connection.*;
import fudp.crypto.*;
import fudp.packet.*;
import fudp.packet.frames.*;
import fudp.security.*;
import fudp.stream.Stream;
import fudp.transport.SentPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main entry point for FUDP protocol.
 * 
 * Simplified version using only AsyTwoWay (ECDH) encryption.
 * No symmetric key negotiation required.
 */
public class Protocol {
    private static final Logger log = LoggerFactory.getLogger(Protocol.class);

    private final CryptoManager cryptoManager;
    private final ConnectionManager connectionManager;
    private final PacketCrypto packetCrypto;
    private final ReplayProtection replayProtection;
    private final DecryptRateLimiter decryptRateLimiter;

    private final DatagramChannel channel;
    private Thread receiveThread;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> ackTask;
    private ScheduledFuture<?> retransmitTask;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<CompletableFuture<byte[]>>> pendingPublicKeyRequests;

    private volatile boolean running = false;

    // Protocol settings
    private static final int DEFAULT_MAX_PACKET_SIZE = 1350;
    private static final int HEADER_OVERHEAD = PacketHeader.HEADER_SIZE + 52; // Header + crypto bundle overhead

    // Instance-level packet size (configurable for LAN/localhost with larger datagrams)
    private final int maxPacketSize;
    private final int maxPayloadSize;

    // Pacing configuration
    private final int pacingBurstOverride;     // -1 = auto-calculate
    private final long pacingIntervalNanos;    // pause duration between bursts
    private static final int MAX_BURST_BYTES = 512 * 1024; // 512KB max per burst to prevent receiver overflow

    // Plaintext control message types
    private static final byte CONTROL_HELLO = 0x01;
    private static final byte CONTROL_PUBLIC_KEY = 0x02;
    private static final byte CONTROL_CHALLENGE = 0x03;
    private static final byte CONTROL_CHALLENGE_RESPONSE = 0x04;
    private static final int CONTROL_PAYLOAD_MAX = 256;

    // Public key response rate limit (per remote socket address)
    private static final long PUBKEY_WINDOW_MS = 2000;
    private static final int PUBKEY_MAX_PER_WINDOW = 3;
    private final ConcurrentHashMap<String, PublicKeyResponseBucket> publicKeyResponseBuckets = new ConcurrentHashMap<>();

    /** Idle threshold for session-epoch eviction: connections idle longer than this
     *  with a different epoch than the incoming packet are considered stale (30 seconds). */
    private static final long STALE_IDLE_THRESHOLD_MS = 30_000;

    // Event listeners
    private final List<PacketListener> packetListeners;

    // DDoS defense
    private DDoSConfig ddosConfig;
    private IpVerifier ipVerifier;
    private ChallengeHandler challengeHandler;
    private ScheduledFuture<?> ddosCleanupTask;

    public Protocol(byte[] privateKey, int port, String dataDir) throws IOException {
        this(privateKey, port, dataDir, DEFAULT_MAX_PACKET_SIZE);
    }

    public Protocol(byte[] privateKey, int port, String dataDir, int maxPacketSize) throws IOException {
        this(privateKey, port, dataDir, maxPacketSize, -1, 1_000_000, 2 * 1024 * 1024);
    }

    public Protocol(byte[] privateKey, int port, String dataDir, int maxPacketSize,
                    int pacingBurstOverride, long pacingIntervalNanos, int socketBufferSize) throws IOException {
        this.maxPacketSize = maxPacketSize;
        this.maxPayloadSize = maxPacketSize - HEADER_OVERHEAD;
        this.pacingBurstOverride = pacingBurstOverride;
        this.pacingIntervalNanos = pacingIntervalNanos;
        this.cryptoManager = new CryptoManager(privateKey);
        this.connectionManager = new ConnectionManager();
        this.packetCrypto = new PacketCrypto(cryptoManager);
        this.replayProtection = new ReplayProtection();
        this.decryptRateLimiter = new DecryptRateLimiter();

        this.channel = DatagramChannel.open();
        this.channel.bind(new InetSocketAddress(port));
        this.channel.configureBlocking(false);

        // Set UDP socket buffers. Larger buffers prevent packet loss at high throughput.
        try {
            this.channel.setOption(java.net.StandardSocketOptions.SO_SNDBUF, socketBufferSize);
            this.channel.setOption(java.net.StandardSocketOptions.SO_RCVBUF, socketBufferSize);
        } catch (Exception e) {
            // Best effort; some OSes cap the max value
            log.warn("[Protocol] Could not increase socket buffer sizes: {}", e.getMessage());
        }

        this.scheduler = Executors.newScheduledThreadPool(2);
        this.pendingPublicKeyRequests = new ConcurrentHashMap<>();

        this.packetListeners = new CopyOnWriteArrayList<>();

        // Initialize DDoS defense with default config (can be overridden)
        initDDoSDefense(new DDoSConfig());
    }

    /**
     * Initialize or reconfigure DDoS defense.
     * 
     * Components are always created regardless of the current enabled state,
     * so that toggling {@link DDoSConfig#setEnabled(boolean)} at runtime
     * takes effect immediately without restarting the node.
     * {@link IpVerifier#checkIncoming} already returns {@code DISABLED} when
     * the config flag is off, so there is no overhead when defence is disabled.
     * 
     * @param config the DDoS configuration
     */
    public void initDDoSDefense(DDoSConfig config) {
        this.ddosConfig = config;
        
        // Shutdown previous challenge handler if re-configuring
        if (this.challengeHandler != null) {
            this.challengeHandler.shutdown();
        }
        
        // Always create components so runtime enable/disable works immediately
        this.ipVerifier = new IpVerifier(config, this::sendChallengePacket);
        this.challengeHandler = new ChallengeHandler(config, this::sendChallengeResponsePacket);
    }

    /**
     * Send a CHALLENGE control packet to an address.
     */
    private void sendChallengePacket(SocketAddress to, byte[] payload) {
        try {
            Packet packet = new Packet(0, 0);
            packet.getHeader().setPacketType(PacketHeader.PACKET_TYPE_CONTROL);
            packet.setEncryptedPayload(payload);

            byte[] data = packet.toBytes();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            channel.send(buffer, to);
        } catch (Exception e) {
            // Ignore send errors for challenges
        }
    }

    /**
     * Send a CHALLENGE_RESPONSE control packet to an address.
     * 
     * After sending the response, we re-send any pending HELLO request to this address,
     * because the original HELLO was dropped by the peer's IpVerifier before we were verified.
     */
    private void sendChallengeResponsePacket(SocketAddress to, byte[] payload) {
        try {
            // Send the challenge response
            Packet packet = new Packet(0, 0);
            packet.getHeader().setPacketType(PacketHeader.PACKET_TYPE_CONTROL);
            packet.setEncryptedPayload(payload);

            byte[] data = packet.toBytes();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            channel.send(buffer, to);
            
            // After sending challenge response, re-send HELLO if we have a pending public key request.
            // This is necessary because the original HELLO was dropped by the peer's IpVerifier.
            // 
            // Note: We need to find the matching pending request by IP:port since the key format
            // may differ (hostname vs IP). The challenge comes from IP address, but the original
            // request may have been made with hostname.
            String matchedKey = findPendingRequestKey(to);
            if (matchedKey != null) {
                // Schedule HELLO re-send after a short delay to ensure challenge response arrives first
                log.debug("[Protocol] Scheduling HELLO re-send to {} (matched key: {})", to, matchedKey);
                scheduler.schedule(() -> resendHelloIfPending(to, matchedKey), 50, TimeUnit.MILLISECONDS);
            } else {
                log.debug("[Protocol] No pending public key request found for {}", to);
            }
        } catch (Exception e) {
            // Ignore send errors for challenge responses
        }
    }
    
    /**
     * Find a pending public key request that matches the given address.
     * The challenge response comes from an IP address, but the original request
     * may have been made with a hostname (e.g., "apip.cash:8500" vs "194.233.79.6:8500").
     * 
     * @param addr the address to match (from received challenge)
     * @return the matching key in pendingPublicKeyRequests, or null if not found
     */
    private String findPendingRequestKey(SocketAddress addr) {
        if (!(addr instanceof InetSocketAddress inet)) {
            return null;
        }
        
        String ip = inet.getAddress().getHostAddress();
        int port = inet.getPort();
        
        // First, try exact match
        String exactKey = addr.toString();
        if (pendingPublicKeyRequests.containsKey(exactKey)) {
            return exactKey;
        }
        
        // Search for matching IP:port in all pending requests
        for (String key : pendingPublicKeyRequests.keySet()) {
            // Key format is typically "hostname/ip:port" or "/ip:port"
            if (key.contains(ip) && key.endsWith(":" + port)) {
                return key;
            }
        }
        
        return null;
    }
    
    /**
     * Re-send HELLO packet if there's still a pending public key request for this address.
     */
    private void resendHelloIfPending(SocketAddress to, String key) {
        try {
            if (pendingPublicKeyRequests.containsKey(key)) {
                log.debug("[Protocol] Re-sending HELLO to {} after challenge completed", to);
                Packet helloPacket = new Packet(0, 0);
                helloPacket.getHeader().setPacketType(PacketHeader.PACKET_TYPE_CONTROL);
                helloPacket.setEncryptedPayload(new byte[]{CONTROL_HELLO});
                
                byte[] helloData = helloPacket.toBytes();
                ByteBuffer helloBuffer = ByteBuffer.wrap(helloData);
                channel.send(helloBuffer, to);
            } else {
                log.debug("[Protocol] Pending request for {} already completed, skip HELLO re-send", key);
            }
        } catch (Exception e) {
            log.warn("[Protocol] Failed to re-send HELLO to {}: {}", to, e.getMessage());
        }
    }

    /**
     * Start the protocol.
     */
    public void start() {
        if (running) return;
        running = true;

        // Start receive loop on a dedicated thread
        receiveThread = new Thread(this::receiveLoop, "fudp-recv-" + localPort());
        receiveThread.setDaemon(true);
        receiveThread.start();

        // Start ACK timer (reduced from 25ms to 5ms for lower latency)
        ackTask = scheduler.scheduleAtFixedRate(this::ackTimerTask, 5, 5, TimeUnit.MILLISECONDS);

        // Start retransmission check (reduced from 100ms to 50ms for faster recovery)
        retransmitTask = scheduler.scheduleAtFixedRate(this::retransmitTask, 50, 50, TimeUnit.MILLISECONDS);

        // Start DDoS defense cleanup task (always scheduled; harmless when disabled)
        if (ddosConfig != null) {
            long cleanupInterval = ddosConfig.getCleanupIntervalMs();
            ddosCleanupTask = scheduler.scheduleAtFixedRate(this::ddosCleanupTask, 
                    cleanupInterval, cleanupInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * DDoS defense cleanup task.
     */
    private void ddosCleanupTask() {
        if (ipVerifier != null) {
            ipVerifier.cleanup();
        }
        if (challengeHandler != null) {
            challengeHandler.cleanup();
        }
        // Clean stale public key response buckets
        long bucketCutoff = System.currentTimeMillis() - 60_000;
        publicKeyResponseBuckets.entrySet().removeIf(e -> e.getValue().windowStart < bucketCutoff);
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
        if (ddosCleanupTask != null) {
            ddosCleanupTask.cancel(true);
        }
        
        // Shutdown DDoS defense components
        if (challengeHandler != null) {
            challengeHandler.shutdown();
        }

        // Capture port before closing channel (localPort() returns -1 after close)
        int port = localPort();

        // Close channel first to unblock receive loop
        try {
            channel.close();
        } catch (IOException e) {
            // Ignore
        }
        
        // Wait for receive thread to finish
        if (receiveThread != null) {
            try {
                receiveThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Log stats on shutdown (after receive thread has stopped, so counters are final)
        System.err.println("[PROTOCOL-STOP] port=" + port + 
                " received=" + receivedPacketCount.get() + 
                " sent=" + sendTotalCount.get() + 
                " processed=" + packetsFullyProcessed.get() +
                " streamFrames=" + totalStreamFramesDelivered.get() +
                " decryptFails=" + decryptFailCount.get() +
                " replayDups=" + replayDuplicateCount.get());
        
        // Dump stream state for debugging
        for (PeerConnection conn : connectionManager.getAllConnections()) {
            for (fudp.stream.Stream s : conn.getStreamManager().getAllStreams()) {
                System.err.println("[STREAM-STOP] stream=" + s.getStreamId() +
                        " recvOffset=" + s.getRecvOffset() + " sendOffset=" + s.getSendOffset());
            }
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
        connectionManager.closeAll();
    }

    /**
     * Connect to a peer (create or retrieve the connection).
     * Does NOT pre-open a stream; callers open streams as needed.
     */
    public void connect(byte[] peerPublicKey, SocketAddress address) throws IOException {
        String peerId = KeyTools.pubkeyToFchAddr(peerPublicKey);
        PeerConnection conn = connectionManager.getOrCreate(peerId, address);
        conn.setPeerPublicKey(peerPublicKey);
    }

    /**
     * Estimate the frame overhead for a StreamFrame on the given stream.
     * This includes the type varint, streamId varint, optional offset varint, and length varint.
     */
    private int estimateFrameOverhead(Stream stream) {
        // type(1-2) + streamId(1-8) + offset(0-8) + length(1-4) ≈ conservative estimate of 30 bytes
        return 30;
    }

    /**
     * Calculate how many frames to send in a burst before pausing.
     * With small packets (1350 bytes), each frame becomes an individually encrypted UDP
     * datagram. Sending too many in a tight loop overwhelms the receiver's ability to
     * decrypt, process, and ACK them, causing UDP buffer overflow and packet loss.
     *
     * The burst is also capped by MAX_BURST_BYTES to prevent receiver buffer overflow
     * with large packets.
     */
    private static final int MIN_BURST_BYTES = 16384; // 16KB target per burst

    private int calculatePacingBurst() {
        // If user explicitly set a burst override, use it
        if (pacingBurstOverride > 0) {
            return pacingBurstOverride;
        }

        // Auto-calculate: target MIN_BURST_BYTES (16KB) per burst.
        // This compensates for OS timer imprecision (parkNanos(1ms) often sleeps
        // 5-15ms on macOS), ensuring reasonable throughput regardless of MTU.
        //
        // With 1350-byte MTU: max(2, 16384/1277) = 12 → 12*1277=15KB/burst → ~1-3 MB/s
        // With 8000-byte MTU: max(2, 16384/7927) =  2 →  2*7927=16KB/burst → ~1-3 MB/s
        // With 60000-byte MTU: max(2, 16384/59927) = 2 → 2*60KB=120KB/burst (capped below)
        int burst = Math.max(2, MIN_BURST_BYTES / Math.max(1, maxPayloadSize));

        // Cap burst so total bytes per burst doesn't exceed MAX_BURST_BYTES.
        // This prevents receiver buffer overflow with large MTU.
        int maxBurstByBytes = Math.max(1, MAX_BURST_BYTES / maxPacketSize);
        return Math.min(burst, maxBurstByBytes);
    }

    /**
     * Time-based pacing: pauses after every `pacingBurst` frames.
     * Uses LockSupport.parkNanos for sub-millisecond precision when configured.
     *
     * Default (1ms nominal interval, actual ~5-15ms on macOS):
     * - 1350-byte packets (burst=12): ~16KB/burst → ~1-3 MB/s
     * - 8000-byte packets (burst=2):  ~16KB/burst → ~1-3 MB/s
     * - 60000-byte packets (burst=2): ~120KB/burst → ~8-24 MB/s
     *
     * With 200us interval (LAN mode):
     * - 1350-byte packets (burst=12): ~60 frames/ms → ~75 MB/s
     */
    private void paceSending(PeerConnection conn, int framesSent, int pacingBurst) {
        if (framesSent > 0 && framesSent % pacingBurst == 0) {
            // Use LockSupport.parkNanos for sub-millisecond precision.
            // Thread.sleep(1) actually sleeps 1-2ms on most OS;
            // parkNanos(200_000) gives ~200us precision on modern hardware.
            java.util.concurrent.locks.LockSupport.parkNanos(pacingIntervalNanos);
        }
    }

    /**
     * Send data on a stream.
     * For large data, automatically splits into multiple MTU-safe StreamFrames
     * to avoid relying on IP fragmentation for UDP datagrams.
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

        int maxChunkSize = maxPayloadSize - estimateFrameOverhead(stream);
        if (maxChunkSize < 100) maxChunkSize = 100; // Safety floor

        if (data.length <= maxChunkSize) {
            // Small data: send in one frame (existing fast path)
            long offset = stream.consumeSendOffset(data.length);
            StreamFrame frame = new StreamFrame(stream.getStreamId(), offset, data, false);
            sendFrame(conn, frame);
        } else {
            // Large data: split into multiple MTU-safe frames with pacing
            int pos = 0;
            int framesSent = 0;
            int pacingBurst = calculatePacingBurst();
            while (pos < data.length) {
                int chunkSize = Math.min(maxChunkSize, data.length - pos);
                byte[] chunk = Arrays.copyOfRange(data, pos, pos + chunkSize);
                long offset = stream.consumeSendOffset(chunkSize);
                StreamFrame frame = new StreamFrame(stream.getStreamId(), offset, chunk, false);
                sendFrame(conn, frame);
                pos += chunkSize;
                framesSent++;
                paceSending(conn, framesSent, pacingBurst);
            }
        }
    }

    /**
     * Send data and close the stream.
     * For large data, automatically splits into multiple MTU-safe StreamFrames,
     * with the FIN flag set only on the last frame.
     */
    public void sendAndClose(Stream stream, byte[] data) throws IOException {
        sendAndClose(stream, data, null);
    }

    /**
     * Send data and close the stream, with optional progress callback.
     * @param stream   the stream to send on
     * @param data     the data to send
     * @param progress callback receiving (bytesSent, totalBytes) — may be null
     */
    public void sendAndClose(Stream stream, byte[] data, java.util.function.BiConsumer<Long, Long> progress) throws IOException {
        PeerConnection conn = getConnectionForStream(stream);
        if (conn == null) {
            throw new IOException("No connection for stream");
        }

        int maxChunkSize = maxPayloadSize - estimateFrameOverhead(stream);
        if (maxChunkSize < 100) maxChunkSize = 100; // Safety floor

        if (data.length <= maxChunkSize) {
            // Small data: send in one frame with FIN (existing fast path)
            long offset = stream.consumeSendOffset(data.length);
            StreamFrame frame = new StreamFrame(stream.getStreamId(), offset, data, true);
            sendFrame(conn, frame);
            if (progress != null) progress.accept((long) data.length, (long) data.length);
        } else {
            // Large data: split into multiple frames with pacing, FIN on last.
            // Flow control is done via pacing only (not congestion window),
            // because the congestion control's loss detection creates phantom loss
            // events on fast networks, collapsing the window and throttling throughput.
            int pos = 0;
            int framesSent = 0;
            int pacingBurst = calculatePacingBurst();
            while (pos < data.length) {
                int chunkSize = Math.min(maxChunkSize, data.length - pos);
                byte[] chunk = Arrays.copyOfRange(data, pos, pos + chunkSize);
                boolean isLast = (pos + chunkSize >= data.length);
                long offset = stream.consumeSendOffset(chunkSize);
                StreamFrame frame = new StreamFrame(stream.getStreamId(), offset, chunk, isLast);
                sendFrame(conn, frame);
                pos += chunkSize;
                framesSent++;
                if (progress != null) progress.accept((long) pos, (long) data.length);
                if (!isLast) {
                    paceSending(conn, framesSent, pacingBurst);
                }
            }
        }
        stream.closeSend();
    }

    /**
     * Send data from an InputStream and close the stream.
     * Reads data in MTU-safe chunks from the InputStream, sending each as a StreamFrame.
     * The FIN flag is set on the last frame. This avoids loading the entire payload into memory.
     *
     * @param stream      The FUDP stream to send on
     * @param input       InputStream providing the data
     * @param totalLength Total number of bytes to read and send
     * @throws IOException if sending fails or the stream is broken
     */
    public void sendAndCloseFromInputStream(Stream stream, java.io.InputStream input, long totalLength) throws IOException {
        PeerConnection conn = getConnectionForStream(stream);
        if (conn == null) {
            throw new IOException("No connection for stream");
        }

        int maxChunkSize = maxPayloadSize - estimateFrameOverhead(stream);
        if (maxChunkSize < 100) maxChunkSize = 100; // Safety floor

        byte[] buffer = new byte[maxChunkSize];
        long remaining = totalLength;
        int framesSent = 0;
        int pacingBurst = calculatePacingBurst();

        while (remaining > 0) {
            int toRead = (int) Math.min(maxChunkSize, remaining);
            int bytesRead = readFully(input, buffer, toRead);
            if (bytesRead <= 0) {
                break;
            }
            remaining -= bytesRead;
            boolean isLast = (remaining <= 0);

            byte[] chunk = (bytesRead == buffer.length) ? buffer.clone() : Arrays.copyOf(buffer, bytesRead);
            long offset = stream.consumeSendOffset(bytesRead);
            StreamFrame frame = new StreamFrame(stream.getStreamId(), offset, chunk, isLast);
            sendFrame(conn, frame);
            framesSent++;
            if (!isLast) {
                paceSending(conn, framesSent, pacingBurst);
            }
        }
        stream.closeSend();
    }

    /**
     * Send data from an InputStream without closing the stream.
     * Reads data in MTU-safe chunks from the InputStream, sending each as a StreamFrame.
     *
     * @param stream      The FUDP stream to send on
     * @param input       InputStream providing the data
     * @param totalLength Total number of bytes to read and send
     * @throws IOException if sending fails or the stream is broken
     */
    public void sendFromInputStream(Stream stream, java.io.InputStream input, long totalLength) throws IOException {
        PeerConnection conn = getConnectionForStream(stream);
        if (conn == null) {
            throw new IOException("No connection for stream");
        }

        int maxChunkSize = maxPayloadSize - estimateFrameOverhead(stream);
        if (maxChunkSize < 100) maxChunkSize = 100; // Safety floor

        byte[] buffer = new byte[maxChunkSize];
        long remaining = totalLength;
        int framesSent = 0;
        int pacingBurst = calculatePacingBurst();

        while (remaining > 0) {
            int toRead = (int) Math.min(maxChunkSize, remaining);
            int bytesRead = readFully(input, buffer, toRead);
            if (bytesRead <= 0) {
                break;
            }
            remaining -= bytesRead;

            byte[] chunk = (bytesRead == buffer.length) ? buffer.clone() : Arrays.copyOf(buffer, bytesRead);
            long offset = stream.consumeSendOffset(bytesRead);
            StreamFrame frame = new StreamFrame(stream.getStreamId(), offset, chunk, false);
            sendFrame(conn, frame);
            framesSent++;
            paceSending(conn, framesSent, pacingBurst);
        }
    }

    /**
     * Read exactly {@code len} bytes from input, or until EOF.
     * @return actual bytes read, or 0 if EOF immediately
     */
    private static int readFully(java.io.InputStream in, byte[] buf, int len) throws IOException {
        int totalRead = 0;
        while (totalRead < len) {
            int n = in.read(buf, totalRead, len - totalRead);
            if (n < 0) break;
            totalRead += n;
        }
        return totalRead;
    }

    /**
     * Close a specific connection by connectionId.
     */
    public void close(long connectionId, int errorCode, String reason) throws IOException {
        PeerConnection conn = connectionManager.getByConnectionId(connectionId);
        if (conn == null) return;

        ConnectionCloseFrame frame = new ConnectionCloseFrame(errorCode, reason);
        sendFrame(conn, frame);
        conn.setState(ConnectionState.CLOSING);
    }

    /**
     * Close all connections for a peer.
     */
    public void closeAll(String peerId, int errorCode, String reason) throws IOException {
        for (PeerConnection conn : connectionManager.getConnectionsByPeerId(peerId)) {
            ConnectionCloseFrame frame = new ConnectionCloseFrame(errorCode, reason);
            sendFrame(conn, frame);
            conn.setState(ConnectionState.CLOSING);
        }
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
        sendPacket(conn, frames, 0);
    }

    /**
     * Send a packet with frames, optionally inheriting a retransmit count from a previous attempt.
     */
    private void sendPacket(PeerConnection conn, List<Frame> frames, int retransmitCount) throws IOException {
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

        // E1: Skip timestamp for ACK-only packets (saves 8 bytes)
        boolean includeTimestamp = packet.isAckEliciting();
        // E2: Skip epoch once peer has confirmed receipt (saves 8 bytes)
        boolean includeEpoch = !conn.isEpochConfirmed();

        // Encrypt using AsyTwoWay
        packetCrypto.encryptPacket(packet, conn.getPeerId(), conn.getPeerPublicKey(),
                includeTimestamp, includeEpoch);

        // IMPORTANT: Record in sentPackets BEFORE sending the UDP datagram.
        // On localhost (near-zero latency), the ACK can arrive before recordSentPacket
        // is called.  If the ACK handler calls sentPackets.remove(pn) and finds nothing,
        // onAck() is never called, bytesInFlight is never decremented, and the packet
        // sits in sentPackets until the retransmit task falsely marks it as lost.
        byte[] data = packet.toBytes();
        boolean ackEliciting = packet.isAckEliciting();
        conn.recordSentPacket(packetNumber, frames, data.length, ackEliciting, retransmitCount);
        conn.getCongestionControl().onSend(data.length);

        // Send with retry if OS buffer is full (non-blocking channel returns 0)
        ByteBuffer buffer = ByteBuffer.wrap(data);
        try {
            int sent = channel.send(buffer, conn.getPeerAddress());
            if (sent == 0) {
                sendDropCount.incrementAndGet();
                // OS send buffer full; retry with backoff (up to 10ms total)
                for (int retry = 0; retry < 10 && sent == 0; retry++) {
                    try { Thread.sleep(1); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    buffer.rewind();
                    sent = channel.send(buffer, conn.getPeerAddress());
                }
                if (sent == 0) {
                    long fails = sendFailCount.incrementAndGet();
                    if (fails <= 5 || fails % 100 == 0) {
                        System.err.println("[SEND-FAIL] Dropped packet after retries! drops=" + sendDropCount.get() + 
                                " fails=" + fails + " total=" + sendTotalCount.get());
                    }
                }
            }
            sendTotalCount.incrementAndGet();
        } catch (java.nio.channels.ClosedChannelException e) {
            // Channel closed during shutdown, ignore
            return;
        }
    }

    /**
     * Receive loop - processes packets inline in a single dedicated thread.
     * Single-threaded processing ensures in-order delivery and avoids
     * replay-protection and stream-reassembly races.
     */
    // Monitoring counters
    private final AtomicLong receivedPacketCount = new AtomicLong();
    private final AtomicLong decryptFailCount = new AtomicLong();
    private final AtomicLong sendDropCount = new AtomicLong();
    private final AtomicLong sendFailCount = new AtomicLong();
    private final AtomicLong sendTotalCount = new AtomicLong();

    private final AtomicLong replayDuplicateCount = new AtomicLong();
    private final AtomicLong packetsFullyProcessed = new AtomicLong();
    private final AtomicLong decryptDropCount = new AtomicLong();
    private final AtomicLong unsupportedVersionCount = new AtomicLong();

    /**
     * Versions we accept on the data path. Currently only the single
     * released wire format; future protocol upgrades will use the CAPS
     * frame negotiation path to widen this set during a rollout window.
     */
    private static boolean isSupportedDataVersion(int v) {
        return v == PacketHeader.CURRENT_VERSION;
    }

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
                    receivedPacketCount.incrementAndGet();
                    handleIncomingPacket(data, sender);
                } else {
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void handleIncomingPacket(byte[] data, SocketAddress from) {
        try {
            // IP verification (DDoS defense) - before any expensive crypto
            if (ipVerifier != null) {
                IpVerifier.Decision decision = ipVerifier.checkIncoming(from, data);
                switch (decision) {
                    case VERIFIED_ALLOW:
                    case DISABLED:
                    case CHALLENGE_VERIFIED:
                        break; // Continue processing
                    default:
                        return; // Drop packet (rate limited, challenge pending, etc.)
                }
            }

            // Parse packet header
            Packet packet = Packet.fromBytes(data);

            // Plaintext control handling (HELLO / PUBLIC_KEY / CHALLENGE / CHALLENGE_RESPONSE)
            if (packet.getHeader().isControlPacket()) {
                handlePlaintextControl(packet, from);
                return;
            }

            // Ignore plaintext error packets
            if (packet.getHeader().isErrorPacket()) {
                return;
            }

            // F1c: data-path version gate. Reject any data/ack packet whose
            // header version is not currently supported BEFORE we burn ECDH
            // on it. Senders that pre-date header-AAD enforcement omitted
            // AAD entirely — silently accepting them here would recreate
            // the F1 vulnerability. Control packets are plaintext and
            // version-agnostic, so the check is scoped to data/ack.
            int wireVersion = packet.getHeader().getVersion();
            if (!isSupportedDataVersion(wireVersion)) {
                long n = unsupportedVersionCount.incrementAndGet();
                if (n <= 5 || n % 1000 == 0) {
                    log.warn("[Protocol] Rejecting data packet from {} with unsupported version {} (count={})",
                            from, wireVersion, n);
                }
                return;
            }

            // Source-rate guard before the expensive ECDH/decrypt step (N1).
            // Independent of DDoS defense being enabled — protects against
            // an attacker flooding fresh per-packet pubkeys to force ECDH
            // misses, even on nodes that opt out of IpVerifier.
            if (decryptRateLimiter.shouldDrop(from)) {
                long n = decryptDropCount.incrementAndGet();
                if (n <= 5 || n % 1000 == 0) {
                    log.warn("[Protocol] Dropping packet from {} due to recent decrypt-failure flood (drops={})",
                            from, n);
                }
                return;
            }

            // Decrypt and identify sender
            String senderId;
            try {
                senderId = packetCrypto.decryptPacket(packet);
            } catch (Exception e) {
                long dfCount = decryptFailCount.incrementAndGet();
                if (dfCount <= 5 || dfCount % 100 == 0) {
                    System.err.println("[DECRYPT-FAIL] count=" + dfCount +
                            " pktNum=" + packet.getPacketNumber() + " from=" + from);
                }
                // Track failure for per-source rate limiting (N1).
                decryptRateLimiter.recordFailure(from);
                // Do NOT reply with PUBLIC_KEY on decrypt failure (N2). It
                // turns this socket into a reflection amplifier for spoofed
                // source IPs. Peers that need our pubkey must send an
                // explicit CONTROL_HELLO; that path is rate-limited via
                // allowPublicKeyResponse(). Drop silently.
                return;
            }
            // Successful decrypt: clear any failure history for this source
            // so a transient burst of bad packets doesn't penalise a
            // legitimate peer once they recover.
            decryptRateLimiter.recordSuccess(from);

            // Skip creating a new connection for close-only packets.
            // When a client sends CONNECTION_CLOSE and the server has already removed
            // the old connection, getOrCreate would create a brand-new connection just
            // to immediately tear it down — causing rapid create/remove churn.
            if (isCloseOnlyPacket(packet)) {
                PeerConnection existingConn = connectionManager.getByAddress(from);
                if (existingConn != null && existingConn.getPeerId().equals(senderId)) {
                    // Process close on the existing connection
                    existingConn.setState(ConnectionState.CLOSED);
                    connectionManager.removeConnection(existingConn.getConnectionId());
                    replayProtection.removeConnection(existingConn.getConnectionId());
                    log.debug("Processed CONNECTION_CLOSE on existing connection {} for peer {} from {}",
                            existingConn.getConnectionId(), senderId, from);
                } else {
                    log.debug("Ignoring CONNECTION_CLOSE from {} for peer {} — no active connection to close",
                            from, senderId);
                }
                return;
            }

            // Get or create connection
            PeerConnection conn = connectionManager.getOrCreate(senderId, from);
            if (packet.getPeerPublicKey() != null) {
                conn.setPeerPublicKey(packet.getPeerPublicKey());
            }

            // E2: If epoch was not included in packet, use the connection's known epoch
            long incomingEpoch = packet.getSessionEpoch();
            if (incomingEpoch == 0 && !packet.getHeader().hasEpoch()) {
                incomingEpoch = conn.getSessionEpoch();
            }
            // Store session epoch on the connection (first packet sets it).
            if (incomingEpoch != 0 && conn.getSessionEpoch() == 0) {
                conn.setSessionEpoch(incomingEpoch);
            }

            // Session epoch based stale connection eviction.
            // Only evict connections from the SAME address with a different epoch (peer restart).
            // Connections from different addresses are different physical devices and must be kept.
            if (incomingEpoch != 0) {
                long now = System.currentTimeMillis();
                for (PeerConnection other : connectionManager.getConnectionsByPeerId(senderId)) {
                    if (other.getConnectionId() == conn.getConnectionId()) continue;
                    long otherEpoch = other.getSessionEpoch();
                    if (otherEpoch != 0 && otherEpoch != incomingEpoch
                            && other.getPeerAddress().equals(conn.getPeerAddress())) {
                        long idle = now - other.getLastActivity().toEpochMilli();
                        if (idle > STALE_IDLE_THRESHOLD_MS) {
                            log.info("[Protocol] Evicting stale connection {} for peer {} " +
                                            "(idle={}ms, epoch={}, new epoch={} from {})",
                                    other.getConnectionId(), senderId, idle, otherEpoch, incomingEpoch, from);
                            other.setState(ConnectionState.CLOSED);
                            replayProtection.removeConnection(other.getConnectionId());
                            connectionManager.removeConnection(other.getConnectionId());
                        }
                    }
                }
            }

            // Replay protection with session epoch for peer restart detection
            // Keyed by connectionId (not peerId) to support multiple connections per FID
            ReplayProtection.CheckResult result = replayProtection.checkAndRecord(
                    conn.getConnectionId(), packet.getPacketNumber(), packet.getTimestamp(), incomingEpoch);

            if (result == ReplayProtection.CheckResult.INVALID_TIMESTAMP) {
                System.err.println("[Protocol] Invalid timestamp from " + senderId);
                close(conn.getConnectionId(), ConnectionCloseFrame.INTERNAL_ERROR, "Invalid timestamp");
                return;
            }

            // Handle peer restart
            if (result == ReplayProtection.CheckResult.PEER_RESTART) {
                if (conn.tryMarkPeerRestartHandled()) {
                    System.err.println("[Protocol] Peer restart detected for " + senderId);
                    conn.resetForPeerRestart();
                }
            }

            boolean ackEliciting = packet.isAckEliciting();
            if (result == ReplayProtection.CheckResult.DUPLICATE) {
                replayDuplicateCount.incrementAndGet();
                if (ackEliciting) {
                    conn.getAckManager().onPacketReceived(packet.getPacketNumber());
                    sendAck(conn);
                }
                return;
            }

            conn.onPacketReceived(data.length);

            if (ackEliciting) {
                conn.getAckManager().onPacketReceived(packet.getPacketNumber());
            }

            // Process frames
            for (Frame frame : packet.getFrames()) {
                handleFrame(conn, frame);
            }
            packetsFullyProcessed.incrementAndGet();

            // Send immediate ACK if needed
            if (ackEliciting && conn.getAckManager().shouldSendAckImmediately()) {
                sendAck(conn);
            }

            // Notify listeners
            for (PacketListener listener : packetListeners) {
                listener.onPacketReceived(conn, packet);
            }

        } catch (Exception e) {
            if (running) {
                System.err.println("[HANDLE-EX] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Check if a packet contains only CONNECTION_CLOSE frame(s) and no data frames.
     */
    private boolean isCloseOnlyPacket(Packet packet) {
        List<Frame> frames = packet.getFrames();
        if (frames == null || frames.isEmpty()) return false;
        for (Frame frame : frames) {
            if (frame.getType() != FrameType.CONNECTION_CLOSE && frame.getType() != FrameType.ACK) {
                return false;
            }
        }
        // Must have at least one CONNECTION_CLOSE frame
        for (Frame frame : frames) {
            if (frame.getType() == FrameType.CONNECTION_CLOSE) {
                return true;
            }
        }
        return false;
    }

    private int localPort() {
        try { return ((InetSocketAddress) channel.getLocalAddress()).getPort(); } catch (Exception e) { return -1; }
    }

    /**
     * Handle plaintext control packets (HELLO / PUBLIC_KEY / CHALLENGE / CHALLENGE_RESPONSE).
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
                
                // Find matching pending request by IP:port (key format may differ due to hostname)
                String matchedKey = findPendingRequestKey(from);
                if (matchedKey != null) {
                    log.debug("[Protocol] Received PUBLIC_KEY from {}, matched key: {}", from, matchedKey);
                    CopyOnWriteArrayList<CompletableFuture<byte[]>> waiters = pendingPublicKeyRequests.remove(matchedKey);
                    if (waiters != null) {
                        for (CompletableFuture<byte[]> waiter : waiters) {
                            waiter.complete(pubkey);
                        }
                    }
                } else {
                    log.debug("[Protocol] Received PUBLIC_KEY from {} but no pending request found", from);
                }
            }
            case CONTROL_CHALLENGE -> {
                // We received a challenge from a peer we're trying to connect to
                // This is the initiator side - solve the PoW and send response
                if (challengeHandler != null) {
                    challengeHandler.handleChallenge(from, payload);
                }
            }
            case CONTROL_CHALLENGE_RESPONSE -> {
                // Challenge responses are handled in IpVerifier.checkIncoming()
                // This case is here for completeness if the packet gets through
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
    // Debug: track total stream frames delivered
    private final AtomicLong totalStreamFramesDelivered = new AtomicLong();
    
    private void handleFrame(PeerConnection conn, Frame frame) throws IOException {
        switch (frame.getType()) {
            case STREAM -> {
                StreamFrame streamFrame = (StreamFrame) frame;
                Stream stream = conn.getStreamManager().getOrCreateStream(streamFrame.getStreamId());
                if (stream == null) {
                    log.warn("Stream limit exceeded, dropping frame for stream {}", streamFrame.getStreamId());
                    return;
                }
                try {
                    stream.onDataReceived(streamFrame.getOffset(), streamFrame.getData(), streamFrame.isFin());
                } catch (fudp.stream.FlowControlViolationException e) {
                    log.warn("[Protocol] Flow control violation on connection {} from peer {}: {}",
                            conn.getConnectionId(), conn.getPeerId(), e.getMessage());
                    try {
                        close(conn.getConnectionId(),
                                ConnectionCloseFrame.FLOW_CONTROL_ERROR, "Stream buffer overflow");
                    } catch (IOException ignored) {
                        // Best-effort close
                    }
                    connectionManager.removeConnection(conn.getConnectionId());
                    replayProtection.removeConnection(conn.getConnectionId());
                }
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
                log.info("Received CONNECTION_CLOSE from peer {} on connection {} (error={}, reason={})",
                        conn.getPeerId(), conn.getConnectionId(), closeFrame.getErrorCode(), closeFrame.getReasonPhrase());
                conn.setState(ConnectionState.CLOSED);
                connectionManager.removeConnection(conn.getConnectionId());
                replayProtection.removeConnection(conn.getConnectionId());
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
            if (lost.isEmpty()) continue;

            // Rate-limit retransmission to avoid overwhelming the receiver.
            int maxRetransmitPerCycle = 50;
            int retransmitted = 0;

            for (SentPacket packet : lost) {
                if (packet.getRetransmitCount() >= 30) {
                    // Abandon the undeliverable packet but keep the connection alive.
                    // Closing the connection for a single lost stream frame is too aggressive
                    // — other streams on this connection may be working fine.
                    conn.removeSentPacket(packet.packetNumber);
                    continue;
                }

                // Rate limit: leave remaining packets for the next cycle
                if (retransmitted >= maxRetransmitPerCycle) {
                    break;
                }

                // Retransmit frames
                List<Frame> framesToRetransmit = new ArrayList<>();
                for (Frame frame : packet.frames) {
                    if (frame.shouldRetransmit()) {
                        framesToRetransmit.add(frame);
                    }
                }

                if (!framesToRetransmit.isEmpty()) {
                    // Remove old entry to free bytesInFlight (prevents deadlock where
                    // unACK'd packets inflate bytesInFlight permanently).
                    SentPacket removed = conn.removeSentPacket(packet.packetNumber);
                    if (removed == null) {
                        // Already ACK'd — no need to retransmit
                        continue;
                    }

                    // Retransmits ALWAYS proceed regardless of congestion window.
                    // Per QUIC RFC 9002: retransmissions are loss recovery, not new data,
                    // and must not be blocked by congestion control. The rate limiter
                    // (maxRetransmitPerCycle) already prevents flooding.
                    int inheritedRetransmitCount = removed.getRetransmitCount() + 1;
                    try {
                        sendPacket(conn, framesToRetransmit, inheritedRetransmitCount);
                        conn.recordRetransmit();
                        retransmitted++;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (retransmitted > 0) {
                conn.trySignalLoss();
            }
        }
    }

    /**
     * Get connection for a stream.
     */
    private PeerConnection getConnectionForStream(Stream stream) {
        return stream.getConnection();
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

    public ReplayProtection getReplayProtection() {
        return replayProtection;
    }

    /** Per-source decrypt-failure rate limiter (for monitoring/tests). */
    public DecryptRateLimiter getDecryptRateLimiter() {
        return decryptRateLimiter;
    }

    /** Number of packets dropped at the rate-limit guard (for monitoring/tests). */
    public long getDecryptDropCount() {
        return decryptDropCount.get();
    }

    /** Number of decrypt failures (for monitoring/tests). */
    public long getDecryptFailCount() {
        return decryptFailCount.get();
    }

    /** Number of packets rejected for unsupported wire version (for monitoring/tests). */
    public long getUnsupportedVersionCount() {
        return unsupportedVersionCount.get();
    }

    /**
     * Packet listener interface.
     */
    public interface PacketListener {
        void onPacketReceived(PeerConnection connection, Packet packet);
    }

    /**
     * Get the DDoS configuration.
     */
    public DDoSConfig getDdosConfig() {
        return ddosConfig;
    }

    /**
     * Get the IP verifier (for monitoring/stats).
     */
    public IpVerifier getIpVerifier() {
        return ipVerifier;
    }

    /**
     * Get the challenge handler (for monitoring/stats).
     */
    public ChallengeHandler getChallengeHandler() {
        return challengeHandler;
    }

    /**
     * Check if DDoS defense is enabled.
     */
    public boolean isDDoSDefenseEnabled() {
        return ddosConfig != null && ddosConfig.isEnabled();
    }

    /**
     * Manually add an IP to the verified whitelist.
     * Useful for trusted peers or local connections.
     */
    public void addVerifiedIp(String ip) {
        if (ipVerifier != null) {
            ipVerifier.addVerified(ip);
        }
    }

    /**
     * Check if an IP is verified.
     */
    public boolean isIpVerified(String ip) {
        return ipVerifier == null || ipVerifier.isVerified(ip);
    }

    /**
     * Send HELLO control packet to ask peer for its public key.
     * 
     * When we actively initiate a connection, we pre-whitelist the target IP
     * so that any response (including PUBLIC_KEY) from the peer will be allowed through
     * our IpVerifier without being challenged.
     */
    public CompletableFuture<byte[]> sendHelloForPublicKey(SocketAddress to, long timeoutMs) throws IOException {
        Objects.requireNonNull(to, "target address");
        
        // Pre-whitelist the target IP since we're actively initiating this connection.
        // This ensures the peer's responses (e.g., PUBLIC_KEY) won't be challenged.
        if (to instanceof InetSocketAddress inet) {
            String targetIp = inet.getAddress().getHostAddress();
            addVerifiedIp(targetIp);
        }
        
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
