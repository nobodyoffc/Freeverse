# FUDP Encryption Mode Improvement

## Context
- Current flow: first outbound packets are AsyTwoWay (0-RTT) while a `SYMKEY_PROPOSAL` is sent opportunistically. Symmetric sessions are kept in memory only.
- When a peer restarts, its sessions vanish. The other side keeps sending with the old `keyName`, gets `SESSION_NOT_FOUND`, removes its sessions, and retransmits pending frames. Retransmissions are encrypted with AsyTwoWay only and no new proposal is attached, so the link can stay in AsyTwoWay indefinitely.
- `forceAsyTwoWay` latches on when AsyTwoWay traffic arrives while an ACTIVE session exists and is only cleared by seeing a symkey packet/proposal, which prolongs the fallback.

## Issue Confirmation
- In `Protocol.handleIncomingPacket` the SESSION_NOT_FOUND path clears sessions and sets the connection to `IDLE` but does not trigger a fresh proposal; subsequent retransmits reuse the original frames and are re-encrypted as AsyTwoWay (Protocol.java:456-474, 611-623).
- `PacketCrypto.encryptPacket` chooses AsyTwoWay whenever no ACTIVE session or `forceAsyTwoWay` is set (PacketCrypto.java:31-58), and retransmission paths never append a new `SYMKEY_PROPOSAL`.
- Result: after a peer restart, traffic may run permanently in AsyTwoWay until an application-level reconnect happens.

## Review of Proposed Plan
- 👍 A per-peer `encryptMode` (SYMKEY default, ASYTWOWAY opt-in) clarifies intent and allows explicit Asy-only peers.
- 👍 SYMKEY-only data and clean key-state transitions (ACTIVE → DEPRECATED on new proposals) reduce ambiguity.
- Gaps to address:
  - Who triggers re-negotiation after `SESSION_NOT_FOUND`/decrypt failure? Needs an automatic proposer, not just a mode flag, with QUIC-like anti-loop backoff.
  - Retransmit flow must attach or precede a new `SYMKEY_PROPOSAL`; otherwise old frames keep being sent as AsyTwoWay.
  - Forcing SYM→ASY on any AsyTwoWay packet risks a permanent downgrade from a single fallback burst; should treat it as “peer restart, re-key” instead of a mode change unless the peer is explicitly Asy-only.
  - Error handling should distinguish “no key yet” vs “peer prefers Asy-only” to avoid flip-flopping modes.
  - Proposal generation must be deterministic per attempt (QUIC Initial keys are stable); retries must reuse the same key material/keyName instead of regenerating.
  - Queue/backpressure semantics must be explicit: which frame types bypass the queue, how long to buffer, and what errors callers see (QUIC disallows app data before handshake keys).

## Type Definitions

### EncryptMode Enum
```java
package fudp.crypto;

/**
 * Encryption mode preference for a peer connection.
 */
public enum EncryptMode {
    /**
     * Prefer symmetric key encryption (default).
     * - App data (STREAM frames) are queued until SYMKEY_ACK is received (1-RTT semantics).
     * - Control frames (ACK, CONNECTION_CLOSE, etc.) always bypass the queue.
     * - Automatically triggers SYMKEY_PROPOSAL when needed.
     */
    PreferSymkey,
    
    /**
     * Asymmetric-only mode.
     * - All traffic uses AsyTwoWay encryption (0-RTT semantics).
     * - Rejects SYMKEY_PROPOSAL with ERROR_ASY_ONLY.
     * - No queueing, all frames sent immediately.
     */
    AsyOnly
}
```

### CryptoState Enum
```java
package fudp.connection;

/**
 * Runtime cryptographic state for a peer connection.
 * This state is independent of ConnectionState and KeyState.
 */
public enum CryptoState {
    /**
     * Negotiating symmetric key.
     * - Has an outstanding SYMKEY_PROPOSAL waiting for ACK.
     * - App data frames are queued.
     */
    Negotiating,
    
    /**
     * Symmetric key is active and ready to use.
     * - Can send app data immediately.
     * - Queue is flushed and ready for new data.
     */
    SymkeyActive,
    
    /**
     * Temporary bridge state (peer likely restarted).
     * - Received AsyTwoWay packet while ACTIVE session exists.
     * - Has a TTL (3-5 seconds).
     * - Will exit to Negotiating if TTL expires.
     */
    AsyBridge
}
```

### OutstandingProposal Class
```java
package fudp.connection;

/**
 * Tracks an outstanding symmetric key proposal.
 * Ensures deterministic key reuse across retries (QUIC Initial key stability).
 */
public static class OutstandingProposal {
    public final byte[] keyName;           // SHA256(symkey)[0:6]
    public final byte[] symkeyBytes;       // The actual symmetric key (32 bytes)
    public final long sendTime;             // First send timestamp
    public volatile int retryCount = 0;     // Number of retries (max 5)
    public volatile long nextRetry = 0;     // Next retry timestamp
    
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 200;
    private static final long MAX_BACKOFF_MS = 5000;
    
    public OutstandingProposal(byte[] keyName, byte[] symkeyBytes) {
        this.keyName = keyName;
        this.symkeyBytes = symkeyBytes;
        this.sendTime = System.currentTimeMillis();
        this.nextRetry = sendTime + INITIAL_BACKOFF_MS;
    }
    
    /**
     * Check if proposal should be retried now.
     */
    public boolean shouldRetry(long now) {
        return now >= nextRetry && retryCount < MAX_RETRIES;
    }
    
    /**
     * Schedule next retry with exponential backoff.
     */
    public void scheduleNextRetry(long rtt) {
        retryCount++;
        long backoff = (long) (INITIAL_BACKOFF_MS * Math.pow(2, retryCount - 1));
        backoff = Math.min(backoff, MAX_BACKOFF_MS);
        nextRetry = System.currentTimeMillis() + backoff;
    }
    
    /**
     * Check if proposal has exceeded max retries.
     */
    public boolean isExhausted() {
        return retryCount >= MAX_RETRIES;
    }
}
```

