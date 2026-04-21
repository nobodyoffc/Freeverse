package freeim;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fudp.message.NotifyMessage;
import fudp.node.FudpNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat message send/receive logic using FUDP NOTIFY.
 *
 * Wire format: JSON via NOTIFY (dataType=1):
 *   {"type":"chat","text":"hello","ts":1711929600000}
 *   {"type":"ack","id":"<messageId>"}
 */
public class ChatManager {
    private static final Gson GSON = new Gson();

    private final FudpNode node;
    private final MessageStore store;
    private final String localFid;

    // In-memory cache of recent messages per peer
    private final Map<String, List<Message>> cache = new ConcurrentHashMap<>();

    // Listener for incoming messages (for live chat view)
    private volatile MessageListener listener;

    public interface MessageListener {
        void onMessage(Message msg);
    }

    public ChatManager(FudpNode node, MessageStore store, String localFid) {
        this.node = node;
        this.store = store;
        this.localFid = localFid;
    }

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    /**
     * Send a chat message to a peer.
     */
    public void send(String peerId, String text) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("type", "chat");
        json.addProperty("text", text);
        json.addProperty("ts", System.currentTimeMillis());

        byte[] data = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        long msgId = node.sendNotifyWithAck(peerId, data, NotifyMessage.DATA_TYPE_JSON);

        String hexId = Long.toHexString(msgId);
        Message msg = new Message(hexId, peerId, text, System.currentTimeMillis(), false);
        store.append(peerId, msg);
        addToCache(peerId, msg);
    }

    /**
     * Handle incoming NOTIFY message. Called from NodeEventListener.
     */
    public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);

        if (dataType == NotifyMessage.DATA_TYPE_JSON) {
            try {
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "chat";

                if ("ack".equals(type)) {
                    handleAck(peerId, json);
                    return;
                }

                // Chat message
                String text = json.has("text") ? json.get("text").getAsString() : content;
                receiveMessage(peerId, messageId, text);
                return;
            } catch (Exception e) {
                // Fall through to plain text
            }
        }

        // Plain text fallback
        receiveMessage(peerId, messageId, content);
    }

    private void receiveMessage(String peerId, long messageId, String text) {
        String hexId = Long.toHexString(messageId);
        Message msg = new Message(hexId, peerId, text, System.currentTimeMillis(), true);
        store.append(peerId, msg);
        addToCache(peerId, msg);

        // Notify live listener
        MessageListener l = listener;
        if (l != null) {
            l.onMessage(msg);
        } else {
            // No active chat view — print notification
            System.out.println("\n  [MSG] " + shortFid(peerId) + ": " + text);
        }

        // Send delivery ack
        sendAck(peerId, hexId);
    }

    private void handleAck(String peerId, JsonObject json) {
        String ackedId = json.has("id") ? json.get("id").getAsString() : null;
        if (ackedId == null) return;

        store.markAcked(peerId, ackedId);

        // Update cache
        List<Message> msgs = cache.get(peerId);
        if (msgs != null) {
            for (Message m : msgs) {
                if (ackedId.equals(m.id) && !m.incoming) {
                    m.acked = true;
                    break;
                }
            }
        }
    }

    /**
     * Handle NOTIFY_ACK (transport-level delivery confirmation).
     */
    public void onNotifyAck(String peerId, long messageId, long rttMs) {
        String hexId = Long.toHexString(messageId);
        store.markAcked(peerId, hexId);

        List<Message> msgs = cache.get(peerId);
        if (msgs != null) {
            for (Message m : msgs) {
                if (hexId.equals(m.id) && !m.incoming) {
                    m.acked = true;
                    break;
                }
            }
        }
    }

    private void sendAck(String peerId, String messageId) {
        try {
            JsonObject ack = new JsonObject();
            ack.addProperty("type", "ack");
            ack.addProperty("id", messageId);
            byte[] data = GSON.toJson(ack).getBytes(StandardCharsets.UTF_8);
            node.sendNotify(peerId, data, NotifyMessage.DATA_TYPE_JSON);
        } catch (Exception e) {
            // Best effort
        }
    }

    /**
     * Load recent messages for display.
     */
    public List<Message> getHistory(String peerId, int count) {
        return store.loadRecent(peerId, count);
    }

    /**
     * Interactive chat session with a peer.
     */
    public void chatSession(String peerId, BufferedReader br) {
        System.out.println("\n  --- Chat with " + shortFid(peerId) + " ---");
        System.out.println("  Type /back to return to menu\n");

        // Show recent history
        List<Message> history = getHistory(peerId, 20);
        for (Message m : history) {
            System.out.println("  " + m.format(localFid));
        }
        if (!history.isEmpty()) System.out.println();

        // Set live listener for this peer
        MessageListener oldListener = listener;
        setListener(msg -> {
            if (msg.peerId.equals(peerId) && msg.incoming) {
                System.out.println("  " + msg.format(localFid));
            }
        });

        try {
            while (true) {
                System.out.print("  > ");
                String line = br.readLine();
                if (line == null || "/back".equals(line.trim())) break;
                String text = line.trim();
                if (text.isEmpty()) continue;

                send(peerId, text);
                // Display sent message
                List<Message> recent = cache.get(peerId);
                if (recent != null && !recent.isEmpty()) {
                    System.out.println("  " + recent.get(recent.size() - 1).format(localFid));
                }
            }
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        } finally {
            setListener(oldListener);
        }
    }

    private void addToCache(String peerId, Message msg) {
        cache.computeIfAbsent(peerId, k -> store.loadRecent(peerId, 50)).add(msg);
    }

    private String shortFid(String fid) {
        if (fid == null) return "?";
        return fid.length() > 10 ? fid.substring(0, 6) + ".." + fid.substring(fid.length() - 4) : fid;
    }
}
