package fudp.node;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fudp.connection.ConnectionState;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage for known peers.
 * Persists peer information to disk and manages address resolution.
 */
public class PeerBook {

    private final Path storageFile;
    private final Map<String, Peer> peers;
    private final Map<String, Peer> peersByAlias;
    private final long addressCacheTtl;
    private final Gson gson;

    public PeerBook(String dataDir, long addressCacheTtlMs, String localFid) {
        this.storageFile = Path.of(dataDir, localFid + "_peers.json");
        this.peers = new ConcurrentHashMap<>();
        this.peersByAlias = new ConcurrentHashMap<>();
        this.addressCacheTtl = addressCacheTtlMs;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        load();
    }

    /**
     * Add a peer with known address.
     */
    public void add(Peer peer) {
        peers.put(peer.getId(), peer);
        if (peer.getAlias() != null && !peer.getAlias().isEmpty()) {
            peersByAlias.put(peer.getAlias().toLowerCase(), peer);
        }
        save();
    }

    /**
     * Add peer with address.
     */
    public void addWithAddress(String peerId, byte[] publicKey, String host, int port) {
        Peer peer = new Peer(peerId, publicKey, host, port);
        add(peer);
    }

    /**
     * Remove a peer.
     */
    public void remove(String peerId) {
        Peer peer = peers.remove(peerId);
        if (peer != null && peer.getAlias() != null) {
            peersByAlias.remove(peer.getAlias().toLowerCase());
        }
        save();
    }

    /**
     * Get peer by FID.
     */
    public Peer get(String peerId) {
        return peers.get(peerId);
    }

    /**
     * Get peer by alias.
     */
    public Peer getByAlias(String alias) {
        return peersByAlias.get(alias.toLowerCase());
    }

    /**
     * Get peer by FID or alias.
     */
    public Peer getByIdOrAlias(String identifier) {
        Peer peer = peers.get(identifier);
        if (peer == null) {
            peer = getByAlias(identifier);
        }
        return peer;
    }

    /**
     * List all peers.
     */
    public List<Peer> list() {
        return new ArrayList<>(peers.values());
    }

    /**
     * Check if peer exists.
     */
    public boolean contains(String peerId) {
        return peers.containsKey(peerId);
    }

    /**
     * Update address from incoming connection.
     * Automatically adds peer if not exists.
     */
    public void updateFromConnection(String peerId, byte[] publicKey, SocketAddress address) {
        Peer peer = peers.get(peerId);
        if (address instanceof InetSocketAddress inet) {
            if (peer == null) {
                // Auto-add new peer with alias (last 4 chars of ID)
                Peer newPeer = new Peer(peerId, publicKey, inet.getHostString(), inet.getPort());
                String alias = peerId.length() > 4 ? peerId.substring(peerId.length() - 4) : peerId;
                newPeer.setAlias(alias);
                add(newPeer);
            } else {
                // Update existing
                boolean changed = false;
                if (!inet.getHostString().equals(peer.getHost())) {
                    peer.setHost(inet.getHostString());
                    changed = true;
                }
                if (inet.getPort() != peer.getPort()) {
                    peer.setPort(inet.getPort());
                    changed = true;
                }
                if (publicKey != null && peer.getPublicKey() == null) {
                    peer.setPublicKey(publicKey);
                    changed = true;
                }
                
                peer.setLastSeen(System.currentTimeMillis());
                peer.setState(ConnectionState.ESTABLISHED);
                
                if (changed) {
                    save();
                }
            }
        }
    }

    /**
     * Update peer state.
     */
    public void updateState(String peerId, ConnectionState state) {
        Peer peer = peers.get(peerId);
        if (peer != null) {
            peer.setState(state);
            if (state == ConnectionState.ESTABLISHED) {
                peer.setLastSeen(System.currentTimeMillis());
            }
        }
    }

    /**
     * Set alias for a peer.
     */
    public void setAlias(String peerId, String alias) {
        Peer peer = peers.get(peerId);
        if (peer != null) {
            // Remove old alias
            if (peer.getAlias() != null) {
                peersByAlias.remove(peer.getAlias().toLowerCase());
            }
            // Set new alias
            peer.setAlias(alias);
            if (alias != null && !alias.isEmpty()) {
                peersByAlias.put(alias.toLowerCase(), peer);
            }
            save();
        }
    }

    /**
     * Resolve address for a peer.
     */
    public SocketAddress resolveAddress(String peerId) throws IOException {
        Peer peer = peers.get(peerId);
        if (peer == null) {
            throw new IOException("Unknown peer: " + peerId);
        }

        if (!peer.hasAddress()) {
            throw new IOException("No address for peer: " + peerId);
        }

        return new InetSocketAddress(peer.getHost(), peer.getPort());
    }

    /**
     * Check if cached address is still valid.
     */
    public boolean isAddressCacheValid(String peerId) {
        Peer peer = peers.get(peerId);
        if (peer == null || !peer.hasAddress()) {
            return false;
        }
        return System.currentTimeMillis() - peer.getLastSeen() < addressCacheTtl;
    }

    /**
     * Save peers to disk.
     */
    public void save() {
        try {
            Files.createDirectories(storageFile.getParent());
            try (Writer writer = new FileWriter(storageFile.toFile())) {
                // Convert to list for JSON
                List<Peer> peerList = new ArrayList<>(peers.values());
                gson.toJson(peerList, writer);
            }
        } catch (IOException e) {
            // Log error but don't throw
            System.err.println("Failed to save peers: " + e.getMessage());
        }
    }

    /**
     * Load peers from disk.
     */
    public void load() {
        if (!Files.exists(storageFile)) {
            return;
        }

        try (Reader reader = new FileReader(storageFile.toFile())) {
            Type listType = new TypeToken<List<Peer>>() {}.getType();
            List<Peer> peerList = gson.fromJson(reader, listType);

            if (peerList != null) {
                for (Peer peer : peerList) {
                    peers.put(peer.getId(), peer);
                    if (peer.getAlias() != null && !peer.getAlias().isEmpty()) {
                        peersByAlias.put(peer.getAlias().toLowerCase(), peer);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load peers: " + e.getMessage());
        }
    }

    /**
     * Get number of peers.
     */
    public int size() {
        return peers.size();
    }

    /**
     * Clear all peers.
     */
    public void clear() {
        peers.clear();
        peersByAlias.clear();
        save();
    }
}