### Error Classes
```java
package fudp.connection;

/**
 * Thrown when app data queue is full.
 */
public static class QueueFullException extends IOException {
    public QueueFullException(String message) {
        super(message);
    }
}

/**
 * Thrown when key negotiation times out.
 */
public static class NegotiationTimeoutException extends IOException {
    public NegotiationTimeoutException(String message) {
        super(message);
    }
}
```

## Suggested Refinement (with clarifications)
1. **Per-peer policy (replaces the `forceAsyTwoWay` latch entirely)**  
   - Persist `encryptMode = PreferSymkey (default) | AsyOnly` in peer config/PeerBook (plus Node-level default/CLI override).  
   - With this split, `forceAsyTwoWay` is no longer needed; temporary downgrades are represented by cryptoState=AsyBridge with TTL.

2. **Explicit state machine and placement**  
   - Per-peer `cryptoState` lives alongside `ConnectionState`: `Negotiating(keyName?) → SymkeyActive(keyName) → AsyBridge ↔ Negotiating/SymkeyActive`.  
   - `KeyState` remains PROPOSED/ACTIVE/DEPRECATED in SessionManager; `cryptoState` decides which session to use.  
   - Suggested placement: `PeerConnection` fields `cryptoState`, `asyBridgeUntil`, `outstandingProposal`, `appDataQueue`. ASCII view:  
     ```
     ConnectionState: IDLE → ESTABLISHING → ESTABLISHED
     cryptoState:     Negotiating → SymkeyActive → AsyBridge → Negotiating/SymkeyActive
     KeyState:        PROPOSED → ACTIVE → DEPRECATED
     ```
   - **Concurrency safety**: Use `volatile` for `cryptoState` and `asyBridgeUntil`; use `synchronized` blocks for state transitions; use `ConcurrentLinkedQueue` for `appDataQueue` (lock-free).

3. **PreferSymkey vs AsyOnly (0-RTT / 1-RTT semantics)**  
   - PreferSymkey is 1-RTT for app data: do not send STREAM frames until SYMKEY_ACK; queue them and flush on activation or fail on timeout. Control frames always allowed.  
   - AsyOnly is 0-RTT: all traffic uses AsyTwoWay; symkey proposals are rejected with ASY_ONLY unless operator allows downgrade.

4. **Behavior matrix (self = local mode/state)**  
   - PreferSymkey + SymkeyActive: Symkey pkt → decrypt; AsyTwoWay pkt → enter AsyBridge, deprecate session, trigger proposal; SYMKEY_PROPOSAL → deprecate ACTIVE, accept+ACK; SESSION_NOT_FOUND → mark stale, send proposal.  
   - PreferSymkey + Negotiating/AsyBridge: SYMKEY_ACK(mine) → SymkeyActive; peer proposal → pubkey-order to pick winner; AsyTwoWay data → stay AsyBridge, ensure proposal; SESSION_NOT_FOUND → ensure proposal + state Negotiating.  
   - AsyOnly: AsyTwoWay → normal; Symkey/SYMKEY_PROPOSAL → respond ASY_ONLY and drop (or downgrade if explicitly allowed).

5. **AsyBridge TTL and exit rules**  
   - Enter: PreferSymkey with ACTIVE session receives AsyTwoWay data lacking symkey context (peer likely restarted).  
   - TTL: short, configurable (3–5s, default 5s). Check in `Protocol.sendPacket()` before encryption; if expired, exit to Negotiating and send exactly one fresh proposal.  
   - Exit early on first valid symkey packet/ACK → SymkeyActive. No mode change is implied.
   - **Implementation**: 
     ```java
     // In Protocol.handleIncomingPacket(), when receiving AsyTwoWay packet
     if (packet.getUsedKeyName() == null && // Received AsyTwoWay
         !hasSymkeyProposal &&              // No proposal in this packet
         sessionManager.getActiveSession(senderId) != null) { // Has ACTIVE session
         synchronized (conn) {
             if (conn.getCryptoState() == CryptoState.SymkeyActive) {
                 // Enter AsyBridge
                 conn.setCryptoState(CryptoState.AsyBridge);
                 conn.setAsyBridgeUntil(System.currentTimeMillis() + 5000); // 5s TTL
                 // Deprecate current session and trigger new proposal
                 FudpSession activeSession = sessionManager.getActiveSession(senderId);
                 if (activeSession != null) {
                     sessionManager.deprecateSession(activeSession.getId());
                 }
                 ensureSymkeyProposal(conn);
             }
         }
     }
     
     // In Protocol.sendPacket(), before encryption
     if (conn.getCryptoState() == CryptoState.AsyBridge) {
         conn.checkAsyBridgeTTL(); // Checks and exits if expired
     }
     
     // In PeerConnection.checkAsyBridgeTTL()
     synchronized void checkAsyBridgeTTL() {
         if (cryptoState == CryptoState.AsyBridge && 
             System.currentTimeMillis() > asyBridgeUntil) {
             exitAsyBridge();
         }
     }
     
     synchronized void exitAsyBridge() {
         if (cryptoState == CryptoState.AsyBridge) {
             cryptoState = CryptoState.Negotiating;
             asyBridgeUntil = 0;
             // Trigger proposal if not already outstanding
             if (outstandingProposal == null) {
                 ensureSymkeyProposal(); // Called from Protocol
             }
         }
     }
     ```

