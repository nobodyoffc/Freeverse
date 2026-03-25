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
        this.maxPacketSize = maxPacketSize;
        this.maxPayloadSize = maxPacketSize - HEADER_OVERHEAD;
        this.cryptoManager = new CryptoManager(privateKey);
        this.connectionManager = new ConnectionManager();
        this.packetCrypto = new PacketCrypto(cryptoManager);
        this.replayProtection = new ReplayProtection();

        this.channel = DatagramChannel.open();
        this.channel.bind(new InetSocketAddress(port));
        this.channel.configureBlocking(false);

        // Increase UDP socket buffers for high-throughput transfers.
        // macOS defaults are ~9KB send / ~42KB receive, which causes silent packet
        // loss when sending many frames in a burst.
        try {
            this.channel.setOption(java.net.StandardSocketOptions.SO_SNDBUF, 2 * 1024 * 1024); // 2 MB send buffer
            this.channel.setOption(java.net.StandardSocketOptions.SO_RCVBUF, 2 * 1024 * 1024); // 2 MB receive buffer
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
     * The burst size scales with packet size:
     * - 1350-byte packets → burst of 2 (pause every ~2.5KB, ~500 pps steady state)
     * - 60000-byte packets → burst of 40+ (pause every ~2.4MB, effectively no bottleneck)
     */
    private int calculatePacingBurst() {
        // Scale with payload capacity. Small packets need very conservative pacing.
        // With 1350 packets: maxPayloadSize ≈ 1277 → burst = 1 (sleep every frame)
        // With 10000 packets: maxPayloadSize ≈ 9927 → burst = max(1, 9927/3000) = 3
        // With 60000 packets: maxPayloadSize ≈ 59927 → burst = max(1, 59927/3000) = 19
        return Math.max(1, maxPayloadSize / 3000);
    }

    /**
     * Simple time-based pacing: sleeps 1ms after every `pacingBurst` frames.
     * This provides reliable rate limiting without depending on the congestion
     * control state, which can collapse and deadlock on fast networks.
     *
     * Effective rates:
     * - 1350-byte packets (burst=1): 1000 fps ≈ 1.27 MB/s
     * - 60000-byte packets (burst=19): 19000 fps ≈ very high throughput
     */
    private void paceSending(PeerConnection conn, int framesSent, int pacingBurst) {
        if (framesSent > 0 && framesSent % pacingBurst == 0) {
            try {
                // Pure time-based pacing: sleep 1ms after every pacingBurst frames.
                //
                // We intentionally do NOT block on the congestion window here.
                // The congestion window is enforced only in the retransmission task
                // (to prevent retransmit floods).  For original data, time-based
                // pacing provides sufficient rate-limiting:
                //  - 1350-byte MTU → pacingBurst=1 → 1 frame/ms → ~1.35 MB/s
                //  - 60000-byte MTU → pacingBurst=19 → ~1.14 MB/ms → very fast
                //
                // With the conservative 2000ms loss detection threshold, no false
                // retransmissions occur on localhost or typical WANs, so the cwnd
                // stays at INITIAL_WINDOW (120KB+) and doesn't need enforcement.
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Wait until the congestion window allows sending more data.
     * This prevents the sender from overwhelming the receiver with a burst of
     * unacknowledged packets. The sender blocks until ACKs arrive (processed
     * by the receive thread) and free up window space.
     *
     * @param conn The peer connection to check
     * @param size The size of data to send
     */
    private void waitForCongestionWindow(PeerConnection conn, int size) {
        int waited = 0;
        while (!conn.getCongestionControl().canSend(size) && running && waited < 5000) {
            try {
                Thread.sleep(1);
                waited++;
                if (waited == 200 || waited == 1000 || waited == 3000) {
                    System.err.println("[CWND-WAIT] Blocked for " + waited + "ms: " + conn.getCongestionControl());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (waited >= 5000) {
            System.err.println("[CWND-WAIT] GAVE UP after 5s: " + conn.getCongestionControl());
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

        // Encrypt using AsyTwoWay
        packetCrypto.encryptPacket(packet, conn.getPeerId(), conn.getPeerPublicKey());

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
    private volatile long lastLossSignalTime = 0;
    private final AtomicLong replayDuplicateCount = new AtomicLong();
    private final AtomicLong packetsFullyProcessed = new AtomicLong();

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
                long sfCount = totalStreamFramesDelivered.incrementAndGet();
                if (sfCount <= 20 || sfCount % 100 == 0) {
                    log.debug("[Protocol] StreamFrame: peer={} stream={} offset={} len={} fin={}",
                            conn.getPeerId(), streamFrame.getStreamId(), streamFrame.getOffset(),
                            streamFrame.getData().length, streamFrame.isFin());
                }
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
            if (lost.isEmpty()) continue;

            // Rate-limit retransmission to avoid overwhelming the receiver.
            // Cap at 50 packets per 50ms cycle = 1000 pps. This matches
            // the pacing rate for small packets (1 frame per ms) and prevents
            // the retransmit snowball effect where unlimited retransmissions
            // flood the network and create more losses.
            int maxRetransmitPerCycle = 50;

            int retransmitted = 0;

            for (SentPacket packet : lost) {
                if (packet.retransmitCount >= 30) {
                    // Too many retransmits, remove from tracking and close connection
                    conn.removeSentPacket(packet.packetNumber);
                    try {
                        close(conn.getPeerId(), ConnectionCloseFrame.INTERNAL_ERROR, "Too many retransmits");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    continue;
                }

                // Rate limit: leave remaining packets for the next cycle
                if (retransmitted >= maxRetransmitPerCycle) {
                    break;
                }

                // Respect congestion window: don't retransmit if we're already
                // over the window.  Leave the packet for the next cycle when
                // ACKs have freed up space.
                if (!conn.getCongestionControl().canSend(0)) {
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
                    // Remove old entry before retransmit (sendPacket creates new entry).
                    // If removeSentPacket returns null, the packet was already ACK'd
                    // between detectLostPackets() and now — skip the retransmission
                    // to avoid creating phantom entries in sentPackets.
                    SentPacket removed = conn.removeSentPacket(packet.packetNumber);
                    if (removed == null) {
                        // Already ACK'd — no need to retransmit
                        continue;
                    }
                    int inheritedRetransmitCount = removed.retransmitCount + 1;
                    try {
                        sendPacket(conn, framesToRetransmit, inheritedRetransmitCount);
                        conn.recordRetransmit();
                        retransmitted++;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Signal loss to congestion control at most once per second
            // to prevent the congestion window from collapsing to MIN_WINDOW.
            // The retransmit task runs every 50ms, and calling onLoss() each cycle
            // would reduce cwnd by 30% twenty times per second, reaching MIN_WINDOW
            // almost instantly.  A 1-second cooldown allows CUBIC time to recover.
            if (retransmitted > 0) {
                long now = System.currentTimeMillis();
                if (now - lastLossSignalTime > 1000) {
                    conn.getCongestionControl().onLoss();
                    lastLossSignalTime = now;
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
