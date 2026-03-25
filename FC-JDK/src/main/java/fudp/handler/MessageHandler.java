package fudp.handler;

import fudp.message.*;
import fudp.node.NodeEventListener;
import fudp.metrics.MeterDirection;
import fudp.metrics.MeterRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


/**
 * Routes incoming messages to appropriate handlers.
 * Integrates balance management for REQUEST/RESPONSE messages.
 */
public class MessageHandler {
    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);

    /**
     * Interface for sending messages (to avoid circular dependency with FudpNode)
     */
    public interface MessageSender {
        void sendMessage(String peerId, AppMessage message) throws IOException;
    }

    private final NodeEventListener eventListener;
    private final Map<Long, CompletableFuture<ResponseMessage>> pendingRequests;
    private final Map<Long, CompletableFuture<PongMessage>> pendingPongs;
    private final ChatHandler chatHandler;
    private final Consumer<MeterRecord> meterSink;
    private MessageSender messageSender;
    
    public MessageHandler(NodeEventListener eventListener, MessageSender messageSender, Consumer<MeterRecord> meterSink) {
        this.eventListener = eventListener;
        this.pendingRequests = new ConcurrentHashMap<>();
        this.pendingPongs = new ConcurrentHashMap<>();
        this.chatHandler = new ChatHandler(eventListener);
        this.messageSender = messageSender;
        this.meterSink = meterSink;
    }
    
    /**
     * Set message sender (called by FudpNode after initialization)
     */
    public void setMessageSender(MessageSender messageSender) {
        this.messageSender = messageSender;
    }
    
    /**
     * Handle incoming data from a peer.
     */
    public void handleIncomingData(String peerId, byte[] data) {
        try {
            AppMessage message = MessageCodec.decode(data);
            emitMeter(peerId, message.getType(), data.length, MeterDirection.INBOUND);
            routeMessage(peerId, message);
        } catch (IllegalArgumentException e) {
            // Invalid message
            if (eventListener != null) {
                eventListener.onError(peerId, 2002, "Invalid message: " + e.getMessage());
            }
        } catch (Exception e) {
            // Unexpected error
            if (eventListener != null) {
                eventListener.onError(peerId, 1, "Error handling message: " + e.getMessage());
            }
        }
    }

    private void emitMeter(String peerId, MessageType type, int payloadBytes, MeterDirection direction) {
        if (meterSink == null) return;
        try {
            meterSink.accept(MeterRecord.builder()
                    .peerId(peerId)
                    .messageType(type)
                    .direction(direction)
                    .payloadBytes(payloadBytes)
                    .receiveTimestampMillis(System.currentTimeMillis())
                    .sendTimestampMillis(0)
                    .retransmitCount(0)
                    .build());
        } catch (Exception ignored) {
            // keep transport path resilient
        }
    }

    /**
     * Route message to appropriate handler.
     */
    private void routeMessage(String peerId, AppMessage message) {
        switch (message.getType()) {
            case CHAT -> chatHandler.handleChat(peerId, (ChatMessage) message);

            case CHAT_ACK -> chatHandler.handleChatAck(peerId, (ChatAckMessage) message);

            case REQUEST -> handleRequest(peerId, (RequestMessage) message);

            case RESPONSE -> handleResponse(peerId, (ResponseMessage) message);

            case ERROR -> handleError(peerId, (ErrorMessage) message);

            case PING -> handlePing(peerId, (PingMessage) message);

            case PONG -> handlePong(peerId, (PongMessage) message);

            default -> {
                // Unhandled message type
                if (eventListener != null) {
                    eventListener.onError(peerId, 2001, "Unhandled message type: " + message.getType());
                }
            }
        }
    }

    /**
     * Handle incoming request (as provider).
     */
    private void handleRequest(String peerId, RequestMessage request) {
        if (eventListener != null) {
            eventListener.onRequestReceived(
                    peerId,
                    request.getMessageId(),
                    request.getSid(),
                    request.getData()
            );
        }
    }

    /**
     * Handle incoming response (as consumer).
     */
    private void handleResponse(String peerId, ResponseMessage response) {
        long responseId = response.getMessageId();
        CompletableFuture<ResponseMessage> future = pendingRequests.remove(responseId);
        if (future != null) {
            log.debug("[MessageHandler] Response matched pending request (peer={}, messageId={}, status={}, dataLen={})",
                    peerId, responseId, response.getStatusCode(),
                    response.getData() != null ? response.getData().length : 0);
            future.complete(response);
        } else {
            log.warn("[MessageHandler] Response has NO matching pending request (peer={}, messageId={}, pending={})",
                    peerId, responseId, pendingRequests.keySet());
        }
    }

    /**
     * Handle error message.
     */
    private void handleError(String peerId, ErrorMessage error) {
        int errorCode = error.getErrorCode();
        String errorMessage = error.getErrorMessage();
        
        // Log error with context
        log.error("Error received from peer {}: code={}, message={}", 
            peerId, errorCode, errorMessage);
        
        // Complete any pending request with error
        CompletableFuture<ResponseMessage> future = pendingRequests.remove(error.getMessageId());
        if (future != null) {
            RuntimeException exception = new RuntimeException(
                String.format("Error %d: %s", errorCode, errorMessage)
            );
            future.completeExceptionally(exception);
        }

        if (eventListener != null) {
            eventListener.onError(peerId, errorCode, errorMessage);
        }
    }

    /**
     * Handle ping message.
     */
    private void handlePing(String peerId, PingMessage ping) {
        // Pong is sent by FudpNode
    }

    /**
     * Handle pong message.
     */
    private void handlePong(String peerId, PongMessage pong) {
        long rtt = pong.calculateRtt();
        if (eventListener != null) {
            eventListener.onPingComplete(peerId, rtt);
            byte[] data = pong.getData();
            if (data != null && data.length > 0) {
                eventListener.onPongInfo(peerId, data);
            }
        }

        CompletableFuture<PongMessage> future = pendingPongs.remove(pong.getMessageId());
        if (future != null && !future.isDone()) {
            future.complete(pong);
        }
    }

    /**
     * Register a pending request.
     */
    public void registerPendingRequest(long messageId, CompletableFuture<ResponseMessage> future) {
        pendingRequests.put(messageId, future);
        log.debug("[MessageHandler] Registered pending request (messageId={}, totalPending={})",
                messageId, pendingRequests.size());
    }

    /**
     * Cancel a pending request.
     */
    public void cancelPendingRequest(long messageId) {
        CompletableFuture<ResponseMessage> future = pendingRequests.remove(messageId);
        if (future != null) {
            future.cancel(true);
        }
    }

    /**
     * Get the chat handler.
     */
    public ChatHandler getChatHandler() {
        return chatHandler;
    }

    /**
     * Get pending request count.
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }

    /**
     * Await a pong matching the given messageId.
     */
    public CompletableFuture<PongMessage> awaitPong(long messageId) {
        CompletableFuture<PongMessage> future = new CompletableFuture<>();
        pendingPongs.put(messageId, future);
        return future;
    }
    
    /**
     * Cancel waiting for a pong.
     */
    public void cancelPong(long messageId) {
        CompletableFuture<PongMessage> f = pendingPongs.remove(messageId);
        if (f != null) {
            f.cancel(true);
        }
    }
    
    /**
     * Send response (as provider).
     */
    public void sendResponse(String peerId, long requestId, int statusCode, byte[] data) throws IOException {
        if (messageSender == null) {
            throw new IllegalStateException("MessageSender not set");
        }
        
        ResponseMessage response = new ResponseMessage(requestId, statusCode, data);
        messageSender.sendMessage(peerId, response);
    }
}