6. **Automatic (re)proposal triggers**  
   - Decrypt failure / missing session in PreferSymkey: send SESSION_NOT_FOUND, clear stale sessions, set cryptoState=Negotiating, queue proposal immediately (not deferred to retransmitTask). Rate-limit with a token bucket: 1 immediate retry, then exponential backoff (start 200–300ms, cap 5s) to prevent oscillation, following QUIC’s handshake/PTO backoff spirit.  
   - Receipt of SESSION_NOT_FOUND: same helper `ensureSymkeyProposal` clears non-PROPOSED, sets Negotiating, (re)sends proposal under the same backoff window.  
   - Outbound app send while not SymkeyActive in PreferSymkey: enqueue STREAM, auto-trigger proposal (HELLO/public-key first if needed, rate-limited).  
   - Dual-init: keep pubkey-order rule—higher pubkey wins, loser drops its proposal and ACKs the winner.

7. **Proposal reliability (independent of app retransmit)**  
   - Track `outstandingProposal { keyName, symkeyBytes, sendTime, retryCount, nextRetry }` per peer and reuse the same `symkeyBytes`/keyName for all retries (mirrors QUIC Initial key stability).  
   - Retransmit in `retransmitTask` with exponential backoff (base 200–300ms, max ~5 retries) until SYMKEY_ACK; cancel on ACK or mode change to AsyOnly.  
   - Optional piggyback: first flush after negotiation may carry proposal if still unacked, but standalone resend is preferred for correctness.
   - **Coordination with packet retransmit**: In `retransmitTask`, check `outstandingProposal` first; if it needs retry, handle proposal retransmission before processing lost packets to avoid duplicate work.
   - **Implementation details**:
     ```java
     // In Protocol.retransmitTask()
     private void retransmitTask() {
         for (PeerConnection conn : connectionManager.getAllConnections()) {
             // 1. Priority: Handle proposal retransmission first
             OutstandingProposal proposal = conn.getOutstandingProposal();
             if (proposal != null && proposal.shouldRetry(System.currentTimeMillis())) {
                 try {
                     // Re-send proposal using the same keyName and symkeyBytes
                     sendSymkeyProposalWithKey(conn, proposal.symkeyBytes, proposal.keyName);
                     proposal.scheduleNextRetry(conn.getRttEstimator().getSmoothedRtt());
                 } catch (IOException e) {
                     // Handle error, but continue to next connection
                 }
                 continue; // Skip packet retransmission for this connection this cycle
             }
             
             // 2. Handle lost packet retransmission
             List<SentPacket> lost = conn.detectLostPackets();
             for (SentPacket packet : lost) {
                 List<Frame> framesToRetransmit = new ArrayList<>();
                 
                 // If in Negotiating state and no outstanding proposal, ensure one exists
                 if (conn.getCryptoState() == CryptoState.Negotiating && 
                     conn.getOutstandingProposal() == null) {
                     ensureSymkeyProposal(conn);
                 }
                 
                 // If in Negotiating state, prepend proposal to retransmitted frames
                 OutstandingProposal prop = conn.getOutstandingProposal();
                 if (prop != null && prop.shouldRetry(System.currentTimeMillis())) {
                     framesToRetransmit.add(0, new SymKeyProposalFrame(prop.symkeyBytes));
                 }
                 
                 // Add retransmittable frames
                 for (Frame frame : packet.frames) {
                     if (frame.shouldRetransmit()) {
                         framesToRetransmit.add(frame);
                     }
                 }
                 
                 if (!framesToRetransmit.isEmpty()) {
                     try {
                         sendPacket(conn, framesToRetransmit);
                         conn.getCongestionControl().onLoss();
                     } catch (IOException e) {
                         // Handle error
                     }
                 }
             }
         }
     }
     
     // Helper method to send proposal with specific key
     private void sendSymkeyProposalWithKey(PeerConnection conn, byte[] symkey, byte[] keyName) 
             throws IOException {
         // Reuse existing session if it exists, or create new one
         FudpSession session = sessionManager.getByKeyName(keyName);
         if (session == null) {
             session = sessionManager.addProposedSession(symkey, conn.getPeerId(),
                     ByteUtils.toHex(conn.getPeerPublicKey()));
         }
         
         SymKeyProposalFrame frame = new SymKeyProposalFrame(symkey);
         sendFrame(conn, frame);
     }
     ```

