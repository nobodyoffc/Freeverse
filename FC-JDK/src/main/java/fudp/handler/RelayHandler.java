package fudp.handler;

import fudp.message.*;
import fudp.node.NodeEventListener;
import fudp.node.Peer;
import fudp.node.PeerBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles relay message routing and tracking.
 * 
 * Supports two modes:
 * 1. Anonymous relay: target never learns sender identity (privacy-preserving)
 * 2. Session-based relay: bidirectional routing for protocols like file transfer
 *    - Sender reveals identity via senderFid
 *    - Session groups related messages (offer, accept, chunks)
 *    - Relay maintains bidirectional routing for entire session
 */
public class RelayHandler {
    private static final Logger log = LoggerFactory.getLogger(RelayHandler.class);

    /** Maximum hop count for relay messages */
    public static final int MAX_HOP_COUNT = 5;
    
    /** Maximum payload size for relay messages (64KB) */
    public static final int MAX_RELAY_PAYLOAD = 64 * 1024;
    
    /** Time-to-live for pending relay entries (60 seconds) */
    public static final long PENDING_RELAY_TTL_MS = 60_000;
    
    /** Time-to-live for relay sessions (5 minutes for file transfers) */
    public static final long SESSION_TTL_MS = 5 * 60_000;

    private final String localFid;
    private final PeerBook peerBook;
    private final NodeEventListener listener;
    private final MessageSender messageSender;
    
    /** Map of (targetFid:msgId) -> PendingRelay for anonymous relays */
    private final Map<String, PendingRelay> pendingRelays = new ConcurrentHashMap<>();
    
    /** Map of messageId -> PendingRelayAck for tracking outbound relays */
    private final Map<Long, PendingRelayAck> pendingRelayAcks = new ConcurrentHashMap<>();
    
    /** Map of sessionId -> RelaySession for bidirectional routing */
    private final Map<Long, RelaySession> relaySessions = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicLong relayedMessageCount = new AtomicLong();
    private final AtomicLong relayFailureCount = new AtomicLong();
    private final AtomicLong relayAckCount = new AtomicLong();
    private final AtomicLong sessionCount = new AtomicLong();
    
    private ScheduledFuture<?> cleanupTask;

    /**
     * Interface for sending messages.
     */
    @FunctionalInterface
    public interface MessageSender {
        void send(String peerId, AppMessage message) throws IOException;
    }

    /**
     * Pending relay entry for routing ACKs back (anonymous relays).
     */
    private static class PendingRelay {
        final String originPeerId;
        final long timestamp;

        PendingRelay(String originPeerId) {
            this.originPeerId = originPeerId;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > PENDING_RELAY_TTL_MS;
        }
    }

    /**
     * Pending relay ACK for tracking outbound relay messages.
     */
    private static class PendingRelayAck {
        final long sendTime;

        PendingRelayAck() {
            this.sendTime = System.currentTimeMillis();
        }
    }

    /**
     * Relay session for bidirectional routing (file transfers).
     * Maps between two peers through the relay.
     */
    private static class RelaySession {
        final String peer1Fid;
        final String peer1PeerId;
        final String peer2Fid;
        final String peer2PeerId;
        final long timestamp;

        RelaySession(String peer1Fid, String peer1PeerId, String peer2Fid, String peer2PeerId) {
            this.peer1Fid = peer1Fid;
            this.peer1PeerId = peer1PeerId;
            this.peer2Fid = peer2Fid;
            this.peer2PeerId = peer2PeerId;
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * Get the peer ID to forward to, given the sender.
         */
        String getForwardPeerId(String senderFid) {
            if (peer1Fid.equals(senderFid)) {
                return peer2PeerId;
            } else if (peer2Fid.equals(senderFid)) {
                return peer1PeerId;
            }
            return null;
        }

        /**
         * Get the target FID for the other peer.
         */
        String getOtherFid(String senderFid) {
            if (peer1Fid.equals(senderFid)) {
                return peer2Fid;
            } else if (peer2Fid.equals(senderFid)) {
                return peer1Fid;
            }
            return null;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > SESSION_TTL_MS;
        }
    }

    public RelayHandler(String localFid, PeerBook peerBook, NodeEventListener listener, MessageSender messageSender) {
        this.localFid = localFid;
        this.peerBook = peerBook;
        this.listener = listener;
        this.messageSender = messageSender;
    }

