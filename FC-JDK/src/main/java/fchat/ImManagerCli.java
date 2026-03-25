package fchat;

import data.fcData.DeliveryMethod;
import data.fcData.DockItem;
import fapi.client.FapiClient;
import fudp.node.FudpNode;
import fudp.node.NodeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JsonUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight IM manager for the CLI chat client.
 * Handles sending, receiving, storing, and querying messages
 * using the same wire protocol as the Android Freer app.
 */
public class ImManagerCli implements NodeEventListener {
    private static final Logger log = LoggerFactory.getLogger(ImManagerCli.class);

    private final String liveFid;
    private final FudpNode fudpNode;
    private FapiClient fapiClient;

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, List<ImMessage>> messagesByConv = new ConcurrentHashMap<>();
    private final Map<String, ImMessage> pendingReceipts = new ConcurrentHashMap<>();

    private final Path dataDir;
    private MessageListener listener;

    public interface MessageListener {
        void onMessageReceived(ImMessage message);
        void onMessageSent(ImMessage message);
        void onStatusChanged(String messageId, MessageStatus status);
    }

    public ImManagerCli(String liveFid, FudpNode fudpNode, Path dataDir) {
        this.liveFid = liveFid;
        this.fudpNode = fudpNode;
        this.dataDir = dataDir;

        try {
            Files.createDirectories(dataDir.resolve("messages"));
        } catch (IOException e) {
            log.error("Failed to create data directory", e);
        }

        loadConversations();
        fudpNode.setEventListener(this);
    }

    public void setFapiClient(FapiClient fapiClient) {
        this.fapiClient = fapiClient;
    }

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    public String getLiveFid() {
        return liveFid;
    }

    // ========== Send ==========

    /**
     * Send a message. Assigns a globally unique ID, persists, then delivers
     * via FUDP -> ROAD -> DOCK cascade.
     */
    public boolean send(ImMessage message) {
        if (message == null) return false;

        if (message.getId() == null) {
            message.setIdFromLong(fudpNode.generateMessageId());
        }
        message.setSenderId(liveFid);

        boolean isReceipt = message.getContentType() == ContentType.RECEIPT;

        if (!isReceipt) {
            storeMessage(message);
            updateConversation(message);
        }

        boolean ok = deliver(message);
        if (ok) {
            message.setStatus(MessageStatus.SENT);
            if (!isReceipt) storeMessage(message);
            if (listener != null) listener.onMessageSent(message);
        } else {
            message.setStatus(MessageStatus.FAILED);
            if (!isReceipt) storeMessage(message);
        }
        return ok;
    }

    /**
     * Build CHAT content string from an ImMessage.
     * For TEXT: just the text content.
     * For RECEIPT: "read:originalMsgId" or "delivered:originalMsgId".
     */
    private String buildChatContent(ImMessage message) {
        if (message.getContentType() == ContentType.RECEIPT) {
            return message.getContent() + ":" + message.getRequestId();
        }
        return message.getContent();
    }

    private boolean shouldUseChatType(ImMessage message) {
        return message.getType() == ImType.P2P
                && (message.getContentType() == ContentType.TEXT
                    || message.getContentType() == ContentType.RECEIPT);
    }