8. **Send-path gating and queue details (PreferSymkey)**  
   - Per-peer queue in `PeerConnection` holds STREAM frames until SymkeyActive. Use `ConcurrentLinkedQueue<StreamFrame>` for thread safety.  
   - Capacity guard: `MAX_QUEUE_SIZE=100` or `MAX_QUEUE_BYTES≈1MB`; on overflow, throw `QueueFullException` (extends `IOException`) to caller. Control frames (ACK, CONNECTION_CLOSE, PUBLIC_KEY/ASY_ONLY errors) bypass the queue to align with QUIC handshake/control behavior.  
   - Negotiation timeout: 2–3s since first queue; on timeout, drop queued frames, surface `NegotiationTimeoutException`, stay Negotiating or IDLE per policy.  
   - On SYMKEY_ACK: atomically transition to SymkeyActive and flush in-order through normal congestion/flow control.
   - **Queue overflow error type**: Define `class QueueFullException extends IOException` in `PeerConnection` for explicit error handling.
   - API contract change: `Protocol.send` may now throw `QueueFullException`/`NegotiationTimeoutException`; callers must handle/retry (similar to QUIC refusing app data before keys are ready).
   - **Implementation details**:
     ```java
     // In Protocol.send() - entry point for app data
     public void send(Stream stream, byte[] data) throws IOException {
         PeerConnection conn = getConnectionForStream(stream);
         if (conn == null) {
             throw new IOException("No connection for stream");
         }
         
         // Check flow control
         if (!stream.canSend(data.length)) {
             throw new IOException("Flow control limit reached");
         }
         
         // Create STREAM frame
         long offset = stream.consumeSendOffset(data.length);
         StreamFrame frame = new StreamFrame(stream.getStreamId(), offset, data, false);
         
         // Enqueue or send based on crypto state
         conn.enqueueOrSend(frame);
     }
     
     // In PeerConnection.enqueueOrSend()
     void enqueueOrSend(StreamFrame frame) throws IOException {
         // Control frames always bypass queue
         if (frame.getType() != FrameType.STREAM) {
             sendFrame(frame);
             return;
         }
         
         // For PreferSymkey mode, queue STREAM frames until SymkeyActive
         if (encryptMode == EncryptMode.PreferSymkey && 
             cryptoState != CryptoState.SymkeyActive) {
             // Check queue capacity
             if (appDataQueue.size() >= MAX_QUEUE_SIZE || 
                 totalQueuedBytes >= MAX_QUEUE_BYTES) {
                 throw new QueueFullException(
                     "App data queue full for peer: " + peerId + 
                     " (size=" + appDataQueue.size() + 
                     ", bytes=" + totalQueuedBytes + ")");
             }
             
             // Enqueue frame
             appDataQueue.offer(frame);
             totalQueuedBytes += frame.getData().length;
             
             // Track first enqueue time for timeout
             if (firstQueueTime == 0) {
                 firstQueueTime = System.currentTimeMillis();
             }
             
             // Check negotiation timeout
             checkNegotiationTimeout();
             
             // If no outstanding proposal, trigger negotiation
             if (outstandingProposal == null) {
                 ensureSymkeyProposal(); // Called from Protocol
             }
         } else {
             // SymkeyActive or AsyOnly: send immediately
             sendFrame(frame);
         }
     }
     
     // In PeerConnection.checkNegotiationTimeout()
     synchronized void checkNegotiationTimeout() {
         if (firstQueueTime > 0 && 
             System.currentTimeMillis() - firstQueueTime > 3000) { // 3s timeout
             // Timeout: clear queue and throw exception
             clearAppDataQueue();
             firstQueueTime = 0;
             throw new NegotiationTimeoutException(
                 "Key negotiation timeout for peer: " + peerId);
         }
     }
     
     // In PeerConnection.flushAppDataQueue() - called atomically with state transition
     synchronized void flushAppDataQueue() {
         while (!appDataQueue.isEmpty()) {
             StreamFrame frame = appDataQueue.poll();
             if (frame != null) {
                 totalQueuedBytes -= frame.getData().length;
                 try {
                     sendFrame(frame); // Send through normal path
                 } catch (IOException e) {
                     // Log error but continue flushing
                     System.err.println("[PeerConnection] Failed to flush queued frame: " + e);
                 }
             }
         }
         firstQueueTime = 0; // Reset timeout tracking
     }
     
     synchronized void clearAppDataQueue() {
         appDataQueue.clear();
         totalQueuedBytes = 0;
         firstQueueTime = 0;
     }
     ```

