package freeim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON file-based message persistence.
 * Stores messages per peer in ~/.freeim/{localFid}/messages/{peerId}.json
 */
public class MessageStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MSG_LIST_TYPE = new TypeToken<List<Message>>() {}.getType();

    private final Path baseDir;

    public MessageStore(Path baseDir) {
        this.baseDir = baseDir;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            System.err.println("Failed to create message store directory: " + e.getMessage());
        }
    }

    /**
     * Append a message and save to disk.
     */
    public synchronized void append(String peerId, Message msg) {
        List<Message> messages = load(peerId);
        messages.add(msg);
        save(peerId, messages);
    }

    /**
     * Mark a sent message as acked by messageId.
     */
    public synchronized boolean markAcked(String peerId, String messageId) {
        List<Message> messages = load(peerId);
        for (Message m : messages) {
            if (messageId.equals(m.id) && !m.incoming) {
                m.acked = true;
                save(peerId, messages);
                return true;
            }
        }
        return false;
    }

    /**
     * Load all messages for a peer.
     */
    public List<Message> load(String peerId) {
        Path file = baseDir.resolve(sanitize(peerId) + ".json");
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            String json = Files.readString(file);
            List<Message> list = GSON.fromJson(json, MSG_LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Failed to load messages for " + peerId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Load the last N messages for a peer.
     */
    public List<Message> loadRecent(String peerId, int count) {
        List<Message> all = load(peerId);
        if (all.size() <= count) return all;
        return new ArrayList<>(all.subList(all.size() - count, all.size()));
    }

    private void save(String peerId, List<Message> messages) {
        Path file = baseDir.resolve(sanitize(peerId) + ".json");
        try {
            Files.writeString(file, GSON.toJson(messages));
        } catch (IOException e) {
            System.err.println("Failed to save messages for " + peerId + ": " + e.getMessage());
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