    private boolean deliver(ImMessage message) {
        String targetFid = message.getTargetId();

        // 1. FUDP direct
        try {
            if (shouldUseChatType(message)) {
                long id = ImMessage.hexIdToLong(message.getId());
                String chatContent = buildChatContent(message);
                if (message.getContentType() == ContentType.RECEIPT) {
                    fudpNode.sendChat(targetFid, chatContent, id);
                } else {
                    fudpNode.sendChatWithAck(targetFid, chatContent, id);
                }
            } else {
                fudpNode.sendBytes(targetFid, message.toWireBytes());
            }
            message.setDeliveryMethod(DeliveryMethod.FUDP_DIRECT);
            if (message.getContentType() == ContentType.TEXT) trackForReceipt(message);
            return true;
        } catch (Exception e) {
            log.debug("FUDP direct failed to {}: {}", targetFid, e.getMessage());
        }

        byte[] wireData = message.toWireBytes();

        // 2. ROAD relay
        if (fapiClient != null) {
            try {
                FapiClient.RoadRelayResult result = fapiClient.roadRelay(targetFid, wireData);
                if (result != null && result.success()) {
                    message.setDeliveryMethod(DeliveryMethod.ROAD_RELAY);
                    trackForReceipt(message);
                    return true;
                }
            } catch (Exception e) {
                log.debug("ROAD relay failed: {}", e.getMessage());
            }
        }

        // 3. DOCK store
        if (fapiClient != null) {
            try {
                DockItem dockItem = fapiClient.dockPut(wireData, List.of(targetFid));
                if (dockItem != null && dockItem.getId() != null) {
                    message.setDockId(dockItem.getId());
                    message.setDeliveryMethod(DeliveryMethod.DOCK_STORED);
                    return true;
                }
            } catch (Exception e) {
                log.debug("DOCK store failed: {}", e.getMessage());
            }
        }

        log.warn("All delivery methods failed for message {} to {}", message.getId(), targetFid);
        return false;
    }

    private void trackForReceipt(ImMessage message) {
        if (message.getContentType() != ContentType.RECEIPT) {
            pendingReceipts.put(message.getId(), message);
        }
    }

    // ========== NodeEventListener callbacks ==========

    @Override
    public void onChatReceived(String peerId, long messageId, String chatContent) {
        log.debug("CHAT from {}: {}", peerId, chatContent);

        if (chatContent != null && (chatContent.startsWith("read:") || chatContent.startsWith("delivered:"))) {
            handleChatReceipt(chatContent);
            return;
        }

        ImMessage msg = ImMessage.createText(ImType.P2P, peerId, liveFid, chatContent);
        msg.setIdFromLong(messageId);
        msg.setDeliveryMethod(DeliveryMethod.FUDP_DIRECT);
        msg.setUnread(true);
        msg.setDeliveredAt(System.currentTimeMillis());
        storeMessage(msg);
        updateConversation(msg);
        if (listener != null) listener.onMessageReceived(msg);
    }

    @Override
    public void onChatAck(String peerId, long messageId, long rttMs) {
        String hexId = ImMessage.longIdToHex(messageId);
        log.debug("CHAT_ACK: id={}, peer={}, rtt={}ms", hexId, peerId, rttMs);
        updateStatus(hexId, MessageStatus.DELIVERED);
    }

    @Override
    public void onBytesReceived(String peerId, long messageId, int dataType, byte[] data) {
        try {
            ImMessage msg = ImMessage.fromWireBytes(data);
            if (msg == null) return;

            if (msg.getId() == null) msg.setIdFromLong(messageId);
            msg.setDeliveryMethod(DeliveryMethod.FUDP_DIRECT);

            if (msg.getContentType() == ContentType.RECEIPT) {
                handleReceipt(msg);
                return;
            }

            msg.setUnread(true);
            msg.setDeliveredAt(System.currentTimeMillis());
            storeMessage(msg);
            updateConversation(msg);
            if (listener != null) listener.onMessageReceived(msg);

            // Auto-send delivery receipt for P2P
            if (msg.getType() == ImType.P2P) {
                ImMessage receipt = ImMessage.createReceipt(liveFid, msg.getSenderId(), msg.getId(), false);
                new Thread(() -> send(receipt)).start();
            }
        } catch (Exception e) {
            log.warn("Failed to parse incoming bytes from {}: {}", peerId, e.getMessage());
        }
    }

    @Override
    public void onBytesAck(String peerId, long messageId, long rttMs) {
        String hexId = ImMessage.longIdToHex(messageId);
        log.debug("BYTES_ACK: id={}, peer={}, rtt={}ms", hexId, peerId, rttMs);
        updateStatus(hexId, MessageStatus.DELIVERED);
    }