9. **Error handling and codes**  
   - Define `ERROR_ASY_ONLY = 0x03` (plaintext ERROR packet so legacy peers ignore it and continue AsyTwoWay).  
   - Send when AsyOnly node receives symkey-encrypted pkt or SYMKEY_PROPOSAL. PreferSymkey node receiving this should warn, optionally retry once, then close/mark peer AsyOnly if operator allows downgrade; if peer appears legacy (ignores 0x03), pause new proposals for a cooldown window to avoid loops.  
   - SESSION_NOT_FOUND path resets replay/stream/ACK/CC state via `resetForPeerRestart`, sets cryptoState=Negotiating, and triggers proposal (with backoff).
   - **Implementation details**:
     ```java
     // Error code definition (in Protocol.java)
     public static final int ERROR_SESSION_NOT_FOUND = 0x01;
     public static final int ERROR_DECRYPTION_FAILED = 0x02;
     public static final int ERROR_ASY_ONLY = 0x03; // New error code
     
     // In Protocol.handleIncomingPacket(), when we are AsyOnly
     if (conn.getEncryptMode() == EncryptMode.AsyOnly) {
         // If received Symkey-encrypted packet or SYMKEY_PROPOSAL
         if (packet.getUsedKeyName() != null || hasSymkeyProposal) {
             sendPlaintextError(from, ERROR_ASY_ONLY);
             // Drop the packet, don't process it
             return;
         }
     }
     
     // In Protocol.handlePlaintextError()
     private void handlePlaintextError(Packet packet, SocketAddress from) {
         byte[] payload = packet.getEncryptedPayload();
         if (payload == null || payload.length == 0) return;
         
         int errorCode = payload[0] & 0xFF;
         PeerConnection conn = connectionManager.getByAddress(from);
         
         if (errorCode == ERROR_SESSION_NOT_FOUND) {
             // Existing handling...
         } else if (errorCode == ERROR_ASY_ONLY) {
             // Peer is AsyOnly, update our mode
             if (conn != null) {
                 synchronized (conn) {
                     // Mark peer as AsyOnly
                     conn.setEncryptMode(EncryptMode.AsyOnly);
                     // Clear queue and cancel proposal
                     conn.clearAppDataQueue();
                     conn.setOutstandingProposal(null);
                     // Flush any queued data with AsyTwoWay
                     conn.flushQueuedDataWithAsyTwoWay();
                 }
                 
                 // Optionally update PeerBook for persistence
                 Peer peer = peerBook.get(conn.getPeerId());
                 if (peer != null) {
                     peer.setEncryptMode(EncryptMode.AsyOnly);
                     peerBook.save();
                 }
             }
         }
     }
     
     // In PeerConnection.flushQueuedDataWithAsyTwoWay()
     synchronized void flushQueuedDataWithAsyTwoWay() {
         while (!appDataQueue.isEmpty()) {
             StreamFrame frame = appDataQueue.poll();
             if (frame != null) {
                 totalQueuedBytes -= frame.getData().length;
                 try {
                     // Send immediately with AsyTwoWay (bypass crypto state check)
                     sendFrameWithAsyTwoWay(frame);
                 } catch (IOException e) {
                     // Log error but continue
                 }
             }
         }
         firstQueueTime = 0;
     }
     ```

10. **Restart/reset scope (align with code)**  
    - Trigger on: AsyTwoWay + SYMKEY_PROPOSAL (new connect), SESSION_NOT_FOUND cycles, explicit mode switches, decrypt-failure with ACTIVE present.  
    - Reset: replay window, stream manager (IDs/flow control), ACK manager, congestion control, sentPackets; keep `nextPacketNumber` monotonic (no reset).  
    - State after reset: ConnectionState→IDLE/ESTABLISHING as today; cryptoState→Negotiating; sessions cleared except PROPOSED.  
    - AsyBridge TTL expiry performs the same crypto reset but does **not** clear streams/CC unless paired with SESSION_NOT_FOUND (QUIC keeps streams alive across PTOs; we only drop transport state when peer restart is confirmed).

11. **Persistence and defaults**  
   - Store `encryptMode` per peer in `Peer` class (`fudp.node.Peer`) and persist via the existing peerbook storage (JSON or DB).  
   - Node-level default in `Protocol`/`NodeConfig` for new peers; CLI flag to override default (e.g., `--encrypt-mode prefer-symkey|asy-only`).  
   - **Initialization logic**: When creating `PeerConnection`, get `encryptMode` from `PeerBook.get(peerId).getEncryptMode()` if exists, else use `Protocol.getDefaultEncryptMode()`.  
   - Telemetry/logging: emit transitions (cryptoState enter/exit), proposals sent/acked, AsyBridge TTL expiry, queue timeouts, ASY_ONLY/SESSION_NOT_FOUND events.
   - **Implementation details**:
     ```java
     // In Peer class (fudp.node.Peer.java)
     private EncryptMode encryptMode; // Add this field
     
     public EncryptMode getEncryptMode() {
         return encryptMode;
     }
     
     public void setEncryptMode(EncryptMode encryptMode) {
         this.encryptMode = encryptMode;
     }
     
     // In Protocol class
     private EncryptMode defaultEncryptMode = EncryptMode.PreferSymkey;
     private PeerBook peerBook;
     
     public EncryptMode getDefaultEncryptMode() {
         return defaultEncryptMode;
     }
     
     public void setDefaultEncryptMode(EncryptMode mode) {
         this.defaultEncryptMode = mode;
     }
     
     // In ConnectionManager.getOrCreate() or PeerConnection constructor
     private EncryptMode initializeEncryptMode(String peerId) {
         // 1. Try to get from PeerBook
         if (peerBook != null) {
             Peer peer = peerBook.get(peerId);
             if (peer != null && peer.getEncryptMode() != null) {
                 return peer.getEncryptMode();
             }
         }
         
         // 2. Use node-level default
         return protocol.getDefaultEncryptMode();
     }
     
     // In PeerConnection constructor
     public PeerConnection(String peerId, SocketAddress address, long connectionId,
                          EncryptMode encryptMode) {
         // ... existing initialization ...
         this.encryptMode = encryptMode != null ? encryptMode : EncryptMode.PreferSymkey;
         this.cryptoState = CryptoState.Negotiating;
         this.appDataQueue = new ConcurrentLinkedQueue<>();
         this.totalQueuedBytes = 0;
         this.firstQueueTime = 0;
     }
     ```

