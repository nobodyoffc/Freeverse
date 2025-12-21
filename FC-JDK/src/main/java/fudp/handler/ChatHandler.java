package fudp.handler;

import fudp.message.ChatAckMessage;
import fudp.message.ChatMessage;
import fudp.node.NodeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler for chat messages.
 */
public class ChatHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);
    
    /** Default max age for received message IDs (1 hour) */
    private static final long DEFAULT_MESSAGE_ID_MAX_AGE_MS = 3600_000L;
    
    /** Default max age for sent ACK cache (5 minutes) */
    private static final long DEFAULT_ACK_CACHE_MAX_AGE_MS = 300_000L;

    private final NodeEventListener eventListener;
    
    /** Pending ACKs: messageId -> PendingMessage (for sent messages awaiting ACK) */
    private final Map<Long, PendingMessage> pendingAcks;
    
    /**
     * Holds a pending message with its send timestamp for RTT calculation.
     */
    public static class PendingMessage {
        private final ChatMessage message;
        private final long sendTimeMs;
        
        public PendingMessage(ChatMessage message, long sendTimeMs) {
            this.message = message;
            this.sendTimeMs = sendTimeMs;
        }
        
        public ChatMessage getMessage() {
            return message;
        }
        
        public long getSendTimeMs() {
            return sendTimeMs;
        }
    }
    
    /** 
     * Received message IDs with timestamp: (peerId + messageId) -> receiveTimestamp (for deduplication).
     * Key format: "peerId:messageId" to ensure uniqueness across different peers.
     */
    private final Map<String, Long> receivedMessageIds;
    
    /** 
     * Sent ACK cache: (peerId + messageId) -> sendTimestamp (to avoid sending duplicate ACKs).
     * Key format: "peerId:messageId"
     */
    private final Map<String, Long> sentAckCache;
    
    /** 
     * Received ACK cache: (peerId + messageId) -> receiveTimestamp (to avoid processing duplicate ACKs).
     * Key format: "peerId:messageId"
     */
    private final Map<String, Long> receivedAckCache;

    public ChatHandler(NodeEventListener eventListener) {
        this.eventListener = eventListener;
        this.pendingAcks = new ConcurrentHashMap<>();
        this.receivedMessageIds = new ConcurrentHashMap<>();
        this.sentAckCache = new ConcurrentHashMap<>();
        this.receivedAckCache = new ConcurrentHashMap<>();
    }

    /**
     * Handle incoming chat message.
     * Note: Deduplication is now handled atomically in FudpNode.handleIncomingData
     * using tryMarkAsProcessed(). This method is called only for new messages.
     */
    public void handleChat(String peerId, ChatMessage message) {
        // Deduplication is already done in FudpNode.handleIncomingData via tryMarkAsProcessed()
        // The message ID is already in receivedMessageIds, so we just notify the listener
        
        // Notify listener
        if (eventListener != null) {
            eventListener.onChatReceived(peerId, message.getMessageId(), message.getContent());
        }
    }

    /**
     * Handle chat acknowledgment.
     */
    public void handleChatAck(String peerId, ChatAckMessage ack) {
        PendingMessage pending = pendingAcks.remove(ack.getAckedMessageId());
        if (pending != null) {
            // Calculate RTT (round-trip time)
            long rttMs = System.currentTimeMillis() - pending.getSendTimeMs();
            
            // Message was delivered - notify with RTT
            if (eventListener != null) {
                eventListener.onChatAck(peerId, ack.getAckedMessageId(), rttMs);
            }
        }
    }

    /**
     * Register a sent message that expects ACK.
     * Records the current timestamp for RTT calculation.
     */
    public void registerPendingAck(ChatMessage message) {
        if (message.hasFlag(ChatMessage.FLAG_NEED_ACK)) {
            pendingAcks.put(message.getMessageId(), new PendingMessage(message, System.currentTimeMillis()));
        }
    }

    /**
     * Create acknowledgment for a message.
     */
    public ChatAckMessage createAck(long messageId) {
        return new ChatAckMessage(messageId);
    }

    /**
     * Build a composite key for deduplication: "peerId:messageId".
     * This ensures messages from different peers don't conflict.
     */
    private static String buildKey(String peerId, long messageId) {
        return peerId + ":" + messageId;
    }

    /**
     * Check if a message has already been processed (for deduplication).
     * This is a read-only check, does not mark the message as processed.
     * @param peerId the peer ID
     * @param messageId the message ID to check
     * @return true if the message was already processed
     */
    public boolean isMessageProcessed(String peerId, long messageId) {
        return receivedMessageIds.containsKey(buildKey(peerId, messageId));
    }

    /**
     * Atomically check and mark a message as processed.
     * This prevents race conditions when multiple threads process the same message.
     * Uses composite key (peerId + messageId) to avoid conflicts between different peers.
     * 
     * @param peerId the peer ID who sent the message
     * @param messageId the message ID to check and mark
     * @return true if this is a NEW message (was not already processed), false if duplicate
     */
    public boolean tryMarkAsProcessed(String peerId, long messageId) {
        long now = System.currentTimeMillis();
        String key = buildKey(peerId, messageId);
        // putIfAbsent returns null if key was not present (new message)
        return receivedMessageIds.putIfAbsent(key, now) == null;
    }

    /**
     * Check if ACK for this message has already been sent.
     * If not sent, marks it as sent.
     * Uses composite key (peerId + messageId) to avoid conflicts.
     * 
     * @param peerId the peer ID
     * @param messageId the message ID to check
     * @return true if ACK should be sent (first time), false if already sent
     */
    public boolean tryMarkAckSent(String peerId, long messageId) {
        long now = System.currentTimeMillis();
        String key = buildKey(peerId, messageId);
        // putIfAbsent returns null if key was not present (ACK not sent yet)
        return sentAckCache.putIfAbsent(key, now) == null;
    }

    /**
     * Check if ACK for this message has already been sent.
     * @param peerId the peer ID
     * @param messageId the message ID to check
     * @return true if ACK was already sent
     */
    public boolean isAckSent(String peerId, long messageId) {
        return sentAckCache.containsKey(buildKey(peerId, messageId));
    }
    
    /**
     * Atomically check and mark a received ACK as processed.
     * Prevents duplicate processing and logging of the same ACK in multi-threaded scenarios.
     * Uses composite key (peerId + messageId) to avoid conflicts.
     * 
     * @param peerId the peer ID who sent the ACK
     * @param messageId the message ID in the ACK
     * @return true if this is a NEW ACK (was not already processed), false if duplicate
     */
    public boolean tryMarkAckReceived(String peerId, long messageId) {
        long now = System.currentTimeMillis();
        String key = buildKey(peerId, messageId);
        // putIfAbsent returns null if key was not present (new ACK)
        return receivedAckCache.putIfAbsent(key, now) == null;
    }

    /**
     * Clear old received message IDs and ACK caches (for memory management).
     * Should be called periodically.
     * @param maxAgeMs maximum age in milliseconds for entries to keep
     */
    public void cleanupOldMessages(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        
        // Cleanup received message IDs
        int beforeReceived = receivedMessageIds.size();
        receivedMessageIds.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        int afterReceived = receivedMessageIds.size();
        
        // Cleanup ACK caches (use shorter TTL)
        long ackCutoff = System.currentTimeMillis() - DEFAULT_ACK_CACHE_MAX_AGE_MS;
        int beforeSentAck = sentAckCache.size();
        sentAckCache.entrySet().removeIf(entry -> entry.getValue() < ackCutoff);
        int afterSentAck = sentAckCache.size();
        
        int beforeReceivedAck = receivedAckCache.size();
        receivedAckCache.entrySet().removeIf(entry -> entry.getValue() < ackCutoff);
        int afterReceivedAck = receivedAckCache.size();
        
        if (beforeReceived != afterReceived || beforeSentAck != afterSentAck || beforeReceivedAck != afterReceivedAck) {
            log.debug("ChatHandler cleanup: receivedMsgs {} -> {}, sentAcks {} -> {}, receivedAcks {} -> {}", 
                    beforeReceived, afterReceived, beforeSentAck, afterSentAck, beforeReceivedAck, afterReceivedAck);
        }
    }

    /**
     * Cleanup with default max age.
     */
    public void cleanupOldMessages() {
        cleanupOldMessages(DEFAULT_MESSAGE_ID_MAX_AGE_MS);
    }

    /**
     * Cleanup expired pending ACKs (for messages that never received ACK).
     * @param timeoutMs timeout in milliseconds
     */
    public void cleanupExpiredPendingAcks(long timeoutMs) {
        // Note: pendingAcks doesn't store timestamp, so we can't do time-based cleanup
        // For now, just log the count for monitoring
        int count = pendingAcks.size();
        if (count > 100) {
            log.warn("ChatHandler has {} pending ACKs, may indicate connectivity issues", count);
        }
    }

    /**
     * Get pending ACK count.
     */
    public int getPendingAckCount() {
        return pendingAcks.size();
    }
    
    /**
     * Get received message ID count (for monitoring).
     */
    public int getReceivedMessageIdCount() {
        return receivedMessageIds.size();
    }
    
    /**
     * Get sent ACK cache size (for monitoring).
     */
    public int getSentAckCacheSize() {
        return sentAckCache.size();
    }
}