    @Override
    public void onPeerConnected(String peerId) {
        log.info("Peer connected: {}", peerId);
    }

    @Override
    public void onPeerDisconnected(String peerId) {
        log.info("Peer disconnected: {}", peerId);
    }

    private void handleChatReceipt(String chatContent) {
        int idx = chatContent.indexOf(':');
        if (idx < 0) return;
        String type = chatContent.substring(0, idx);
        String origId = chatContent.substring(idx + 1);
        pendingReceipts.remove(origId);
        updateStatus(origId, "read".equals(type) ? MessageStatus.READ : MessageStatus.DELIVERED);
    }

    private void handleReceipt(ImMessage receiptMsg) {
        String origId = receiptMsg.getRequestId();
        if (origId == null) return;
        pendingReceipts.remove(origId);
        boolean isRead = "read".equals(receiptMsg.getContent());
        updateStatus(origId, isRead ? MessageStatus.READ : MessageStatus.DELIVERED);
    }

    private void updateStatus(String messageId, MessageStatus newStatus) {
        for (List<ImMessage> msgs : messagesByConv.values()) {
            for (ImMessage m : msgs) {
                if (messageId.equals(m.getId())) {
                    m.setStatus(newStatus);
                    if (newStatus == MessageStatus.DELIVERED) m.setDeliveredAt(System.currentTimeMillis());
                    if (newStatus == MessageStatus.READ) m.setReadAt(System.currentTimeMillis());
                    saveConvMessages(convKeyFor(m));
                    if (listener != null) listener.onStatusChanged(messageId, newStatus);
                    return;
                }
            }
        }
    }

    // ========== Query ==========

    public List<Conversation> getConversations() {
        List<Conversation> list = new ArrayList<>(conversations.values());
        list.sort((a, b) -> {
            long at = a.getLastActiveAt() != null ? a.getLastActiveAt() : 0;
            long bt = b.getLastActiveAt() != null ? b.getLastActiveAt() : 0;
            return Long.compare(bt, at);
        });
        return list;
    }

    public List<Conversation> getConversations(ImType type) {
        List<Conversation> all = getConversations();
        all.removeIf(c -> c.getType() != type);
        return all;
    }

    public List<ImMessage> getMessages(ImType type, String targetId, int limit) {
        String key = convKey(type, targetId);
        List<ImMessage> msgs = messagesByConv.get(key);
        if (msgs == null || msgs.isEmpty()) {
            msgs = loadConvMessages(key);
            messagesByConv.put(key, msgs);
        }
        List<ImMessage> display = new ArrayList<>();
        for (ImMessage m : msgs) {
            if (m.getContentType() != ContentType.RECEIPT) display.add(m);
        }
        if (limit > 0 && display.size() > limit) {
            return display.subList(display.size() - limit, display.size());
        }
        return display;
    }

    public void markAsRead(ImType type, String targetId) {
        String convId = type.name() + "_" + targetId;
        Conversation conv = conversations.get(convId);
        if (conv != null) {
            conv.markAsRead();
            saveConversations();
        }

        // Send read receipts for unread incoming P2P messages
        if (type == ImType.P2P) {
            String key = convKey(type, targetId);
            List<ImMessage> msgs = messagesByConv.get(key);
            if (msgs == null) return;
            for (ImMessage msg : msgs) {
                if (msg.getUnread() != null && msg.getUnread() && !msg.isOutgoing(liveFid)) {
                    msg.setUnread(false);
                    ImMessage receipt = ImMessage.createReceipt(liveFid, msg.getSenderId(), msg.getId(), true);
                    new Thread(() -> send(receipt)).start();
                }
            }
            saveConvMessages(key);
        }
    }

    // ========== Fetch from DOCK ==========