12. **Key rotation (protocol-triggered, automatic)**  
   - Key rotation is automatically triggered by protocol layer based on time and/or data volume (similar to TLS 1.3 and QUIC).  
   - **Time-based trigger**: Default rotation interval is 1 hour (configurable per peer or globally).  
   - **Volume-based trigger**: Rotate after sending/receiving a certain amount of data (default: 1GB, configurable).  
   - **Check on send**: When sending packets, check if rotation is needed (time elapsed or data threshold reached); if so, initiate rotation before encrypting. Guard against recursion by skipping rotation while `outstandingProposal` exists.  
   - **Rotation process**: Mark current ACTIVE session as DEPRECATED, send new SYMKEY_PROPOSAL (encrypted with old key), set `cryptoState=Negotiating`. Proposal must reuse the same newly generated key/keyName across retries.  
   - Old session transitions: ACTIVE → DEPRECATED → deleted after cleanup delay (60s).  
   - Both parties continue using old key until new key is ACKed, then switch atomically.  
   - **Dual-initiation handling**: If both peers initiate rotation simultaneously, use pubkey-order rule (same as initial negotiation).  
   - **Implementation**: Track `session.birthTime` in `FudpSession`; track `bytesEncrypted` and `bytesDecrypted` in `PeerConnection` (per connection, not per session, to simplify). Check rotation condition in `Protocol.sendPacket()` before encryption and in `handleIncomingPacket()` after decryption.  
   - **Data volume tracking**: Increment `bytesEncrypted` after successful encryption, `bytesDecrypted` after successful decryption. Reset counters when new key becomes ACTIVE.  
   - **Time check**: Compare `System.currentTimeMillis() - session.birthTime` with `keyRotationIntervalMs`.  
   - **Dual-initiation**: If rotation is already in progress (cryptoState=Negotiating with outstandingProposal), skip rotation check to avoid duplicate proposals.
   - **Counter reset implementation**:
     ```java
     // In Protocol.handleFrame(), when processing SYMKEY_ACK
     case SYMKEY_ACK -> {
         SymKeyAckFrame ack = (SymKeyAckFrame) frame;
         sessionManager.activateProposedSession(ack.getKeyName());
         
         synchronized (conn) {
             // Atomically: transition state, reset counters, flush queue
             conn.setCryptoState(CryptoState.SymkeyActive);
             conn.resetRotationCounters(); // bytesEncrypted = 0, bytesDecrypted = 0
             conn.flushAppDataQueue(); // Flush queued frames
             conn.setOutstandingProposal(null); // Clear proposal tracking
         }
     }
     
     // In PeerConnection.resetRotationCounters()
     synchronized void resetRotationCounters() {
         bytesEncrypted = 0;
         bytesDecrypted = 0;
     }
     ```

13. **Mode switching (immediate effect)**  
    - Manual mode switch via `Protocol.setEncryptMode(String peerId, EncryptMode mode)`.  
    - **PreferSymkey → AsyOnly**: clear app data queue, flush queued frames immediately with AsyTwoWay, cancel any outstanding proposal.  
    - **AsyOnly → PreferSymkey**: if not SymkeyActive, send proposal immediately; queue app data until negotiation completes.  
    - Mode switch is synchronized to ensure atomic state transition.

14. **State transition atomicity**  
    - Critical transitions (e.g., `Negotiating → SymkeyActive`) must be atomic to prevent race conditions.  
    - Use `synchronized` blocks around state checks and updates:  
      ```java
      synchronized void onSymkeyAck(byte[] keyName) {
          if (outstandingProposal != null && Arrays.equals(outstandingProposal.keyName, keyName)) {
              outstandingProposal = null;
              cryptoState = CryptoState.SymkeyActive;
              flushAppDataQueue();  // Atomic with state change
          }
      }
      ```

15. **Address migration compatibility**  
    - Address migration does not affect `cryptoState`; session remains valid across address changes.  
    - State reset is only triggered when session is lost (not on address change alone).  
    - In `handleIncomingPacket`, check address change flag separately from session validity.

16. **Key rotation configuration**  
    - Configurable per-peer or globally via `PeerConnection.setKeyRotationInterval(long ms)` and `setKeyRotationDataVolume(long bytes)`.  
    - Defaults: 1 hour interval, 1GB data volume.  
    - Rotation check happens automatically on each send/receive, no application intervention needed.  
    - Both triggers are independent: rotation happens when either condition is met (whichever comes first).

17. **Implementation checkpoints (phased)**  
    1) Add config persistence and per-peer cryptoState skeleton (no behavior change).  
    2) Replace `forceAsyTwoWay` usage with cryptoState+AsyBridge TTL; wire PacketCrypto to choose mode by (encryptMode, cryptoState).  
    3) Implement deterministic proposal object + send-path gating + queue + negotiation timeout (PreferSymkey 1-RTT).  
    4) Add proposal reliability/backoff + outstandingProposal tracking and rate limits on auto reproposal.  
    5) Add ASY_ONLY error handling, restart/reset alignment, and dual-init handling.  
    6) Add automatic key rotation (time + data volume based) and mode switching logic.  
    7) Tests: peer restart recovery, dual proposals, manual mode toggles, AsyBridge TTL expiry, queue overflow/timeout, ASY_ONLY interop (including legacy ignoring 0x03), key rotation (time-based and volume-based triggers), address migration.