    /**
     * Start periodic cleanup of expired entries.
     */
    public void startCleanup(ScheduledExecutorService scheduler) {
        cleanupTask = scheduler.scheduleAtFixedRate(
                this::cleanupExpired,
                PENDING_RELAY_TTL_MS,
                PENDING_RELAY_TTL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Stop cleanup task.
     */
    public void stopCleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel(true);
            cleanupTask = null;
        }
    }

    /**
     * Handle incoming relay message.
     */
    public void handleRelayMessage(String fromPeerId, RelayMessage message) {
        long msgId = message.getMessageId();
        String targetFid = message.getTargetFid();
        
        log.debug("[RelayHandler] Received relay from {} for target {}, hopCount={}, session={}", 
                fromPeerId, targetFid, message.getHopCount(), message.getSessionId());

        // Validate hop count
        if (message.getHopCount() <= 0) {
            sendRelayFail(fromPeerId, msgId, RelayErrorCode.ERR_HOP_LIMIT, 
                    "Hop count exceeded");
            return;
        }

        // Validate payload size
        if (!message.isPayloadValid()) {
            sendRelayFail(fromPeerId, msgId, RelayErrorCode.ERR_PAYLOAD_TOO_LARGE,
                    "Payload exceeds " + MAX_RELAY_PAYLOAD + " bytes");
            return;
        }

        // Check if we are the target
        if (localFid.equals(targetFid)) {
            // Deliver to local application
            deliverToLocal(fromPeerId, message);
            return;
        }

        // Check for session-based routing
        if (message.hasSessionId()) {
            forwardSessionRelay(fromPeerId, message);
        } else {
            // Anonymous relay
            forwardRelay(fromPeerId, message);
        }
    }

    /**
     * Deliver relayed message to local application.
     */
    private void deliverToLocal(String relayPeerId, RelayMessage relayMessage) {
        long msgId = relayMessage.getMessageId();
        
        try {
            // Decode inner payload
            AppMessage innerMessage = MessageCodec.decode(relayMessage.getInnerPayload());
            
            // Notify listener with sender FID if available
            if (listener != null) {
                if (relayMessage.hasSenderFid()) {
                    // Identified relay - sender revealed identity
                    listener.onRelayedMessageReceived(relayPeerId, relayMessage.getSenderFid(), 
                            relayMessage.getSessionId(), innerMessage);
                } else {
                    // Anonymous relay
                    listener.onRelayedMessageReceived(relayPeerId, innerMessage);
                }
            }
            
            // Send ACK back
            RelayAckMessage ack = new RelayAckMessage(msgId);
            messageSender.send(relayPeerId, ack);
            
            log.debug("[RelayHandler] Delivered relay message {} locally, sent ACK", msgId);
            
        } catch (Exception e) {
            log.warn("[RelayHandler] Failed to deliver relay message {}: {}", msgId, e.getMessage());
            sendRelayFail(relayPeerId, msgId, RelayErrorCode.ERR_DELIVERY_FAILED, e.getMessage());
        }
    }

    /**
     * Forward session-based relay message (bidirectional routing).
     */
    private void forwardSessionRelay(String fromPeerId, RelayMessage message) {
        String targetFid = message.getTargetFid();
        String senderFid = message.getSenderFid();
        long sessionId = message.getSessionId();
        long msgId = message.getMessageId();

        // Get or create session
        RelaySession session = relaySessions.get(sessionId);
        
        if (session == null) {
            // New session - create bidirectional mapping
            if (!message.hasSenderFid()) {
                log.warn("[RelayHandler] Session relay requires sender FID");
                sendRelayFail(fromPeerId, msgId, RelayErrorCode.ERR_DELIVERY_FAILED,
                        "Session relay requires sender identity");
                return;
            }
            
            // Check if target is known
            Peer target = peerBook.getByIdOrAlias(targetFid);
            if (target == null) {
                log.warn("[RelayHandler] Unknown target FID: {}", targetFid);
                sendRelayFail(fromPeerId, msgId, RelayErrorCode.ERR_UNKNOWN_TARGET,
                        "Unknown target: " + targetFid);
                relayFailureCount.incrementAndGet();
                return;
            }
            
            // Create session
            session = new RelaySession(senderFid, fromPeerId, targetFid, target.getId());
            relaySessions.put(sessionId, session);
            sessionCount.incrementAndGet();
            log.debug("[RelayHandler] Created relay session {} between {} and {}", 
                    sessionId, senderFid, targetFid);
        }
        
        // Forward based on session
        String forwardPeerId = session.getForwardPeerId(senderFid != null ? senderFid : targetFid);
        if (forwardPeerId == null) {
            // Try reverse direction (response from target)
            forwardPeerId = session.getForwardPeerId(targetFid);
        }
        
        if (forwardPeerId == null) {
            log.warn("[RelayHandler] Cannot determine forward peer for session {}", sessionId);
            sendRelayFail(fromPeerId, msgId, RelayErrorCode.ERR_DELIVERY_FAILED,
                    "Invalid session routing");
            return;
        }

        // Store routing info for ACK return path
        String key = targetFid + ":" + msgId;
        pendingRelays.put(key, new PendingRelay(fromPeerId));

        try {
            // Decrement hop count and forward
            message.decrementHopCount();
            messageSender.send(forwardPeerId, message);
            
            relayedMessageCount.incrementAndGet();
            log.debug("[RelayHandler] Forwarded session relay {} to {}", msgId, forwardPeerId);
            
        } catch (IOException e) {
            log.warn("[RelayHandler] Failed to forward session relay {} to {}: {}", 
                    msgId, forwardPeerId, e.getMessage());
            pendingRelays.remove(key);
            sendRelayFail(fromPeerId, msgId, RelayErrorCode.ERR_DELIVERY_FAILED, e.getMessage());
            relayFailureCount.incrementAndGet();
        }
    }

