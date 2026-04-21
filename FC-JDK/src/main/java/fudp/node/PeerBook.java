package fudp.node;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fudp.connection.ConnectionState;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetAddress;
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
     * If the peer already exists, adds the address as an additional endpoint
     * instead of overwriting the existing one.
     * Resolves hostname to IP address to avoid DNS dependency on reconnect.
     */
    public void addWithAddress(String peerId, byte[] publicKey, String host, int port) {
        String resolvedHost = resolveToIp(host);
        Peer existing = peers.get(peerId);
        if (existing != null) {
            // Peer exists: add as additional endpoint
            boolean added = existing.addEndpoint(resolvedHost, port);
            if (publicKey != null && existing.getPublicKey() == null) {
                existing.setPublicKey(publicKey);
            }
            if (added) {
                save();
            }
        } else {
            // New peer
            Peer peer = new Peer(peerId, publicKey, resolvedHost, port);
            add(peer);
        }
    }

    /**
     * Resolve a hostname to its IP address. If already an IP or resolution fails, returns the original.
     */
    private static String resolveToIp(String host) {
        if (host == null || host.isEmpty()) return host;
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.getHostAddress();
        } catch (Exception e) {
            return host;
        }
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
     * Always stores the resolved IP address (not hostname) to avoid DNS dependency on reconnect.
     */
    public void updateFromConnection(String peerId, byte[] publicKey, SocketAddress address) {
        Peer peer = peers.get(peerId);
        if (address instanceof InetSocketAddress inet) {
            // Always use resolved IP address, not hostname, to avoid DNS issues on reconnect
            String hostAddr = (inet.getAddress() != null)
                    ? inet.getAddress().getHostAddress()
                    : inet.getHostString();
            int port = inet.getPort();

            if (peer == null) {
                // Auto-add new peer with alias (last 4 chars of ID)
                Peer newPeer = new Peer(peerId, publicKey, hostAddr, port);
                String alias = peerId.length() > 4 ? peerId.substring(peerId.length() - 4) : peerId;
                newPeer.setAlias(alias);
                add(newPeer);
            } else {
                // Update existing: add as endpoint (doesn't overwrite existing endpoints)
                boolean changed = peer.addEndpoint(hostAddr, port);
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
                boolean needsSave = false;
                for (Peer peer : peerList) {
                    // Resolve any stored domain names to IPs
                    if (peer.getHost() != null && !peer.getHost().isEmpty()) {
                        String resolved = resolveToIp(peer.getHost());
                        if (!resolved.equals(peer.getHost())) {
                            peer.setHost(resolved);
                            needsSave = true;
                        }
                    }
                    peers.put(peer.getId(), peer);
                    if (peer.getAlias() != null && !peer.getAlias().isEmpty()) {
                        peersByAlias.put(peer.getAlias().toLowerCase(), peer);
                    }
                }
                if (needsSave) {
                    save();
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