## Implementation Details

### Code Structure Changes

**PeerConnection.java**:
```java
// State fields (thread-safe)
private volatile CryptoState cryptoState = CryptoState.Negotiating;
private volatile EncryptMode encryptMode;
private volatile long asyBridgeUntil = 0;

// Outstanding proposal tracking
private volatile OutstandingProposal outstandingProposal;

// App data queue (lock-free)
private final Queue<StreamFrame> appDataQueue = new ConcurrentLinkedQueue<>();
private volatile long totalQueuedBytes = 0;
private static final int MAX_QUEUE_SIZE = 100;
private static final long MAX_QUEUE_BYTES = 1_000_000;

// Key rotation configuration and tracking
private static final long DEFAULT_KEY_ROTATION_INTERVAL_MS = 3600_000; // 1 hour
private static final long DEFAULT_KEY_ROTATION_DATA_VOLUME = 1_000_000_000; // 1GB
private long keyRotationIntervalMs = DEFAULT_KEY_ROTATION_INTERVAL_MS;
private long keyRotationDataVolume = DEFAULT_KEY_ROTATION_DATA_VOLUME;
private volatile long bytesEncrypted = 0;  // Reset when key becomes ACTIVE
private volatile long bytesDecrypted = 0;  // Reset when key becomes ACTIVE

// State transition methods (synchronized)
synchronized void transitionToAsyBridge() { /* ... */ }
synchronized void onSymkeyAck(byte[] keyName) { 
    // Reset rotation counters when new key becomes active
    bytesEncrypted = 0;
    bytesDecrypted = 0;
}
void checkAsyBridgeTTL() { /* ... */ }
void incrementBytesEncrypted(int bytes) { 
    bytesEncrypted += bytes; 
}
void incrementBytesDecrypted(int bytes) { 
    bytesDecrypted += bytes; 
}
boolean shouldRotateKey(FudpSession session) { 
    // Skip if already rotating
    if (cryptoState == CryptoState.Negotiating && outstandingProposal != null) {
        return false;
    }
    long age = System.currentTimeMillis() - session.getBirthTime();
    boolean timeExpired = age >= keyRotationIntervalMs;
    boolean volumeExceeded = (bytesEncrypted + bytesDecrypted) >= keyRotationDataVolume;
    return timeExpired || volumeExceeded;
}
```

**Protocol.java**:
```java
// In sendPacket(), before encryption
private void sendPacket(PeerConnection conn, List<Frame> frames) throws IOException {
    // Check AsyBridge TTL
    if (conn.getCryptoState() == CryptoState.AsyBridge) {
        conn.checkAsyBridgeTTL();
    }
    
    // Check key rotation before encryption
    if (conn.getEncryptMode() == EncryptMode.PreferSymkey &&
        conn.getCryptoState() == CryptoState.SymkeyActive) {
        FudpSession activeSession = sessionManager.getActiveSession(conn.getPeerId());
        if (activeSession != null && conn.shouldRotateKey(activeSession)) {
            initiateKeyRotation(conn);
        }
    }
    
    // ... encryption and send
    // After encryption, update bytesEncrypted counter
    if (conn.getCryptoState() == CryptoState.SymkeyActive) {
        conn.incrementBytesEncrypted(packetSize);
    }
}

// Key rotation initiation
private void initiateKeyRotation(PeerConnection conn) throws IOException {
    // Skip if rotation already in progress
    if (conn.getCryptoState() == CryptoState.Negotiating && conn.getOutstandingProposal() != null) {
        return;  // Already rotating
    }
    
    FudpSession oldSession = sessionManager.getActiveSession(conn.getPeerId());
    if (oldSession != null) {
        sessionManager.deprecateSession(oldSession.getId());
    }
    sendSymkeyProposal(conn);
    conn.setCryptoState(CryptoState.Negotiating);
}

// In handleIncomingPacket(), after successful decryption
private void handleIncomingPacket(byte[] data, SocketAddress from) {
    // ... decrypt packet ...
    
    // Update bytesDecrypted counter if using symkey
    if (packet.getUsedKeyName() != null) {
        conn.incrementBytesDecrypted(data.length);
        
        // Check key rotation after decryption (both peers should rotate roughly at same time)
        if (conn.getEncryptMode() == EncryptMode.PreferSymkey &&
            conn.getCryptoState() == CryptoState.SymkeyActive) {
            FudpSession activeSession = sessionManager.getActiveSession(conn.getPeerId());
            if (activeSession != null && conn.shouldRotateKey(activeSession)) {
                initiateKeyRotation(conn);
            }
        }
    }
    
    // ... process frames ...
}

// In retransmitTask(), prioritize proposal retransmission
private void retransmitTask() {
    for (PeerConnection conn : connectionManager.getAllConnections()) {
        // 1. Check proposal retransmission first
        OutstandingProposal proposal = conn.getOutstandingProposal();
        if (proposal != null && proposal.shouldRetry(System.currentTimeMillis())) {
            sendSymkeyProposal(conn);
            proposal.scheduleNextRetry(conn.getRttEstimator().getSmoothedRtt());
            continue;
        }
        // 2. Then handle packet retransmission
        // ... existing logic
    }
}
```