    /**
     * Forward anonymous relay message to target.
     */
    private void forwardRelay(String fromPeerId, RelayMessage message) {
        String targetFid = message.getTargetFid();
        long msgId = message.getMessageId();

        // Check if target is known
        Peer target = peerBook.getByIdOrAlias(targetFid);
        if (target == null) {
            log.warn("[RelayHandler] Unknown target FID: {}", targetFid);
            sendRelayFail(fromPeerId, msgId, RelayErrorCode.ERR_UNKNOWN_TARGET,
                    "Unknown target: " + targetFid);
            relayFailureCount.incrementAndGet();
            return;
        }

        // Store routing info for ACK return path
        String key = targetFid + ":" + msgId;
        pendingRelays.put(key, new PendingRelay(fromPeerId));

        try {
            // Decrement hop count and forward
            message.decrementHopCount();
            messageSender.send(target.getId(), message);
            
            relayedMessageCount.incrementAndGet();
            log.debug("[RelayHandler] Forwarded relay {} to {}, hopCount={}", 
                    msgId, targetFid, message.getHopCount());
            
        } catch (IOException e) {
            log.warn("[RelayHandler] Failed to forward relay {} to {}: {}", 
                    msgId, targetFid, e.getMessage());
            pendingRelays.remove(key);
            sendRelayFail(fromPeerId, msgId, RelayErrorCode.ERR_DELIVERY_FAILED, e.getMessage());
            relayFailureCount.incrementAndGet();
        }
    }

    /**
     * Handle relay acknowledgment.
     */
    public void handleRelayAck(String fromPeerId, RelayAckMessage ack) {
        long originalMsgId = ack.getOriginalMessageId();
        
        // Check if this is a response to our outbound relay
        PendingRelayAck pendingAck = pendingRelayAcks.remove(originalMsgId);
        if (pendingAck != null) {
            long rttMs = System.currentTimeMillis() - pendingAck.sendTime;
            relayAckCount.incrementAndGet();
            if (listener != null) {
                listener.onRelayAck(originalMsgId, rttMs);
            }
            log.debug("[RelayHandler] Relay ACK received for {}, RTT={}ms", originalMsgId, rttMs);
            return;
        }

        // Look up origin to forward ACK
        String key = fromPeerId + ":" + originalMsgId;
        // Try with sender's FID first, then scan all
        PendingRelay pending = pendingRelays.remove(key);
        
        if (pending == null) {
            // Try to find by message ID (scan)
            for (Map.Entry<String, PendingRelay> entry : pendingRelays.entrySet()) {
                if (entry.getKey().endsWith(":" + originalMsgId)) {
                    pending = pendingRelays.remove(entry.getKey());
                    break;
                }
            }
        }

        if (pending != null) {
            try {
                messageSender.send(pending.originPeerId, ack);
                relayAckCount.incrementAndGet();
                log.debug("[RelayHandler] Forwarded relay ACK {} to {}", 
                        originalMsgId, pending.originPeerId);
            } catch (IOException e) {
                log.warn("[RelayHandler] Failed to forward relay ACK {} to {}: {}", 
                        originalMsgId, pending.originPeerId, e.getMessage());
            }
        } else {
            log.debug("[RelayHandler] No pending relay found for ACK {}", originalMsgId);
        }
    }

