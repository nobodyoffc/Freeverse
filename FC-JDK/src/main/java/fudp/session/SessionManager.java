package fudp.session;

import fudp.util.ByteUtils;
import org.iq80.leveldb.*;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Session manager for FUDP symmetric key sessions with persistence
 *
 * Key negotiation flow:
 * 1. Proposer creates session with PROPOSED state
 * 2. Acceptor creates session with ACCEPTED state, sends ACK
 * 3. Proposer receives ACK, changes state to ACTIVE
 * 4. Acceptor receives first encrypted packet, changes state to ACTIVE
 *
 * Both parties use the same symmetric key for bidirectional communication.
 *
 * Performance optimization: Uses asynchronous batch writes to LevelDB to avoid
 * blocking on write operations, improving performance in high-concurrency scenarios.
 */
public class SessionManager {

    // In-memory cache
    private final Map<String, FudpSession> sessionsByKeyName;
    private final Map<String, List<FudpSession>> sessionsByPeerId;

    // Persistence - LevelDB
    private DB db;
    private final Object dbLock = new Object();

    // Asynchronous batch write queue
    private static class WriteOperation {
        enum Type { PUT, DELETE }
        final Type type;
        final String key;
        final byte[] value; // null for DELETE

        WriteOperation(Type type, String key, byte[] value) {
            this.type = type;
            this.key = key;
            this.value = value;
        }
    }

    private final BlockingQueue<WriteOperation> writeQueue;
    private final ExecutorService writeExecutor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private static final int BATCH_SIZE = 100;
    private static final long BATCH_TIMEOUT_MS = 50; // Flush batch after 50ms if not full

    // Cleanup
    private final ScheduledExecutorService scheduler;
    private static final long DEPRECATED_CLEANUP_DELAY = 60; // seconds

    // Performance statistics (optional)
    private final AtomicLong writeCount = new AtomicLong();
    private final AtomicLong batchWriteCount = new AtomicLong();
    private final AtomicLong totalWriteTime = new AtomicLong();