**PacketCrypto.java**:
```java
public Packet encryptPacket(Packet packet, String peerId, byte[] peerPubkey, 
                           CryptoState cryptoState, EncryptMode encryptMode) {
    if (encryptMode == EncryptMode.AsyOnly) {
        return encryptAsAsyTwoWay(...);
    }
    if (cryptoState == CryptoState.SymkeyActive) {
        FudpSession session = sessionManager.getActiveSession(peerId);
        if (session != null) {
            return encryptAsSymkey(...);
        }
    }
    // Default: AsyTwoWay (for negotiation phase)
    return encryptAsAsyTwoWay(...);
}
```

## Next Steps

### Phase 1: Type Definitions and Basic Structure
- [ ] Add `EncryptMode` enum in `fudp.crypto` package
- [ ] Add `CryptoState` enum in `fudp.connection` package
- [ ] Implement `OutstandingProposal` class in `PeerConnection`
- [ ] Add error classes: `QueueFullException`, `NegotiationTimeoutException` in `PeerConnection`
- [ ] Add `encryptMode` field to `Peer` class with getter/setter
- [ ] Add `encryptMode` and `cryptoState` fields to `PeerConnection`

### Phase 2: State Machine Implementation
- [ ] Implement `cryptoState` state transitions (Negotiating ↔ SymkeyActive ↔ AsyBridge)
- [ ] Implement AsyBridge TTL checking and exit logic
- [ ] Replace `forceAsyTwoWay` usage with `cryptoState == AsyBridge`
- [ ] Update `PacketCrypto.encryptPacket()` to use `encryptMode` and `cryptoState`

### Phase 3: Queue and Proposal Management
- [ ] Implement application data queue (`ConcurrentLinkedQueue<StreamFrame>`)
- [ ] Implement `enqueueOrSend()` logic with capacity checks
- [ ] Implement queue timeout checking (3 seconds)
- [ ] Implement `flushAppDataQueue()` with atomic state transition
- [ ] Implement `OutstandingProposal` tracking and retry logic
- [ ] Update `retransmitTask()` to handle proposal retransmission

### Phase 4: Automatic Re-negotiation
- [ ] Implement `ensureSymkeyProposal()` helper with rate limiting
- [ ] Add automatic proposal triggers (SESSION_NOT_FOUND, decrypt failure, app send)
- [ ] Implement proposal retry with exponential backoff
- [ ] Update `handleIncomingPacket()` to enter AsyBridge on AsyTwoWay with ACTIVE session

### Phase 5: Error Handling and Mode Switching
- [ ] Implement `ERROR_ASY_ONLY` error code and handling
- [ ] Implement mode switching logic (`Protocol.setEncryptMode()`)
- [ ] Update `handlePlaintextError()` to process `ERROR_ASY_ONLY`
- [ ] Implement `flushQueuedDataWithAsyTwoWay()` for mode switch

### Phase 6: Key Rotation
- [ ] Add `birthTime` field to `FudpSession`
- [ ] Add rotation tracking fields to `PeerConnection` (`bytesEncrypted`, `bytesDecrypted`)
- [ ] Implement `shouldRotateKey()` check logic
- [ ] Implement `initiateKeyRotation()` in `Protocol`
- [ ] Add rotation check in `sendPacket()` and `handleIncomingPacket()`
- [ ] Implement counter reset on SYMKEY_ACK

### Phase 7: Initialization and Persistence
- [ ] Implement `initializeEncryptMode()` logic in `ConnectionManager` or `PeerConnection`
- [ ] Add `getDefaultEncryptMode()` and `setDefaultEncryptMode()` to `Protocol`
- [ ] Update `PeerBook` persistence to save/load `encryptMode`
- [ ] Add CLI flag support for default encrypt mode

### Phase 8: Testing
- [ ] Unit tests for state transitions
- [ ] Unit tests for queue management
- [ ] Unit tests for proposal retry logic
- [ ] Integration test: peer restart recovery
- [ ] Integration test: dual-init negotiation
- [ ] Integration test: AsyBridge TTL expiry
- [ ] Integration test: queue overflow/timeout
- [ ] Integration test: ERROR_ASY_ONLY handling
- [ ] Integration test: key rotation (time-based and volume-based)
- [ ] Integration test: mode switching
- [ ] Integration test: address migration compatibility

## Implementation Checklist Summary

**Core Types**: EncryptMode, CryptoState, OutstandingProposal, Error classes  
**State Management**: cryptoState transitions, AsyBridge TTL, state atomicity  
**Queue Management**: Enqueue/Dequeue logic, capacity checks, timeout handling  
**Proposal Management**: Outstanding tracking, retry logic, retransmission coordination  
**Error Handling**: ERROR_ASY_ONLY, SESSION_NOT_FOUND improvements  
**Key Rotation**: Time/volume triggers, counter management, dual-init handling  
**Initialization**: EncryptMode initialization, persistence, defaults  
**Testing**: Comprehensive unit and integration tests