    public int fetchFromDock(ImType type, String targetId) {
        if (fapiClient == null) return 0;
        try {
            List<DockItem> items = fapiClient.dockList(null, targetId);
            if (items == null) return 0;
            int count = 0;
            for (DockItem item : items) {
                byte[] data = fapiClient.dockGet(item.getId(), targetId);
                if (data == null) continue;
                try {
                    ImMessage msg = ImMessage.fromWireBytes(data);
                    if (msg == null) continue;
                    msg.setDockId(item.getId());
                    msg.setDeliveryMethod(DeliveryMethod.DOCK_STORED);
                    msg.setUnread(!liveFid.equals(msg.getSenderId()));
                    storeMessage(msg);
                    updateConversation(msg);
                    count++;
                } catch (Exception e) {
                    log.debug("Failed to parse DOCK item {}: {}", item.getId(), e.getMessage());
                }
            }
            return count;
        } catch (Exception e) {
            log.warn("Failed to fetch from DOCK for {}: {}", targetId, e.getMessage());
            return 0;
        }
    }

    // ========== Storage ==========

    private String convKey(ImType type, String targetId) {
        if (type == ImType.P2P) {
            // Normalize: both directions map to same conversation
            return type.name() + "_" + targetId;
        }
        return type.name() + "_" + targetId;
    }

    private String convKeyFor(ImMessage msg) {
        String resolvedTarget = msg.getType() == ImType.P2P
                ? msg.getConversationPartnerId(liveFid) : msg.getTargetId();
        return convKey(msg.getType(), resolvedTarget);
    }

    private void storeMessage(ImMessage msg) {
        String key = convKeyFor(msg);
        List<ImMessage> msgs = messagesByConv.computeIfAbsent(key, k -> loadConvMessages(k));
        // Dedup by ID
        msgs.removeIf(m -> msg.getId() != null && msg.getId().equals(m.getId()));
        msgs.add(msg);
        msgs.sort((a, b) -> {
            long at = a.getTimestamp() != null ? a.getTimestamp() : 0;
            long bt = b.getTimestamp() != null ? b.getTimestamp() : 0;
            return Long.compare(at, bt);
        });
        saveConvMessages(key);
    }

    private void updateConversation(ImMessage msg) {
        String resolvedTarget = msg.getType() == ImType.P2P
                ? msg.getConversationPartnerId(liveFid) : msg.getTargetId();
        String convId = msg.getType().name() + "_" + resolvedTarget;
        Conversation conv = conversations.get(convId);
        if (conv == null) {
            conv = Conversation.fromMessage(msg, liveFid);
        } else {
            conv.updateWithMessage(msg);
            if (!msg.isOutgoing(liveFid)) conv.incrementUnread();
        }
        conversations.put(convId, conv);
        saveConversations();
    }

    private void saveConvMessages(String convKey) {
        List<ImMessage> msgs = messagesByConv.get(convKey);
        if (msgs == null) return;
        Path file = dataDir.resolve("messages").resolve(convKey + ".json");
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < msgs.size(); i++) {
                if (i > 0) sb.append(",\n");
                sb.append(msgs.get(i).toJson());
            }
            sb.append("]");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to save messages for {}: {}", convKey, e.getMessage());
        }
    }

    private List<ImMessage> loadConvMessages(String convKey) {
        Path file = dataDir.resolve("messages").resolve(convKey + ".json");
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            ImMessage[] arr = JsonUtils.fromJson(json, ImMessage[].class);
            return arr != null ? new ArrayList<>(Arrays.asList(arr)) : new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to load messages for {}: {}", convKey, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveConversations() {
        Path file = dataDir.resolve("conversations.json");
        try {
            Conversation[] arr = conversations.values().toArray(new Conversation[0]);
            Files.writeString(file, JsonUtils.toNiceJson(arr), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to save conversations: {}", e.getMessage());
        }
    }

    private void loadConversations() {
        Path file = dataDir.resolve("conversations.json");
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Conversation[] arr = JsonUtils.fromJson(json, Conversation[].class);
            if (arr != null) {
                for (Conversation c : arr) {
                    if (c.getId() != null) conversations.put(c.getId(), c);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load conversations: {}", e.getMessage());
        }
    }
}