    public SessionManager(String dataDir, String localFid) {
        this.sessionsByKeyName = new ConcurrentHashMap<>();
        this.sessionsByPeerId = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.writeQueue = new LinkedBlockingQueue<>();
        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SessionManager-WriteThread");
            t.setDaemon(true);
            return t;
        });

        initializeDb(dataDir, localFid);
        loadSessions();
        startBatchWriteThread();
    }

    private void initializeDb(String dataDir, String localFid) {
        File dir = new File(dataDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String dbFolderName = localFid + "_fudp_sessions_leveldb";
        File dbFolder = new File(dir, dbFolderName);

        synchronized (dbLock) {
            try {
                Options options = new Options();
                options.createIfMissing(true);
                db = Iq80DBFactory.factory.open(dbFolder, options);
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize LevelDB for sessions", e);
            }
        }
    }

    private void loadSessions() {
        synchronized (dbLock) {
            try (DBIterator iterator = db.iterator()) {
                for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                    try {
                        byte[] valueBytes = iterator.peekNext().getValue();
                        FudpSession session = FudpSession.fromBytes(valueBytes);
                        // Cache it
                        sessionsByKeyName.put(session.getId(), session);
                        sessionsByPeerId.computeIfAbsent(session.getFid(), k -> new CopyOnWriteArrayList<>()).add(session);
                    } catch (Exception e) {
                        String keyStr = new String(iterator.peekNext().getKey(), StandardCharsets.UTF_8);
                        System.err.println("Failed to load session " + keyStr + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to iterate sessions: " + e.getMessage());
            }
        }
    }

    /**
     * Start the batch write thread for asynchronous LevelDB writes
     */
    private void startBatchWriteThread() {
        writeExecutor.submit(() -> {
            List<WriteOperation> batch = new ArrayList<>(BATCH_SIZE);
            long lastFlushTime = System.currentTimeMillis();

            while (running.get() || !writeQueue.isEmpty()) {
                try {
                    // Drain available operations, with timeout
                    WriteOperation op = writeQueue.poll(BATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (op != null) {
                        batch.add(op);
                    }

                    long now = System.currentTimeMillis();
                    boolean shouldFlush = batch.size() >= BATCH_SIZE ||
                            (!batch.isEmpty() && (now - lastFlushTime >= BATCH_TIMEOUT_MS)) ||
                            (!running.get() && !writeQueue.isEmpty());

                    if (shouldFlush && !batch.isEmpty()) {
                        flushBatch(batch);
                        batch.clear();
                        lastFlushTime = now;
                    }

                    // If we got an operation but batch is not full, continue collecting
                    if (op != null && batch.size() < BATCH_SIZE && running.get()) {
                        // Drain more operations without blocking
                        writeQueue.drainTo(batch, BATCH_SIZE - batch.size());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error in batch write thread: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Final flush on shutdown
            if (!batch.isEmpty()) {
                flushBatch(batch);
            }
        });
    }

    /**
     * Flush a batch of write operations to LevelDB
     */
    private void flushBatch(List<WriteOperation> batch) {
        if (batch.isEmpty()) return;

        long startTime = System.nanoTime();
        synchronized (dbLock) {
            try {
                WriteBatch writeBatch = db.createWriteBatch();
                try {
                    for (WriteOperation op : batch) {
                        byte[] keyBytes = op.key.getBytes(StandardCharsets.UTF_8);
                        if (op.type == WriteOperation.Type.PUT) {
                            writeBatch.put(keyBytes, op.value);
                        } else {
                            writeBatch.delete(keyBytes);
                        }
                    }
                    db.write(writeBatch);
                    batchWriteCount.incrementAndGet();
                } finally {
                    writeBatch.close();
                }
            } catch (Exception e) {
                System.err.println("Failed to flush batch to LevelDB: " + e.getMessage());
                e.printStackTrace();
            }
        }
        long duration = System.nanoTime() - startTime;
        totalWriteTime.addAndGet(duration);
    }

    /**
     * Persist a session asynchronously (non-blocking)
     */
    private void persistSession(FudpSession session) {
        byte[] key = session.getId().getBytes(StandardCharsets.UTF_8);
        byte[] value = session.toBytes();
        WriteOperation op = new WriteOperation(WriteOperation.Type.PUT, session.getId(), value);
        
        if (!writeQueue.offer(op)) {
            // Queue is full, fall back to synchronous write (should rarely happen)
            synchronized (dbLock) {
                try {
                    db.put(key, value);
                } catch (Exception e) {
                    System.err.println("Failed to write session synchronously: " + e.getMessage());
                }
            }
        } else {
            writeCount.incrementAndGet();
        }
    }

    /**
     * Delete a session from DB asynchronously (non-blocking)
     */
    private void deleteSessionFromDb(String keyNameHex) {
        WriteOperation op = new WriteOperation(WriteOperation.Type.DELETE, keyNameHex, null);
        
        if (!writeQueue.offer(op)) {
            // Queue is full, fall back to synchronous delete (should rarely happen)
            synchronized (dbLock) {
                try {
                    byte[] key = keyNameHex.getBytes(StandardCharsets.UTF_8);
                    db.delete(key);
                } catch (Exception e) {
                    System.err.println("Failed to delete session synchronously: " + e.getMessage());
                }
            }
        } else {
            writeCount.incrementAndGet();
        }
    }

    /**
     * Add a session I proposed (sent SYMKEY_PROPOSAL)
     * State: PROPOSED
     */
    public FudpSession addProposedSession(byte[] symkey, String peerId, String peerPubkey) {
        FudpSession session = new FudpSession(symkey, peerId, peerPubkey);
        session.setState(KeyState.PROPOSED);

        cacheAndPersist(session);
        return session;
    }

    /**
     * Add a session I accepted (received SYMKEY_PROPOSAL)
     * State: ACCEPTED
     */
    public FudpSession addAcceptedSession(byte[] symkey, String peerId, String peerPubkey) {
        FudpSession session = new FudpSession(symkey, peerId, peerPubkey);
        session.setState(KeyState.ACCEPTED);

        cacheAndPersist(session);
        return session;
    }

    private void cacheAndPersist(FudpSession session) {
        sessionsByKeyName.put(session.getId(), session);
        sessionsByPeerId.computeIfAbsent(session.getFid(), k -> new CopyOnWriteArrayList<>()).add(session);
        persistSession(session);
    }

    /**
     * Activate a session (received SYMKEY_ACK for my proposal)
     * PROPOSED → ACTIVE
     */
    public void activateProposedSession(byte[] keyName) {
        String keyNameHex = ByteUtils.toHex(keyName);
        FudpSession session = sessionsByKeyName.get(keyNameHex);

        if (session != null && session.getState() == KeyState.PROPOSED) {
            // Deprecate existing active sessions for this peer
            deprecateActiveSessions(session.getFid());

            // Activate the new session
            session.setState(KeyState.ACTIVE);
            persistSession(session);
        }
    }

    /**
     * Activate an accepted session (received first encrypted packet)
     * ACCEPTED → ACTIVE
     */
    public void activateAcceptedSession(byte[] keyName) {
        String keyNameHex = ByteUtils.toHex(keyName);
        FudpSession session = sessionsByKeyName.get(keyNameHex);

        if (session != null && session.getState() == KeyState.ACCEPTED) {
            // Deprecate existing active sessions for this peer
            deprecateActiveSessions(session.getFid());

            // Activate the new session
            session.setState(KeyState.ACTIVE);
            persistSession(session);
        }
    }

    /**
     * Deprecate all active sessions for a peer
     */
    private void deprecateActiveSessions(String peerId) {
        List<FudpSession> sessions = sessionsByPeerId.get(peerId);
        if (sessions == null) return;

        for (FudpSession session : sessions) {
            if (session.getState() == KeyState.ACTIVE) {
                session.setState(KeyState.DEPRECATED);
                persistSession(session);

                // Schedule removal after delay
                scheduleSessionRemoval(session.getId(), DEPRECATED_CLEANUP_DELAY);
            }
        }
    }

    /**
     * Get session by key name
     */
    public FudpSession getByKeyName(byte[] keyName) {
        String keyNameHex = ByteUtils.toHex(keyName);
        return sessionsByKeyName.get(keyNameHex);
    }

    /**
     * Get active session for a peer (for encryption)
     * Returns ACTIVE session first, then ACCEPTED session as fallback
     */
    public FudpSession getActiveSession(String peerId) {
        List<FudpSession> sessions = sessionsByPeerId.get(peerId);
        if (sessions == null) return null;

        // First try to find ACTIVE session
        FudpSession active = sessions.stream()
                .filter(s -> s.getState() == KeyState.ACTIVE)
                .findFirst()
                .orElse(null);

        if (active != null) return active;

        // Fallback to ACCEPTED session (acceptor can use this for encryption)
        return sessions.stream()
                .filter(s -> s.getState() == KeyState.ACCEPTED)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get any valid session for decryption
     * Priority: ACTIVE > ACCEPTED > PROPOSED > DEPRECATED
     */
    public FudpSession getSessionForDecryption(String peerId, byte[] keyName) {
        // First try to find by keyName
        FudpSession session = getByKeyName(keyName);
        if (session != null) {
            return session;
        }

        // Fallback: find any session for the peer
        List<FudpSession> sessions = sessionsByPeerId.get(peerId);
        if (sessions == null) return null;

        // Sort by state priority
        return sessions.stream()
                .sorted(Comparator.comparingInt(s -> getStatePriority(s.getState())))
                .findFirst()
                .orElse(null);
    }

    private int getStatePriority(KeyState state) {
        return switch (state) {
            case ACTIVE -> 0;
            case ACCEPTED -> 1;
            case PROPOSED -> 2;
            case DEPRECATED -> 3;
        };
    }

    /**
     * Check if we have a PROPOSED session waiting for ACK
     */
    public FudpSession getProposedSession(String peerId) {
        List<FudpSession> sessions = sessionsByPeerId.get(peerId);
        if (sessions == null) return null;

        return sessions.stream()
                .filter(s -> s.getState() == KeyState.PROPOSED)
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if a session exists for a peer
     */
    public boolean hasSession(String peerId) {
        return sessionsByPeerId.containsKey(peerId) && !sessionsByPeerId.get(peerId).isEmpty();
    }

    /**
     * Check if we have an active or accepted session
     */
    public boolean hasUsableSession(String peerId) {
        List<FudpSession> sessions = sessionsByPeerId.get(peerId);
        if (sessions == null) return false;

        return sessions.stream()
                .anyMatch(s -> s.getState() == KeyState.ACTIVE || s.getState() == KeyState.ACCEPTED);
    }

    /**
     * Schedule session removal
     */
    private void scheduleSessionRemoval(String keyNameHex, long delaySeconds) {
        scheduler.schedule(() -> removeSession(keyNameHex), delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Remove a session
     */
    public void removeSession(String keyNameHex) {
        FudpSession session = sessionsByKeyName.remove(keyNameHex);
        if (session != null) {
            List<FudpSession> peerSessions = sessionsByPeerId.get(session.getFid());
            if (peerSessions != null) {
                peerSessions.remove(session);
                if (peerSessions.isEmpty()) {
                    sessionsByPeerId.remove(session.getFid());
                }
            }
            deleteSessionFromDb(keyNameHex);
            session.clear();
        }
    }

    /**
     * Remove all sessions for a peer
     */
    public void removeSessionsForPeer(String peerId) {
        List<FudpSession> sessions = sessionsByPeerId.remove(peerId);
        if (sessions != null) {
            for (FudpSession session : sessions) {
                sessionsByKeyName.remove(session.getId());
                deleteSessionFromDb(session.getId());
                session.clear();
            }
        }
    }

    /**
     * Get all sessions for a peer
     */
    public List<FudpSession> getSessionsForPeer(String peerId) {
        List<FudpSession> sessions = sessionsByPeerId.get(peerId);
        return sessions != null ? new ArrayList<>(sessions) : Collections.emptyList();
    }

    /**
     * Get all active sessions
     */
    public List<FudpSession> getAllActiveSessions() {
        List<FudpSession> active = new ArrayList<>();
        for (List<FudpSession> sessions : sessionsByPeerId.values()) {
            for (FudpSession session : sessions) {
                if (session.getState() == KeyState.ACTIVE) {
                    active.add(session);
                }
            }
        }
        return active;
    }

    /**
     * Shutdown the session manager
     */
    public void shutdown() {
        running.set(false);
        scheduler.shutdown();

        // Wait for write queue to be flushed
        try {
            writeExecutor.shutdown();
            if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Write executor did not terminate in time, forcing shutdown");
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        synchronized (dbLock) {
            if (db != null) {
                try {
                    db.close();
                } catch (IOException e) {
                    System.err.println("Failed to close LevelDB: " + e.getMessage());
                }
            }
        }

        // Clear all sessions
        for (FudpSession session : sessionsByKeyName.values()) {
            session.clear();
        }
        sessionsByKeyName.clear();
        sessionsByPeerId.clear();
    }

    /**
     * Get statistics
     */
    public Map<String, Integer> getStats() {
        Map<String, Integer> stats = new HashMap<>();
        int total = 0, proposed = 0, accepted = 0, active = 0, deprecated = 0;

        for (FudpSession session : sessionsByKeyName.values()) {
            total++;
            switch (session.getState()) {
                case PROPOSED -> proposed++;
                case ACCEPTED -> accepted++;
                case ACTIVE -> active++;
                case DEPRECATED -> deprecated++;
            }
        }

        stats.put("total", total);
        stats.put("proposed", proposed);
        stats.put("accepted", accepted);
        stats.put("active", active);
        stats.put("deprecated", deprecated);
        stats.put("peers", sessionsByPeerId.size());

        return stats;
    }

    /**
     * Get performance statistics
     * @return Map containing performance metrics
     */
    public Map<String, Long> getPerformanceStats() {
        Map<String, Long> perfStats = new HashMap<>();
        perfStats.put("writeOperations", writeCount.get());
        perfStats.put("batchWrites", batchWriteCount.get());
        perfStats.put("pendingWrites", (long) writeQueue.size());
        perfStats.put("totalWriteTimeNs", totalWriteTime.get());
        
        long avgWriteTime = batchWriteCount.get() > 0 
            ? totalWriteTime.get() / batchWriteCount.get() 
            : 0;
        perfStats.put("avgBatchWriteTimeNs", avgWriteTime);
        
        return perfStats;
    }
}