    /**
     * Handle relay failure.
     */
    public void handleRelayFail(String fromPeerId, RelayFailMessage fail) {
        long originalMsgId = fail.getOriginalMessageId();
        
        // Check if this is a response to our outbound relay
        PendingRelayAck pendingAck = pendingRelayAcks.remove(originalMsgId);
        if (pendingAck != null) {
            relayFailureCount.incrementAndGet();
            if (listener != null) {
                listener.onRelayFailed(originalMsgId, fail.getErrorCode(), fail.getErrorMessage());
            }
            log.debug("[RelayHandler] Relay FAIL received for {}: {} - {}", 
                    originalMsgId, fail.getErrorCode(), fail.getErrorMessage());
            return;
        }

        // Look up origin to forward failure
        String key = fromPeerId + ":" + originalMsgId;
        PendingRelay pending = pendingRelays.remove(key);
        
        if (pending == null) {
            // Try to find by message ID (scan)
            for (Map.Entry<String, PendingRelay> entry : pendingRelays.entrySet()) {
                if (entry.getKey().endsWith(":" + originalMsgId)) {
                    pending = pendingRelays.remove(entry.getKey());
                    break;
                }
            }
        }

        if (pending != null) {
            try {
                messageSender.send(pending.originPeerId, fail);
                relayFailureCount.incrementAndGet();
                log.debug("[RelayHandler] Forwarded relay FAIL {} to {}", 
                        originalMsgId, pending.originPeerId);
            } catch (IOException e) {
                log.warn("[RelayHandler] Failed to forward relay FAIL {} to {}: {}", 
                        originalMsgId, pending.originPeerId, e.getMessage());
            }
        } else {
            log.debug("[RelayHandler] No pending relay found for FAIL {}", originalMsgId);
        }
    }

    /**
     * Register a pending outbound relay for ACK tracking.
     */
    public void registerPendingRelayAck(long messageId) {
        pendingRelayAcks.put(messageId, new PendingRelayAck());
    }

    /**
     * Send relay failure message.
     */
    private void sendRelayFail(String peerId, long originalMsgId, int errorCode, String errorMessage) {
        try {
            RelayFailMessage fail = new RelayFailMessage(originalMsgId, errorCode, errorMessage);
            messageSender.send(peerId, fail);
        } catch (IOException e) {
            log.warn("[RelayHandler] Failed to send relay fail to {}: {}", peerId, e.getMessage());
        }
    }

    /**
     * Cleanup expired pending relay entries and sessions.
     */
    public void cleanupExpired() {
        int removed = 0;
        Iterator<Map.Entry<String, PendingRelay>> it = pendingRelays.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        
        // Cleanup expired pending ACKs
        long now = System.currentTimeMillis();
        pendingRelayAcks.entrySet().removeIf(e -> 
                now - e.getValue().sendTime > PENDING_RELAY_TTL_MS);
        
        // Cleanup expired sessions
        int sessionRemoved = 0;
        Iterator<Map.Entry<Long, RelaySession>> sessionIt = relaySessions.entrySet().iterator();
        while (sessionIt.hasNext()) {
            if (sessionIt.next().getValue().isExpired()) {
                sessionIt.remove();
                sessionRemoved++;
            }
        }
        
        if (removed > 0 || sessionRemoved > 0) {
            log.debug("[RelayHandler] Cleaned up {} expired pending relays, {} sessions", 
                    removed, sessionRemoved);
        }
    }

    /**
     * Get relay statistics.
     */
    public RelayStats getStats() {
        return new RelayStats(
                relayedMessageCount.get(),
                relayFailureCount.get(),
                relayAckCount.get(),
                pendingRelays.size(),
                relaySessions.size()
        );
    }

    /**
     * Relay statistics.
     */
    public record RelayStats(
            long relayedMessages,
            long failures,
            long acks,
            int pendingCount,
            int activeSessions
    ) {
        @Override
        public String toString() {
            return String.format("RelayStats{relayed=%d, failures=%d, acks=%d, pending=%d, sessions=%d}",
                    relayedMessages, failures, acks, pendingCount, activeSessions);
        }
    }
}
