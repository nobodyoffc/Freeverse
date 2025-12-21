# FUDP Node Implementation Plan

## 1. Overview

The FUDP Node is an application-layer program built on top of the existing FUDP protocol stack. It provides a user-friendly interface for P2P communication, supporting messaging, request/response patterns, and file transfer.

---

## 2. Application-Level Message Protocol

### 2.1 Message Frame Format

All application messages are transmitted as STREAM frame data with a common header:

```
Application Message
┌─────────────────────────────────────┐
│ Message Type (1 byte)               │
├─────────────────────────────────────┤
│ Message ID (8 bytes)                │  ← For request/response correlation
├─────────────────────────────────────┤
│ Flags (1 byte)                      │
├─────────────────────────────────────┤
│ Payload Length (varint)             │
├─────────────────────────────────────┤
│ Payload (variable)                  │
└─────────────────────────────────────┘
```

### 2.2 Message Types

```java
enum MessageType {
    // Chat/Messaging
    CHAT(0x01),                    // Simple text message
    CHAT_ACK(0x02),                // Message delivered acknowledgment (New)

    // Request/Response
    REQUEST(0x10),                 // Application request
    RESPONSE(0x11),                // Application response
    ERROR(0x12),                   // Error response

    // File Transfer
    FILE_OFFER(0x20),              // Offer to send a file
    FILE_ACCEPT(0x21),             // Accept file offer
    FILE_REJECT(0x22),             // Reject file offer
    FILE_CHUNK(0x23),              // File data chunk
    FILE_COMPLETE(0x24),           // File transfer complete
    FILE_CANCEL(0x25),             // Cancel transfer

    // Control
    PING(0x30),                    // Keep-alive ping
    PONG(0x31),                    // Ping response
    PEER_INFO(0x32),               // Exchange peer information

    // Relay
    RELAY(0x40),                   // Relay message to target FID
    RELAY_ACK(0x41),               // Relay delivery confirmed
    RELAY_FAIL(0x42),              // Relay delivery failed
    RELAY_QUERY(0x43),             // Query relay path/cost
    RELAY_QUOTE(0x44),             // Relay cost quote response
}
```

### 2.3 Flags Definition

```
Bit 0: NEED_ACK      - Require delivery confirmation
Bit 1: COMPRESSED    - Payload is compressed (gzip)
Bit 2: ENCRYPTED_APP - Additional app-level encryption
Bit 3: FRAGMENTED    - Message is fragmented (for large payloads)
Bit 4: WANT_PONG_INFO - For PING: ask peer to include optional info data in PONG
Bit 5-7: Reserved
```

---

## 3. Architecture

### 3.1 Integration with FUDP Protocol Layer

The Node layer builds on top of the existing FUDP protocol implementation (`fudp.Protocol`). Key integration points:

#### 3.1.1 Layer Relationship

```
┌─────────────────────────────────────────────────┐
│ Application Layer                                │
│   FudpNode, MessageHandler, TransferManager      │
│   (Chat, Request/Response, File Transfer, Relay) │
├─────────────────────────────────────────────────┤
│ Protocol Layer (existing fudp.Protocol)          │
│   Connection, Stream, Encryption, ACK/Retransmit │
│   (Already implemented in fudp.* packages)       │
├─────────────────────────────────────────────────┤
│ UDP                                              │
└─────────────────────────────────────────────────┘
```

#### 3.1.2 FudpNode wraps Protocol

```java
public class FudpNode implements Protocol.PacketListener {
    private final Protocol protocol;

    public FudpNode(byte[] privateKey, NodeConfig config) {
        this.protocol = new Protocol(privateKey, config.getPort());
        this.protocol.addPacketListener(this);
        // ...
    }

    @Override
    public void onPacketReceived(PeerConnection connection, Packet packet) {
        // Route to appropriate handler based on stream data
        for (Frame frame : packet.getFrames()) {
            if (frame instanceof StreamFrame sf) {
                Stream stream = connection.getStream(sf.getStreamId());
                byte[] data = stream.readAvailable();
        if (data != null && data.length > 0) {
            messageHandler.handleIncomingData(connection.getPeerId(), data);
        }
    }
}
```

#### 3.1.3 Resilience: peer restart with same IP/port
- 当收到 AsyTwoWay 携带的 `SYMKEY_PROPOSAL` 时，视为对端重启：重置 replay window、流状态、ACK 状态，接受重新从 0 开始的包号与 streamId。
- 收到 `ERROR_SESSION_NOT_FOUND`（code=1）时，仅清理 ACTIVE/DEPRECATED，会保留 PROPOSED，避免正在协商的新密钥被误删。
- 若在 ACTIVE 会话下收到 AsyTwoWay 数据，临时开启 `forceAsyTwoWay`，直至新会话协商完成，避免使用失效对称密钥。
    }
}
```

#### 3.1.3 Application Messages over Streams

Each application message type uses FUDP streams differently:

| Message Type | Stream Usage | Notes |
|-------------|--------------|-------|
| CHAT | New unidirectional stream per message | Simple fire-and-forget |
| REQUEST | New bidirectional stream | Response on same stream |
| RESPONSE | Same stream as request | Closes after send |
| FILE_OFFER | New bidirectional stream | Control channel |
| FILE_CHUNK | Separate unidirectional stream | Data channel, high throughput |
| RELAY | New unidirectional stream | Forwarded to target |

Control specifics:
- `PING` payload: `timestamp (8B)`. Set `WANT_PONG_INFO` flag when you expect service metadata in `PONG`.
- `PONG` payload: `echoTs (8B) + replyTs (8B) + dataLen (varint) + data (<=1KB default, configurable)`. Nodes rate-limit info-bearing PONGs per peer (default ≥2s) and truncate data to configured max to avoid amplification.

```java
// Example: Sending a chat message
public void sendChat(String peerId, String message) {
    PeerConnection conn = getOrConnectPeer(peerId);
    Stream stream = conn.openStream();  // Uses Protocol's stream management

    ChatMessage chat = new ChatMessage(message);
    byte[] encoded = MessageCodec.encode(chat);

    protocol.sendAndClose(stream, encoded);  // Send and close stream
}

// Example: Request/Response
public CompletableFuture<ResponseMessage> request(String peerId, byte[] data) {
    PeerConnection conn = getOrConnectPeer(peerId);
    Stream stream = conn.openStream();

    RequestMessage req = new RequestMessage(nextMessageId(), data);
    byte[] encoded = MessageCodec.encode(req);

    CompletableFuture<ResponseMessage> future = new CompletableFuture<>();
    pendingRequests.put(req.getMessageId(), future);

    // Set up stream data callback for response
    stream.setDataCallback(responseData -> {
        ResponseMessage resp = MessageCodec.decode(responseData);
        CompletableFuture<ResponseMessage> f = pendingRequests.remove(resp.getMessageId());
        if (f != null) f.complete(resp);
    });

    protocol.send(stream, encoded);
    return future;
}
```

#### 3.1.4 Session and Connection Reuse

- **Connection**: FudpNode reuses `PeerConnection` from Protocol's `ConnectionManager`
- **Session**: Protocol's `SessionManager` handles symmetric key lifecycle (in-memory only, cleared on process exit)
- **Streams**: Multiple concurrent streams per connection (flow controlled)

```java
private PeerConnection getOrConnectPeer(String peerId) {
    // First check if already connected
    PeerConnection conn = protocol.getConnectionManager().getByPeerId(peerId);
    if (conn != null && conn.isEstablished()) {
        return conn;
    }

    // Get address from PeerBook
    Peer peer = peerBook.get(peerId);
    if (peer == null) {
        throw new UnknownPeerException(peerId);
    }

    // Connect via Protocol
    SocketAddress address = new InetSocketAddress(peer.getHost(), peer.getPort());
    Stream stream = protocol.connect(peer.getPublicKey(), address);

    return protocol.getConnectionManager().getByPeerId(peerId);
}
```

#### 3.1.5 Pong info advertisement (New)

- NodeConfig 新增 `pongDataProvider`、`maxPongDataBytes`（默认 1024B）、`pongInfoMinIntervalMs`（默认 2000ms）。
- 收到携带 `WANT_PONG_INFO` 的 PING 时，FudpNode 调用 `pongDataProvider` 生成数据，按配置截断并按 peer 做速率限制后写入 PONG。
- 典型用例：FAPI 节点在 PONG data 中返回可提供的服务 ID/元信息，便于对端做服务发现/路由。

### 3.2 Module Structure

```
fudp/
├── src/main/java/fudp/
│   ├── node/
│   │   ├── FudpNode.java              # Main node entry point
│   │   ├── NodeConfig.java            # Node configuration
│   │   ├── PeerBook.java              # Known peers storage
│   │   └── NodeEventListener.java     # Event callback interface
│   ├── message/
│   │   ├── AppMessage.java            # Base application message
│   │   ├── MessageType.java           # Message type enum
│   │   ├── MessageCodec.java          # Serialization/deserialization
│   │   ├── ChatMessage.java           # Chat message
│   │   ├── RequestMessage.java        # Request message
│   │   ├── ResponseMessage.java       # Response message
│   │   └── FileMessage.java           # File transfer messages
│   ├── handler/
│   │   ├── MessageHandler.java        # Routes incoming messages
│   │   ├── ChatHandler.java           # Chat message handling
│   │   ├── RequestHandler.java        # Request/response handling
│   │   └── FileHandler.java           # File transfer handling
│   ├── transfer/
│   │   ├── FileTransfer.java          # Active transfer state
│   │   ├── FileChunker.java           # File chunking logic
│   │   ├── FileAssembler.java         # Chunk reassembly
│   │   └── TransferManager.java       # Manages all transfers
│   └── cli/
│       ├── NodeCli.java               # CLI entry point
│       ├── CommandParser.java         # Command parsing
│       └── ConsoleOutput.java         # Output formatting
```

### 3.2 Core Class Design

#### FudpNode.java

```java
public class FudpNode {
    private final Protocol protocol;
    private final NodeConfig config;
    private final PeerBook peerBook;
    private final MessageHandler messageHandler;
    private final TransferManager transferManager;
    private final Map<Long, CompletableFuture<ResponseMessage>> pendingRequests;

    public FudpNode(byte[] privateKey, NodeConfig config);

    // Lifecycle
    public void start();
    public void stop();

    // Messaging
    public void sendChat(String peerId, String message);
    public void sendChatWithAck(String peerId, String message, long messageId); // New
    public CompletableFuture<ResponseMessage> request(String peerId, byte[] data);
    public void broadcast(String message, List<String> peerIds);

    // File Transfer
    public FileTransfer sendFile(String peerId, String filePath);
    public void cancelTransfer(String transferId);

    // Peer Management
    public void addPeer(String peerId, String host, int port);
    public void removePeer(String peerId);
    public List<Peer> listPeers();
    public Peer getPeer(String peerId);

    // Performance Monitoring
    public NodeStats getNodeStats();                    // Aggregated node statistics
    public NodeStats.PeerStats getPeerStats(String peerId);  // Per-peer statistics
    public void addMeterListener(MeterListener listener);    // Transport metering events
    public void removeMeterListener(MeterListener listener);

    // Events
    public void setEventListener(NodeEventListener listener);

    // Direct Protocol Access
    public Protocol getProtocol();
    public String getLocalFid();
}
```

#### NodeEventListener.java

```java
public interface NodeEventListener {
    // Connection events
    void onPeerConnected(String peerId);
    void onPeerDisconnected(String peerId);

    // Message events
    void onChatReceived(String peerId, long messageId, String message);
    void onChatAck(String peerId, long messageId, long rttMs);  // Includes RTT measurement
    void onRequestReceived(String peerId, long requestId, byte[] data);

    // File events
    void onFileOfferReceived(String peerId, FileOffer offer);
    void onFileProgress(String transferId, long transferred, long total);
    void onFileComplete(String transferId, String filePath);
    void onFileError(String transferId, String error);

    // Performance events
    void onPingComplete(String peerId, long rttMs);  // Ping/pong latency measurement
}
```

---

## 4. Detailed Component Design

### 4.1 Chat Messaging

**Flow:**
1. User calls `node.sendChat(peerId, message)`
2. FudpNode creates `ChatMessage` with type `CHAT`
3. Message encoded and sent on a new stream
4. Peer receives, `ChatHandler` processes, triggers `onChatReceived`
5. If `NEED_ACK` flag set, peer sends `CHAT_ACK`

**ChatMessage Payload:**
```
┌─────────────────────────────────────┐
│ Content Type (1 byte)               │  0=text, 1=markdown, 2=json
├─────────────────────────────────────┤
│ Content (UTF-8 string)              │
└─────────────────────────────────────┘
```

### 4.2 Request/Response Pattern

**Flow:**
1. User calls `node.request(peerId, data)` → returns `CompletableFuture<ResponseMessage>`
2. FudpNode creates `RequestMessage` with unique `messageId`
3. Stores `messageId → CompletableFuture` in `pendingRequests`
4. Sends on a new stream
5. Peer receives, `RequestHandler` processes, calls `onRequestReceived`
6. Application creates response via `node.respond(requestId, data)`
7. Response sent with matching `messageId`
8. Original sender matches response to `CompletableFuture`, completes it

**RequestMessage Payload:**
```
┌─────────────────────────────────────┐
│ Service Name Length (varint)        │
├─────────────────────────────────────┤
│ Service Name (UTF-8)                │  e.g., "user.profile"
├─────────────────────────────────────┤
│ Request Data                        │
└─────────────────────────────────────┘
```

**ResponseMessage Payload:**
```
┌─────────────────────────────────────┐
│ Status Code (2 bytes)               │  0=success, others=error
├─────────────────────────────────────┤
│ Response Data                       │
└─────────────────────────────────────┘
```

### 4.3 File Transfer

**States:**
```java
enum TransferState {
    OFFERING,      // Sent FILE_OFFER, waiting for response
    WAITING,       // Received FILE_OFFER, not yet accepted
    ACCEPTED,      // Transfer accepted
    TRANSFERRING,  // Actively sending/receiving chunks
    COMPLETING,    // Sent/received all chunks, verifying
    COMPLETE,      // Successfully completed
    CANCELLED,     // User cancelled
    FAILED         // Error occurred
}
```

**FILE_OFFER Payload:**
```
┌─────────────────────────────────────┐
│ Transfer ID (8 bytes)               │
├─────────────────────────────────────┤
│ File Name Length (varint)           │
├─────────────────────────────────────┤
│ File Name (UTF-8)                   │
├─────────────────────────────────────┤
│ File Size (8 bytes)                 │
├─────────────────────────────────────┤
│ Chunk Size (4 bytes)                │  default: 32KB
├─────────────────────────────────────┤
│ SHA-256 Hash (32 bytes)             │  file integrity check
└─────────────────────────────────────┘
```

**FILE_CHUNK Payload:**
```
┌─────────────────────────────────────┐
│ Transfer ID (8 bytes)               │
├─────────────────────────────────────┤
│ Chunk Index (4 bytes)               │
├─────────────────────────────────────┤
│ Chunk Data                          │
└─────────────────────────────────────┘
```

**Transfer Flow:**
```
Sender                                Receiver
  │                                      │
  │──── FILE_OFFER ─────────────────────>│
  │                                      │ onFileOfferReceived()
  │                                      │ user accepts
  │<──── FILE_ACCEPT ────────────────────│
  │                                      │
  │──── FILE_CHUNK[0] ──────────────────>│
  │──── FILE_CHUNK[1] ──────────────────>│
  │──── FILE_CHUNK[2] ──────────────────>│
  │      ...                             │
  │──── FILE_CHUNK[N] ──────────────────>│
  │                                      │
  │──── FILE_COMPLETE ──────────────────>│
  │                                      │ verify hash
  │                                      │ onFileComplete()
```

**Resume Support:**
- `FileAssembler` tracks received chunks in a bitmap
- If connection drops, receiver can request missing chunks
- Uses `FILE_ACCEPT` with `resumeFromChunk` field

### 4.4 Data Relay

**Use Case:** NAT traversal, indirect messaging when peers cannot connect directly

#### 4.4.1 Relay Message Format

```
RELAY Message Payload
┌─────────────────────────────────────┐
│ Target FID (33 bytes)               │  ← Final destination
├─────────────────────────────────────┤
│ Flags (1 byte)                      │
│   Bit 0: NEED_DELIVERY_RECEIPT      │
│   Bit 1: STORE_IF_OFFLINE           │
│   Bit 2-7: Reserved                 │
├─────────────────────────────────────┤
│ TTL (1 byte, default=3)             │  ← Max hops, prevent loops
├─────────────────────────────────────┤
│ Message ID (8 bytes)                │  ← For tracking and dedup
├─────────────────────────────────────┤
│ Max Fee Per KB (8 bytes)            │  ← User's fee threshold
├─────────────────────────────────────┤
│ Accumulated Fee (8 bytes)           │  ← Total fee so far
├─────────────────────────────────────┤
│ Payment ID (8 bytes)                │  ← For settlement tracking
├─────────────────────────────────────┤
│ CryptoDataByte Bundle               │  ← End-to-end encrypted
└─────────────────────────────────────┘
```

**Note:** Original sender FID is derived from `pubkeyA` in the CryptoDataByte Bundle (AsyTwoWay mode) or from `FcSession.userId` (Symkey mode).

#### 4.4.2 Relay Flow

```
Sender A          Relay X           Addressing        Relay Y/Z        Receiver B
   │                 │                 │                 │                 │
   │ RELAY(FID_B,    │                 │                 │                 │
   │   maxFee,       │                 │                 │                 │
   │   Bundle[A→B])  │                 │                 │                 │
   ├────────────────>│                 │                 │                 │
   │                 │ 1. Identify A (FUDP layer)        │                 │
   │                 │ 2. Check fee threshold            │                 │
   │                 │                 │                 │                 │
   │                 │ Query FID_B     │                 │                 │
   │                 ├────────────────>│                 │                 │
   │                 │                 │                 │                 │
   │                 │ Return relay    │                 │                 │
   │                 │ list with status│                 │                 │
   │                 │<────────────────┤                 │                 │
   │                 │                 │                 │                 │
   │                 │ 3. Poll relays for B's status     │                 │
   │                 ├────────────────────────────────────>│                 │
   │                 │                 │                 │                 │
   │                 │ 4. Select best relay based on:    │                 │
   │                 │    - Online status                │                 │
   │                 │    - Fee rate                     │                 │
   │                 │    - tRate (credit rating)        │                 │
   │                 │    - tCdd (coin-day destroyed)    │                 │
   │                 │    - Cached latency               │                 │
   │                 │                 │                 │                 │
   │                 │ RELAY(FID_B, accumulatedFee + localFee)             │
   │                 ├────────────────────────────────────>│                 │
   │                 │                 │                 │ 5. Check fee    │
   │                 │                 │                 │ 6. Find B local │
   │                 │                 │                 │                 │
   │                 │                 │                 │ Deliver to B    │
   │                 │                 │                 ├────────────────>│
   │                 │                 │                 │                 │
   │                 │                 │                 │<────────────────┤
   │                 │                 │                 │  ACK            │
   │                 │<────────────────────────────────────┤                 │
   │                 │  RELAY_ACK      │                 │                 │
   │<────────────────┤                 │                 │                 │
   │  RELAY_ACK      │                 │                 │                 │

If B is offline:
   │                 │                 │                 │                 │
   │                 │                 │                 │ Store to        │
   │                 │                 │                 │ designated      │
   │                 │                 │                 │ storage service │
   │                 │                 │                 │                 │
   │                 │                 │                 │ (Relay Y pays   │
   │                 │                 │                 │  storage fee)   │
```

#### 4.4.3 Fee Threshold Mechanism

**No Pre-payment Required:** Users don't need to query fees in advance. Instead, they set a maximum fee per KB threshold.

```java
public class FeeThresholdChecker {
    public boolean checkFee(RelayMessage msg, long localFee) {
        int messageSizeKb = (msg.getPayloadSize() / 1024) + 1;
        long totalFee = msg.getAccumulatedFee() + localFee;
        long feePerKb = totalFee / messageSizeKb;

        if (feePerKb > msg.getMaxFeePerKb()) {
            // Return FEE_EXCEEDED error to sender
            return false;
        }

        // Update accumulated fee and forward
        msg.setAccumulatedFee(totalFee);
        return true;
    }
}
```

#### 4.4.4 Relay Caching

Relays cache recent routing information to improve performance:

```java
public class RelayCache {
    // Cache: Target FID → Best relay info
    private Cache<String, CachedRelayInfo> fidToRelayCache;

    // Cache: Relay ID → Route metrics (latency, fee, success rate)
    private Cache<String, RouteMetrics> relayMetricsCache;

    private long cacheTtlMs = 300000;  // 5 minutes TTL

    public void updateMetrics(String relayId, long latency, long fee, boolean success) {
        RouteMetrics metrics = relayMetricsCache.get(relayId);
        if (metrics == null) {
            metrics = new RouteMetrics(relayId);
        }
        metrics.addSample(latency, fee, success);
        relayMetricsCache.put(relayId, metrics);
    }
}

public class CachedRelayInfo {
    private String targetFid;
    private String relayId;
    private long lastLatency;
    private long lastFee;
    private long cachedTime;
}
```

#### 4.4.5 Offline Handling

When target user is offline:
1. Relay Y stores message to its **designated storage service** (relay pays storage fee)
2. When B comes online, B retrieves message from storage service (free for B, identity verified via FUDP)
3. Storage service deletes data after retrieval
4. B sends ACK to Relay Y
5. Relay Y pays receiver reward to B

**Note:** The storage fee roughly offsets the receiver reward, which helps determine pricing.

```
RELAY_FAIL Error Codes:
  0x01 = TARGET_UNKNOWN        // FID not found in any addressing service
  0x02 = TARGET_UNREACHABLE    // All candidate relays unreachable
  0x03 = FEE_EXCEEDED          // Accumulated fee exceeds max fee threshold
  0x04 = TTL_EXPIRED           // Too many hops
  0x05 = MESSAGE_TOO_LARGE     // Exceeds relay limit
  0x06 = STORAGE_FAILED        // Failed to store for offline user
```

---

## 5. Peer Management

### 5.1 PeerBook Storage

```java
public class PeerBook {
    private final Path storageFile;  // ~/.fudp/{fid}_peers.json
    private final Map<String, Peer> peers;

    public PeerBook(String dataDir, long cacheTtl, String localFid) {
        // File name includes local FID to support multiple nodes
        this.storageFile = Path.of(dataDir, localFid + "_peers.json");
        // ...
    }

    public void add(Peer peer);
    public void remove(String peerId);
    public Peer get(String peerId);
    public List<Peer> list();
    public void save();
    public void load();
}

public class Peer {
    private String peerId;           // FID
    private byte[] publicKey;
    private String host;
    private int port;
    private String alias;            // User-friendly name
    private long lastSeen;
    private ConnectionState state;
}
```

#### 5.1.1 Address Resolution and Update

PeerBook integrates with Protocol layer for address management:

```java
public class PeerBook {
    private final Protocol protocol;

    /**
     * Add peer with known address (manual configuration)
     */
    public void addWithAddress(String peerId, byte[] publicKey, String host, int port) {
        Peer peer = new Peer(peerId, publicKey, host, port);
        peers.put(peerId, peer);
        save();
    }

    /**
     * Update address from incoming connection
     * Called when we receive data from a peer
     */
    public void updateFromConnection(String peerId, SocketAddress address) {
        Peer peer = peers.get(peerId);
        if (peer != null && address instanceof InetSocketAddress inet) {
            peer.setHost(inet.getHostString());
            peer.setPort(inet.getPort());
            peer.setLastSeen(System.currentTimeMillis());
            peer.setState(ConnectionState.ESTABLISHED);
            save();
        }
    }

    /**
     * Get address for connecting to peer
     * Priority: 1) Cached address, 2) Query relay/addressing service
     */
    public SocketAddress resolveAddress(String peerId) throws IOException {
        Peer peer = peers.get(peerId);
        if (peer == null) {
            throw new UnknownPeerException(peerId);
        }

        // Use cached address if recent
        if (System.currentTimeMillis() - peer.getLastSeen() < ADDRESS_CACHE_TTL) {
            return new InetSocketAddress(peer.getHost(), peer.getPort());
        }

        // For NAT traversal: query relay for current address
        // (Implementation depends on relay integration)
        return new InetSocketAddress(peer.getHost(), peer.getPort());
    }
}
```

**Address Update Flow:**

Since FUDP protocol identifies peers by public key (not IP), addresses can change transparently:

```
1. Peer A sends packet to Peer B
2. Protocol layer decrypts and identifies sender by public key
3. FudpNode receives onPacketReceived callback
4. FudpNode calls peerBook.updateFromConnection(senderId, fromAddress)
5. PeerBook updates cached address for this peer
```

This enables:
- **Mobile devices** - Address changes when switching networks
- **NAT rebinding** - Port changes after NAT timeout
- **Load balancing** - Peer moves between relay servers

### 5.2 Multi-Relay Registration

Users can register with multiple relays for better availability and fault tolerance.

```java
public class UserRegistration {
    private String fid;
    private List<RelayRegistration> relays;  // Multiple relays
    private String storageServiceId;         // For offline message storage
}

public class RelayRegistration {
    private String relayId;          // Relay service ID
    private int priority;            // User preference priority (lower = higher priority)
    private long registeredTime;
    private String networkAddress;   // User's address for this relay
}
```

**Benefits:**
- Higher availability (if one relay is down, others can receive messages)
- Geographic distribution for lower latency
- Redundancy for critical communications

### 5.3 Online Status Management

#### 5.3.1 Relay Manages User Status

Relays track online status of their registered users using heartbeat mechanism:

```java
public class UserStatusManager {
    private Map<String, UserOnlineStatus> userStatus;
    private HeartbeatConfig config;

    public void onUserConnect(String fid, String networkAddress) {
        userStatus.put(fid, new UserOnlineStatus(fid, networkAddress, ONLINE));
        // Report to addressing service
        addressingClient.updateStatus(fid, this.relayId, ONLINE);
    }

    public void onHeartbeat(String fid) {
        UserOnlineStatus status = userStatus.get(fid);
        if (status != null) {
            status.setLastHeartbeat(System.currentTimeMillis());
        }
    }

    public void onUserDisconnect(String fid) {
        userStatus.remove(fid);
        addressingClient.updateStatus(fid, this.relayId, OFFLINE);
    }

    // Scheduled task to detect timeout
    @Scheduled(fixedRate = 30000)
    public void checkHeartbeatTimeout() {
        long now = System.currentTimeMillis();
        for (UserOnlineStatus status : userStatus.values()) {
            if (now - status.getLastHeartbeat() > config.getTimeoutMs()) {
                onUserDisconnect(status.getFid());
            }
        }
    }
}

public class HeartbeatConfig {
    private long intervalMs = 30000;     // Heartbeat interval: 30s
    private long timeoutMs = 90000;      // Timeout: 90s (3 missed heartbeats)
}
```

#### 5.3.2 Addressing Service Manages Relay Status

Addressing services track online status of relays:

```java
public class RelayStatusManager {
    private Map<String, RelayOnlineStatus> relayStatus;

    public void onRelayHeartbeat(String relayId) {
        RelayOnlineStatus status = relayStatus.get(relayId);
        if (status != null) {
            status.setLastSeen(System.currentTimeMillis());
            status.setState(ONLINE);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void checkRelayStatus() {
        // Mark relays as offline if no heartbeat received
    }
}
```

### 5.4 Automatic Peer Discovery (Optional - Phase 7)

- Bootstrap nodes for initial peer discovery
- DHT-based lookup for FID → address resolution
- Gossip protocol for peer exchange

### 5.5 Addressing Service Integration

#### 5.5.1 Architecture

```
┌─────────────────────────────────────────────────────────┐
│              Blockchain (Service Registry)               │
│   - Relay services registered via FEIP protocol          │
│   - Addressing services registered via FEIP protocol     │
│   - Storage services registered via FEIP protocol        │
│   - Settlement services registered via FEIP protocol     │
└─────────────────────────────────────────────────────────┘
         ▲                    ▲                    ▲
         │                    │                    │
    ┌────┴────┐          ┌────┴────┐          ┌────┴────┐
    │Addressing│◄────────►│Addressing│◄────────►│Addressing│
    │Service A │  sync    │Service B │  sync    │Service C │
    └────┬────┘          └────┬────┘          └─────────┘
         │                    │
    ┌────┴────┐          ┌────┴────┐
    │ Relay X  │          │ Relay Y  │
    └────┬────┘          └────┬────┘
         │                    │
    ┌────┴────┐          ┌────┴────┐
    │ User A   │          │ User B   │
    └─────────┘          └─────────┘
```

**Key Principles:**
- Addressing services only store `FID → List<RelayInfo>` mapping (with status), NOT network addresses
- Relays store local user network addresses
- Users can register with multiple relays
- All requests are paid and encrypted via FUDP layer

#### 5.5.2 User Registration Flow

```
User Online Flow:
1. User A connects to Relay X (can connect to multiple relays)
2. User A provides: network address, storage service ID
3. Relay X stores locally: {FID_A, network_address, storage_id}
4. Relay X → Addressing Service: UPDATE(FID_A, RelayID_X, ONLINE)
5. Addressing Service stores the update

User Offline Flow:
1. Relay X detects A disconnected (heartbeat timeout)
2. Relay X → Addressing Service: UPDATE(FID_A, RelayID_X, OFFLINE)
```

#### 5.5.3 User Status Model

```java
public class UserStatus {
    private String fid;                        // User's FID
    private List<RelayStatus> relays;          // All relays user registered with
    private long lastUpdated;
}

public class RelayStatus {
    private String relayId;                    // Relay service ID
    private String relayEndpoint;              // Relay network address
    private UserState state;                   // ONLINE, OFFLINE
    private long lastSeen;                     // Last update timestamp
    private long tRate;                        // Relay's credit rating
    private long tCdd;                         // Relay's coin-day destroyed
    private long feePerKb;                     // Relay's fee rate
}

enum UserState {
    ONLINE,     // Connected to relay, direct delivery
    OFFLINE     // Disconnected, store to storage service
}
```

#### 5.5.4 Addressing Service Distributed Sync

**Key Design:** Sync and Query are separate operations. Queries only search locally.

```
┌─────────────────────────────────────────────────────────────┐
│                    Sync Flow (Background)                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Addressing A        Addressing B          Relay R          │
│      │                    │                   │             │
│      │ 1. Request sync    │                   │             │
│      │   (paid + encrypted)                   │             │
│      ├───────────────────>│                   │             │
│      │                    │                   │             │
│      │ 2. Return new/     │                   │             │
│      │    changed records │                   │             │
│      │<───────────────────┤                   │             │
│      │                    │                   │             │
│      │ 3. Verify with relay (paid + encrypted)│             │
│      ├────────────────────────────────────────>│             │
│      │                    │                   │             │
│      │ 4. Confirm/deny    │                   │             │
│      │<────────────────────────────────────────┤             │
│      │                    │                   │             │
│      │ 5. Save valid records                  │             │
│      │    Track B's verification stats        │             │
│      │    High failure rate → On-chain negative rating      │
│                                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    Query Flow (Real-time)                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Relay X              Addressing A                          │
│      │                    │                                 │
│      │ Query FID (paid)   │                                 │
│      ├───────────────────>│                                 │
│      │                    │                                 │
│      │                    │ Search local only               │
│      │                    │                                 │
│      │ Return relay list  │                                 │
│      │ with status        │                                 │
│      │<───────────────────┤                                 │
│      │                    │                                 │
│  No distributed query = No circular query risk              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### 5.5.5 Source Verification

Addressing services verify synced information directly with the source relay:

```java
public class AddressingSyncManager {
    private Map<String, PeerTrustStats> peerStats;

    public void syncFromPeer(String peerId) {
        // 1. Request sync data from peer addressing service
        List<UserStatus> updates = peerService.requestSync(lastSyncTime);

        // 2. Verify each record with the source relay
        for (UserStatus status : updates) {
            for (RelayStatus relayStatus : status.getRelays()) {
                boolean verified = verifyWithRelay(
                    relayStatus.getRelayId(),
                    status.getFid()
                );

                if (verified) {
                    localStore.save(status);
                    peerStats.get(peerId).incrementSuccess();
                } else {
                    peerStats.get(peerId).incrementFailure();
                }
            }
        }

        // 3. Check failure rate and submit on-chain negative rating if needed
        checkAndRatePeer(peerId);
    }

    private boolean verifyWithRelay(String relayId, String fid) {
        try {
            // Direct query to relay: "Is FID registered with you?"
            return relayClient.verifyRegistration(relayId, fid);
        } catch (TimeoutException e) {
            // Relay offline, add to pending verification queue
            pendingVerification.add(new PendingItem(relayId, fid));
            return false;
        }
    }

    private void checkAndRatePeer(String peerId) {
        PeerTrustStats stats = peerStats.get(peerId);
        if (stats.getFailureRate() > 0.1) {  // >10% failure rate
            // Submit on-chain negative rating to lower tRate
            chainClient.submitNegativeRating(peerId, "High false information rate");
            // Optionally suspend sync with this peer
        }
    }
}
```

#### 5.5.6 Addressing Service API

```java
public interface AddressingService {
    // Query (local only)
    UserStatus getStatus(String fid);
    List<UserStatus> batchGetStatus(List<String> fids);

    // Update (called by relays, paid + encrypted)
    void updateStatus(String fid, String relayId, UserState state);

    // Sync (between addressing services, paid + encrypted)
    List<UserStatus> requestSync(long sinceTimestamp);

    // Verification (called by other addressing services)
    boolean verifyRegistration(String fid);
}
```

#### 5.5.7 High Availability

```java
public class AddressingClient {
    private List<String> serviceEndpoints;  // Multiple addressing services
    private int currentIndex = 0;

    public UserStatus getStatus(String fid) {
        for (int i = 0; i < serviceEndpoints.size(); i++) {
            try {
                return queryService(serviceEndpoints.get(currentIndex), fid);
            } catch (Exception e) {
                // Failover to next service
                currentIndex = (currentIndex + 1) % serviceEndpoints.size();
            }
        }
        throw new AddressingServiceUnavailableException();
    }
}
```

---

## 6. Economic Mechanism

### 6.1 Chain Payment Model

**Core Principle:** Users only pay their directly connected relay. Relays pay each other with discounts.

```
User A ──100%──> Relay X ──60%──> Relay Y ──20%──> User B
  │                │                 │                 │
  │                │                 │                 └─ Receiver reward
  │                │                 └─ 40% profit (60% - 20%)
  │                └─ 40% profit (100% - 60%)
  └─ Pays full fee to direct relay

Two-hop example with 100 satoshi user fee:
├─ Relay X receives: 100 satoshi
├─ Relay X pays Relay Y: 60 satoshi (60% of user fee)
├─ Relay Y pays User B: 20 satoshi (receiver reward)
├─ Relay X profit: 40 satoshi
├─ Relay Y profit: 40 satoshi
└─ User B reward: 20 satoshi
```

**Benefits:**
- Symmetric incentives for both relays
- Simple pricing for users (only need to know direct relay's rate)
- Receiver reward incentivizes staying online
- When user is offline, receiver reward offsets storage cost

### 6.2 Fee Configuration

```java
public class RelayFeeConfig {
    private long baseFeePerKb;              // User fee rate (e.g., 100 satoshi/KB)
    private double interRelayDiscount;       // Discount for relay-to-relay (e.g., 0.6)
    private double receiverRewardRate;       // Receiver reward percentage (e.g., 0.2)
    private long storageFeePerKbDay;         // Storage service fee
}

public class FeeCalculator {
    private RelayFeeConfig config;

    public long calculateUserFee(int messageSizeKb) {
        return messageSizeKb * config.getBaseFeePerKb();
    }

    public long calculateInterRelayFee(long userFee) {
        return (long)(userFee * config.getInterRelayDiscount());
    }

    public long calculateReceiverReward(long userFee) {
        return (long)(userFee * config.getReceiverRewardRate());
    }

    public long calculateRelayProfit(long userFee, boolean isSenderRelay) {
        if (isSenderRelay) {
            // Sender relay: receives full fee, pays inter-relay fee
            return userFee - calculateInterRelayFee(userFee);
        } else {
            // Receiver relay: receives inter-relay fee, pays receiver reward
            return calculateInterRelayFee(userFee) - calculateReceiverReward(userFee);
        }
    }
}
```

### 6.3 Receiver Reward Mechanism

**Paid by Target Relay:** The receiver reward is paid by the target relay, not the sender.

```java
public class ReceiverRewardManager {
    private double rewardRate = 0.2;         // 20% of user fee
    private long minMessageSize = 100;       // Min size to qualify (bytes)

    public void processReward(String receiverFid, long userFee, int messageSize) {
        if (messageSize < minMessageSize) {
            return;  // Too small, no reward
        }

        long reward = (long)(userFee * rewardRate);
        accountManager.credit(receiverFid, reward, "receiver_reward");
    }
}
```

**When User is Offline:**
- No receiver reward is paid
- Target relay stores message to its designated storage service
- Storage cost ≈ receiver reward (helps determine pricing)

### 6.4 Settlement Service

**Independent on-chain registered settlement service** handles inter-relay payments.

#### 6.4.1 Settlement Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Settlement Flow                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Relay X          Settlement S         Relay Y              │
│      │                 │                   │                │
│      │ 1. Relay transaction with Payment ID                 │
│      ├─────────────────────────────────────>│                │
│      │                 │                   │                │
│      │ 2. Upload payment records (periodic)│                │
│      ├────────────────>│                   │                │
│      │                 │                   │                │
│      │                 │ 3. Upload payment records          │
│      │                 │<──────────────────┤                │
│      │                 │                   │                │
│      │                 │ 4. Internal reconciliation         │
│      │                 │                   │                │
│      │                 │ 5. Cross-settlement service sync   │
│      │                 │    (if different services)         │
│      │                 │                   │                │
│      │ 6. Settlement complete              │                │
│      │<────────────────┤                   │                │
│      │                 ├──────────────────>│                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### 6.4.2 Settlement Data Model

```java
public class PaymentRecord {
    private String paymentId;            // Unique payment ID
    private String senderRelayId;        // Relay that sent the message
    private String receiverRelayId;      // Relay that received
    private long amount;                 // Payment amount in satoshis
    private long timestamp;
    private String messageId;            // Related message ID
}

public class SettlementBatch {
    private String relayId;
    private String settlementServiceId;
    private List<PaymentRecord> records;
    private long periodStart;
    private long periodEnd;
    private byte[] signature;            // Relay signature
}
```

#### 6.4.3 Dispute Resolution

Settlement failures are resolved through on-chain reputation:

```java
public class DisputeHandler {
    public void handleSettlementFailure(String failedRelayId, String reason) {
        // 1. Record dispute
        disputeLog.add(new Dispute(failedRelayId, reason, System.currentTimeMillis()));

        // 2. If repeated failures, submit on-chain negative rating
        if (getRecentFailureCount(failedRelayId) > FAILURE_THRESHOLD) {
            chainClient.submitNegativeRating(failedRelayId,
                "Settlement failure: " + reason);
            // This lowers the relay's tRate
        }
    }
}
```

### 6.5 Trust System Integration

All services have on-chain verifiable credit values:

```java
public class ServiceTrust {
    private String serviceId;
    private long tRate;          // Comprehensive rating (from chain)
    private long tCdd;           // Total coin-day destroyed (from chain)

    // Used for relay selection
    public double calculateTrustScore() {
        // Weighted combination of tRate and tCdd
        return tRate * 0.7 + Math.log(tCdd + 1) * 0.3;
    }
}

public class RelaySelector {
    public String selectBestRelay(List<RelayStatus> candidates) {
        return candidates.stream()
            .filter(r -> r.getState() == ONLINE)
            .max(Comparator.comparingDouble(r -> {
                double trustScore = r.getTrustScore();
                double feeScore = 1.0 / (r.getFeePerKb() + 1);
                double latencyScore = 1.0 / (getCachedLatency(r.getRelayId()) + 1);

                // Weighted selection
                return trustScore * 0.4 + feeScore * 0.3 + latencyScore * 0.3;
            }))
            .map(RelayStatus::getRelayId)
            .orElse(null);
    }
}
```

### 6.6 Service Registration (FEIP Protocol)

**Relay Service:**
```json
{
  "type": "FEIP",
  "sn": "Service",
  "ver": "1",
  "name": "FUDP_Relay",
  "data": {
    "op": "publish",
    "stdName": "FUDP Relay Service",
    "params": {
      "endpoint": "relay.example.com:8400",
      "baseFeePerKb": 100,
      "interRelayDiscount": 0.6,
      "receiverRewardRate": 0.2,
      "maxMessageSize": 1048576,
      "settlementServiceId": "SETTLEMENT_SERVICE_ID",
      "storageServiceId": "STORAGE_SERVICE_ID"
    }
  }
}
```

**Settlement Service:**
```json
{
  "type": "FEIP",
  "sn": "Service",
  "ver": "1",
  "name": "FUDP_Settlement",
  "data": {
    "op": "publish",
    "stdName": "FUDP Settlement Service",
    "params": {
      "endpoint": "settlement.example.com:8401",
      "settlementPeriodHours": 1,
      "supportedRelays": ["*"]
    }
  }
}
```

**Addressing Service:**
```json
{
  "type": "FEIP",
  "sn": "Service",
  "ver": "1",
  "name": "FUDP_Addressing",
  "data": {
    "op": "publish",
    "stdName": "FUDP Addressing Service",
    "params": {
      "endpoint": "addressing.example.com:8402",
      "queryFeePerRequest": 10,
      "syncFeePerRecord": 5
    }
  }
}
```

### 6.7 Implementation Note

**Service-side implementations** (Relay Server, Settlement Server, Addressing Server, Storage Server) will be implemented in separate modules. This document focuses on the **protocol design** and **client-side node implementation**.

---

## 7. CLI Interface

### 7.1 Commands

```
FUDP Node CLI

Usage: fudpnode [OPTIONS] COMMAND [ARGS...]

Options:
  -k, --key <file>       Private key file (default: ~/.fudp/key.dat)
  -p, --port <port>      Listen port (default: 8400)
  -c, --config <file>    Config file (default: ~/.fudp/config.json)
  -v, --verbose          Verbose output

Commands:
  start                  Start the node
  stop                   Stop the node

  peer add <fid> <host:port> [alias]   Add a peer
  peer remove <fid|alias>              Remove a peer
  peer list                            List known peers
  peer info <fid|alias>                Show peer details

  chat <fid|alias> <message>           Send chat message
  chat-to <fid|alias>                  Enter chat mode with peer

  request <fid|alias> <service> <data> Send request

  file send <fid|alias> <filepath>     Send file
  file list                            List active transfers
  file cancel <transfer-id>            Cancel transfer

  info                                 Show node info (FID, pubkey, etc.)
  status                               Show connection status

  keygen                               Generate new key pair
```

### 7.2 Interactive Mode

```
> start
Node started on port 8400
Local FID: FHLxxxxxyyyyzzzzz...

> peer add FAbcd... 192.168.1.100:8400 alice
Peer added: alice (FAbcd...)

> chat alice Hello!
Message sent to alice

[alice]: Hi there!

> file send alice ./document.pdf
File offer sent: document.pdf (2.3 MB)
[alice] accepted file transfer
Progress: [████████░░░░░░░░░░░░] 40% (920 KB / 2.3 MB)
...
Transfer complete: document.pdf

> status
Connected peers: 2
  - alice (FAbcd...) - connected, 5ms RTT
  - bob (FBxyz...) - connected, 12ms RTT

Active transfers: 0

> stop
Node stopped.
```

---

## 8. Configuration

### 8.1 NodeConfig

```java
public class NodeConfig {
    // Network
    private int port = 8400;
    private String bindAddress = "0.0.0.0";

    // File Transfer
    private int chunkSize = 32768;           // 32KB
    private String downloadDir = "~/Downloads/fudp";
    private boolean autoAcceptFiles = false;
    private long maxFileSize = 1073741824;   // 1GB

    // Timeouts
    private long requestTimeoutMs = 30000;   // 30 seconds
    private long transferTimeoutMs = 300000; // 5 minutes

    // Storage
    private String dataDir = "~/.fudp";

    // Logging
    private String logLevel = "INFO";
}
```

### 8.2 Config File (~/.fudp/config.json)

```json
{
  "port": 8400,
  "bindAddress": "0.0.0.0",
  "chunkSize": 32768,
  "downloadDir": "~/Downloads/fudp",
  "autoAcceptFiles": false,
  "maxFileSize": 1073741824,
  "requestTimeoutMs": 30000,
  "transferTimeoutMs": 300000,
  "logLevel": "INFO"
}
```

---

## 9. Implementation Phases

### Phase 1: Core Node (Foundation)
**Goal:** Basic node with chat messaging

**Tasks:**
1. Create `FudpNode` wrapper around `Protocol`
2. Implement `MessageCodec` for serialization
3. Implement `ChatMessage` and `ChatHandler`
4. Implement `PeerBook` for peer storage
5. Create basic CLI with start/stop/chat commands
6. Write unit tests

**Deliverables:**
- Two nodes can exchange chat messages
- Peers persist across restarts
- Basic CLI operational

---

### Phase 2: Request/Response
**Goal:** Application-level request/response pattern

**Tasks:**
1. Implement `RequestMessage`, `ResponseMessage`
2. Implement `RequestHandler` with timeout handling
3. Add pending request tracking with `CompletableFuture`
4. Add service name routing
5. Add CLI request command
6. Write integration tests

**Deliverables:**
- Nodes can exchange request/response
- Timeout handling works
- Service name routing operational

---

### Phase 3: File Transfer
**Goal:** Reliable file transfer with resume

**Tasks:**
1. Implement `FileOffer`, `FileChunk`, `FileComplete` messages
2. Implement `FileChunker` for splitting files
3. Implement `FileAssembler` for reassembly
4. Implement `TransferManager` for state tracking
5. Add progress callbacks
6. Add hash verification
7. Implement resume capability
8. Add CLI file commands
9. Write file transfer tests

**Deliverables:**
- Large file transfer works reliably
- Progress reporting functional
- Resume after disconnect works
- Integrity verification passes

---

### Phase 4: Data Relay (Basic)
**Goal:** Basic relay functionality with static routing

**Tasks:**
1. Implement `RELAY`, `RELAY_ACK`, `RELAY_FAIL` messages
2. Implement `RelayHandler` for message forwarding
3. Implement basic account management (balance check/deduct)
4. Add static route configuration
5. Add CLI relay commands
6. Implement offline storage integration
7. Write relay tests

**Deliverables:**
- Messages can be relayed between nodes
- Basic billing works
- Offline messages stored to configured storage service

---

### Phase 5: Addressing & Economics
**Goal:** Dynamic routing and complete economic mechanism

**Tasks:**
1. Implement `AddressingClient` for service queries
2. Implement user registration/status updates
3. Implement `RELAY_QUERY`/`RELAY_QUOTE` for guaranteed mode
4. Implement receiver reward mechanism
5. Add fee calculation and display
6. Implement inter-relay settlement
7. Add addressing service failover
8. Documentation for relay operators

**Deliverables:**
- Dynamic routing via addressing service
- Complete economic mechanism functional
- Receiver rewards operational
- Guaranteed delivery mode works

---

### Phase 6: Polish & Production
**Goal:** Production-ready node

**Tasks:**
1. Add compression for messages
2. Add broadcast messaging
3. Improve error handling and recovery
4. Add connection health monitoring
5. Add bandwidth throttling
6. Add logging and metrics
7. Complete documentation
8. Performance optimization
9. Security audit

**Deliverables:**
- Production-ready node application
- Complete documentation
- Performance benchmarks

---

### Phase 7: Advanced Features (Optional)
**Goal:** Enhanced functionality

**Tasks:**
1. Peer discovery (DHT/bootstrap)
2. Group messaging
3. GUI application
4. Integration with APIP/DISK services
5. Mobile SDK
6. Onion routing for enhanced privacy

---

## 10. Project Structure

### 10.1 New Files to Create

```
FC-JDK/src/main/java/fudp/
├── node/
│   ├── FudpNode.java
│   ├── NodeConfig.java
│   ├── PeerBook.java
│   ├── Peer.java
│   ├── UserRegistration.java      # Multi-relay registration
│   └── NodeEventListener.java
├── message/
│   ├── AppMessage.java
│   ├── MessageType.java
│   ├── MessageCodec.java
│   ├── ChatMessage.java
│   ├── RequestMessage.java
│   ├── ResponseMessage.java
│   ├── FileMessage.java
│   └── RelayMessage.java          # Relay message with fee threshold
├── handler/
│   ├── MessageHandler.java
│   ├── ChatHandler.java
│   ├── RequestHandler.java
│   ├── FileHandler.java
│   └── RelayHandler.java          # Relay message processing
├── transfer/
│   ├── FileTransfer.java
│   ├── FileChunker.java
│   ├── FileAssembler.java
│   ├── TransferState.java
│   └── TransferManager.java
├── relay/
│   ├── RelayRouter.java           # Routing logic with caching
│   ├── RelayCache.java            # Cache for relay/FID info
│   ├── RelaySelector.java         # Select best relay by trust/fee/latency
│   ├── AddressingClient.java      # Addressing service client
│   ├── UserStatus.java            # User online status
│   └── RelayStatus.java           # Relay status with tRate/tCdd
├── economics/                     # Application plugin for billing (e.g., FAPI); not part of transport core
│   ├── FeeCalculator.java         # Fee computation with chain payment
│   ├── FeeThresholdChecker.java   # Check max fee threshold
│   ├── ReceiverRewardManager.java # Receiver reward calculation
│   ├── PaymentRecord.java         # Payment record for settlement
│   └── SettlementClient.java      # Settlement service client
├── trust/
│   ├── ServiceTrust.java          # tRate/tCdd from chain
│   ├── TrustScorer.java           # Calculate trust scores
│   └── ChainClient.java           # Query chain for trust data
├── status/
│   ├── UserStatusManager.java     # Manage user online status
│   ├── HeartbeatConfig.java       # Heartbeat configuration
│   └── OnlineStatus.java          # Online/offline state
└── cli/
    ├── NodeCli.java
    ├── StartNode.java             # Main entry point
    ├── CommandParser.java
    └── ConsoleOutput.java

FC-JDK/src/test/java/fudp/
├── node/
│   ├── FudpNodeTest.java
│   └── PeerBookTest.java
├── message/
│   └── MessageCodecTest.java
├── handler/
│   └── RequestHandlerTest.java
├── transfer/
│   └── FileTransferTest.java
├── relay/
│   ├── RelayRouterTest.java
│   ├── RelaySelectorTest.java
│   └── AddressingClientTest.java
├── economics/
│   ├── FeeCalculatorTest.java
│   └── FeeThresholdTest.java
├── trust/
│   └── TrustScorerTest.java
└── integration/
    ├── ChatIntegrationTest.java
    ├── RequestIntegrationTest.java
    ├── FileTransferIntegrationTest.java
    └── RelayIntegrationTest.java
```

---

## 11. Usage Examples

### 11.1 Programmatic Usage

```java
// Initialize node
byte[] privateKey = KeyTools.generatePrivateKey();
NodeConfig config = new NodeConfig();
config.setPort(8400);
config.setDownloadDir("/tmp/fudp");

FudpNode node = new FudpNode(privateKey, config);

// Set event listener
node.setEventListener(new NodeEventListener() {
    @Override
    public void onChatReceived(String peerId, String message) {
        System.out.println("[" + peerId.substring(0, 8) + "]: " + message);
    }

    @Override
    public void onRequestReceived(String peerId, long requestId, byte[] data) {
        // Process request and respond
        byte[] response = processRequest(data);
        node.respond(requestId, response);
    }

    @Override
    public void onFileOfferReceived(String peerId, FileOffer offer) {
        // Auto-accept files under 10MB
        if (offer.getFileSize() < 10_000_000) {
            node.acceptFile(offer.getTransferId());
        }
    }

    @Override
    public void onFileComplete(String transferId, String filePath) {
        System.out.println("File saved: " + filePath);
    }
});

// Start node
node.start();

// Add peer
node.addPeer("FAbcd1234...", "192.168.1.100", 8400);

// Send chat
node.sendChat("FAbcd1234...", "Hello from FUDP!");

// Send request
CompletableFuture<ResponseMessage> future = node.request(
    "FAbcd1234...",
    "user.profile",
    "{\"action\": \"get\"}".getBytes()
);
ResponseMessage response = future.get(30, TimeUnit.SECONDS);

// Send file
FileTransfer transfer = node.sendFile("FAbcd1234...", "/path/to/file.pdf");
transfer.onProgress((transferred, total) -> {
    System.out.printf("Progress: %d%%\n", transferred * 100 / total);
});
transfer.await();  // Wait for completion

// Stop node
node.stop();
```

### 11.2 CLI Usage

```bash
# Generate key pair
$ fudpnode keygen
Private key saved to: ~/.fudp/key.dat
Public key: 02abc123...
FID: FHLxyzabc...

# Start node
$ fudpnode start -p 8400
Node started on port 8400
Local FID: FHLxyzabc...

# In another terminal, add peer and chat
$ fudpnode peer add FAbcd... 192.168.1.100:8400 alice
Peer added: alice

$ fudpnode chat alice "Hello!"
Message sent.

# Send file
$ fudpnode file send alice ./report.pdf
Sending: report.pdf (5.2 MB)
Progress: [████████████████████] 100%
Transfer complete!
```

---

## 12. Integration with Freeverse Ecosystem

### 12.1 Future Integrations

- **APIP Integration** - Query APIP services via FUDP
- **DISK Integration** - Request files stored in DISK service
- **Identity** - Use CID (Crypto ID) for enhanced peer naming
- **Payment** - FCH micropayments for premium features

### 12.2 Service Discovery

Future: Use blockchain to publish node endpoints:
```json
{
  "type": "FEIP",
  "sn": "FUDP",
  "ver": "1",
  "name": "FudpService",
  "op": "publish",
  "data": {
    "fid": "FHLxyzabc...",
    "host": "node.example.com",
    "port": 8400,
    "services": ["chat", "file", "relay"]
  }
}
```

---

## 13. Summary

This plan provides a comprehensive roadmap for building a FUDP Node application that enables:

1. **Real-time messaging** with delivery confirmation
2. **Request/response patterns** for application APIs
3. **Reliable file transfer** with resume and integrity checking
4. **Data relay** with chain payment model (no pre-payment, fee threshold)
5. **Multi-relay registration** for high availability
6. **Trust-based relay selection** using on-chain tRate and tCdd
7. **Settlement service** for inter-relay payment reconciliation
8. **Peer management** with persistent storage
9. **CLI and programmatic interfaces**
10. **Performance monitoring** - RTT, packet loss, retransmit rate, congestion control status

**Key Design Principles:**
- **No pre-payment:** Users set max fee per KB, relay stops if exceeded
- **Chain payment:** User → Sender Relay (100%) → Receiver Relay (60%) → Receiver (20%)
- **On-chain trust:** All services use tRate/tCdd for reputation
- **Source verification:** Addressing services verify synced data with source relay
- **Economic incentives:** Symmetric relay profits, receiver rewards

The implementation is divided into 7 phases, building incrementally from basic chat to full relay capability with economic mechanisms. Each phase produces a working deliverable that can be tested and used independently.

**Note:** Service-side implementations (Relay Server, Settlement Server, Addressing Server) will be in separate modules.

---

## 14. Design Decisions Made

The following questions have been resolved through discussion:

### Architecture & System Design

1. **Trust System** ✅ - Use on-chain tRate (credit rating) and tCdd (coin-day destroyed) for all services

2. **Multi-Relay Registration** ✅ - Users can register with multiple relays for better availability

3. **Online Status Management** ✅ - Relays manage user status (heartbeat), addressing services manage relay status

4. **Addressing Service Sync** ✅ - Separate sync and query operations; verify with source relay; on-chain negative rating for bad actors

5. **Payment Model** ✅ - No pre-payment; users set max fee threshold; chain payment with inter-relay discount (60%)

6. **Receiver Reward** ✅ - Paid by target relay (20% of user fee); offsets storage cost when user offline

7. **Settlement** ✅ - Independent on-chain settlement service; disputes resolved via tRate degradation

8. **Storage** ✅ - Designated by relay; relay pays storage fee; user retrieves free (identity verified via FUDP)

9. **Service Implementation** ✅ - Server-side services implemented in separate modules; this document focuses on protocol and client

### Node Implementation Decisions

10. **Peer Discovery** ✅ - Manual configuration only for this module. A separate Node Management module will be added later for automatic discovery.

11. **Message Persistence** ✅ - Chat at node layer is temporary messaging mechanism only. Provides API interface, no console output, no persistence. Upper layers handle persistence if needed.

12. **Group Messaging** ✅ - Not implemented at this layer. A separate Talk service will be designed using request/response message type.

13. **NAT Traversal** ✅ - Implement STUN/TURN functionality via relay nodes. See Section 15 for design details.

14. **Rate Limiting** ✅ - Node layer only provides peer quality feedback interface. Rating system implemented at upper layer.

15. **Code Organization** ✅ - Keep within FUDP module but use independent package structure:
    - `fudp.node.*` - Core node functionality
    - `fudp.message.*` - Application-level message protocol
    - `fudp.handler.*` - Message handlers
    - `fudp.transfer.*` - File transfer
    - `fudp.relay.*` - Relay functionality
    - `fudp.cli.*` - CLI (separate, optional dependency)

16. **Logging System** ✅ - Use SLF4J as logging facade for cross-platform compatibility:
    - Desktop/Server: Logback implementation
    - Android: android-logger-slf4j implementation
    - Log levels configurable via NodeConfig

17. **UI Separation** ✅ - CLI in separate `fudp.cli.*` package. Core node functionality usable as library without CLI dependencies.

18. **Application Message Protocol** ✅ - In separate `fudp.message.*` package, already shown in project structure.

---

## 15. NAT Traversal Design

### 15.1 Overview

NAT traversal is implemented using relay nodes as intermediaries, similar to TURN (Traversal Using Relays around NAT). When two peers behind NAT cannot establish direct connection, they communicate through a relay node.

### 15.2 Connection Flow

```
┌─────────────────────────────────────────────────────────────┐
│                   NAT Traversal via Relay                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Peer A (NAT)      Relay R          Addressing      Peer B (NAT)
│      │                │                 │                │
│      │ 1. Connect to relay              │                │
│      ├───────────────>│                 │                │
│      │                │                 │                │
│      │ 2. Register FID_A                │                │
│      │    as reachable via Relay R      │                │
│      │                ├────────────────>│                │
│      │                │                 │                │
│      │                │                 │<───────────────┤
│      │                │                 │ 3. Register    │
│      │                │                 │    FID_B       │
│      │                │                 │                │
│      │ 4. Want to reach FID_B           │                │
│      │    Query addressing service      │                │
│      ├─────────────────────────────────>│                │
│      │                │                 │                │
│      │ 5. Return: FID_B reachable       │                │
│      │    via Relay R                   │                │
│      │<─────────────────────────────────┤                │
│      │                │                 │                │
│      │ 6. Send message to FID_B via R   │                │
│      ├───────────────>│                 │                │
│      │                │ 7. Relay to B   │                │
│      │                ├────────────────────────────────>│
│      │                │                 │                │
│      │                │<────────────────────────────────┤
│      │                │ 8. Response     │                │
│      │<───────────────┤                 │                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 15.3 Message Types for NAT Traversal

**Note:** These are **application-level message types** (Section 2.2), NOT protocol-level frames. They are transmitted as STREAM data using the existing FUDP protocol.

```java
enum MessageType {
    // ... existing types (CHAT, REQUEST, FILE_*, etc.) ...

    // NAT Traversal (Application Layer Messages)
    NAT_REGISTER(0x50),        // Register with relay for NAT traversal
    NAT_KEEPALIVE(0x51),       // Keep NAT mapping alive
    NAT_PROBE(0x52),           // Probe for direct connectivity
    NAT_PROBE_RESPONSE(0x53),  // Response to connectivity probe
}

// These messages are sent using the standard application message format:
// [MessageType][MessageID][Flags][PayloadLength][Payload]
// and transmitted via Protocol.send(stream, encodedMessage)
```

### 15.4 NAT Registration

```java
public class NatRegistration {
    private String relayId;              // Relay service ID
    private String internalAddress;      // Internal IP:port (for diagnostics)
    private long keepaliveIntervalMs;    // Interval for keepalive messages
    private long registrationTime;
}

public class NatManager {
    private Map<String, NatRegistration> registrations;
    private ScheduledExecutorService keepaliveScheduler;

    public void registerWithRelay(String relayId) {
        // 1. Establish connection to relay
        // 2. Send NAT_REGISTER message
        // 3. Relay reports our status to addressing service
        // 4. Schedule keepalive messages
    }

    private void sendKeepalive(String relayId) {
        // Send NAT_KEEPALIVE to maintain NAT mapping
        // Typical interval: 25-30 seconds
    }

    public void attemptDirectConnection(String peerId) {
        // 1. Get peer's public address from relay
        // 2. Send NAT_PROBE directly
        // 3. If successful, establish direct connection
        // 4. If failed, continue using relay
    }
}
```

### 15.5 Direct Connection Probing (Optional Optimization)

After initial relay-based communication, peers can attempt to establish direct connections:

```
Peer A                  Relay R                  Peer B
  │                        │                        │
  │ 1. Request B's public  │                        │
  │    address             │                        │
  ├───────────────────────>│                        │
  │                        │                        │
  │ 2. Return B's mapped   │                        │
  │    address             │                        │
  │<───────────────────────┤                        │
  │                        │                        │
  │ 3. NAT_PROBE direct    │                        │
  ├────────────────────────────────────────────────>│
  │                        │                        │
  │ 4. NAT_PROBE_RESPONSE  │                        │
  │    (if NAT allows)     │                        │
  │<────────────────────────────────────────────────┤
  │                        │                        │
  │ 5. Direct communication (bypassing relay)       │
  │<───────────────────────────────────────────────>│
```

### 15.6 Integration with FudpNode

```java
public class FudpNode {
    private final NatManager natManager;
    private final boolean behindNat;

    public FudpNode(byte[] privateKey, NodeConfig config) {
        // ...
        this.behindNat = config.isBehindNat();
        if (behindNat) {
            this.natManager = new NatManager(protocol, config);
        }
    }

    public void start() {
        // ...
        if (behindNat && config.getRelayIds() != null) {
            for (String relayId : config.getRelayIds()) {
                natManager.registerWithRelay(relayId);
            }
        }
    }

    public void sendMessage(String peerId, AppMessage message) {
        if (canReachDirectly(peerId)) {
            // Send directly
            protocol.send(peerId, message);
        } else {
            // Send via relay
            relayRouter.relay(peerId, message);
        }
    }
}
```

### 15.7 NodeConfig NAT Settings

```java
public class NodeConfig {
    // ... existing fields ...

    // NAT Traversal
    private boolean behindNat = false;
    private List<String> relayIds;           // Relays to register with
    private long natKeepaliveMs = 25000;     // Keepalive interval
    private boolean attemptDirectConnect = true;  // Try direct after relay
}
```

### 15.8 NodeConfig Balance Settings (Credit Limit)

- `defaultCreditLimit` 默认为 0 时不再固定为 10000，而是在启动时动态计算：`max(pricePerKb * creditLimitPriceMultiplier, creditLimitMinSats)`。
- 新增参数：
  - `creditLimitMinSats`：信用额度下限（默认 10000 satoshi）
  - `creditLimitPriceMultiplier`：基于 `pricePerKb` 的倍数（默认 1000，对应约 1MB 响应额度）
- 仍可通过显式设置 `defaultCreditLimit > 0` 覆盖动态计算。
- 目的：当链上价格上调时，默认额度仍能覆盖基础响应成本，避免因额度过低导致请求被拒。

---

## 16. Logging Architecture

### 16.1 SLF4J Integration

```java
// All classes use SLF4J
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FudpNode {
    private static final Logger logger = LoggerFactory.getLogger(FudpNode.class);

    public void start() {
        logger.info("Starting FUDP node on port {}", config.getPort());
        // ...
        logger.debug("Node started with config: {}", config);
    }
}
```

### 16.2 Log Levels

```
TRACE - Detailed protocol-level information (packet contents, state transitions)
DEBUG - Diagnostic information (connection events, message routing)
INFO  - Normal operational events (node start/stop, peer connect/disconnect)
WARN  - Potential issues (timeout, retry, degraded performance)
ERROR - Failures requiring attention (connection failed, transfer error)
```

### 16.3 Platform-Specific Configuration

**Desktop/Server (logback.xml):**
```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>${user.home}/.fudp/logs/fudp.log</file>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

**Android (in Application class):**
```java
// Use android-logger-slf4j
LoggerFactory.getLogger("fudp").info("FUDP initialized");
// Logs to Android Logcat
```

### 16.4 NodeConfig Logging Settings

```java
public class NodeConfig {
    // ... existing fields ...

    // Logging
    private String logLevel = "INFO";
    private String logDir = "~/.fudp/logs";
    private int maxLogFiles = 7;              // Rotation
    private long maxLogSizeBytes = 10485760;  // 10MB per file
}
```

---

## 17. Updated Project Structure

### 17.1 Package Organization

```
FC-JDK/src/main/java/fudp/
├── node/                          # Core node (no UI dependencies)
│   ├── FudpNode.java              # Main node entry point
│   ├── NodeConfig.java            # Node configuration
│   ├── NodeStats.java             # Performance statistics (RTT, loss, throughput)
│   ├── PeerBook.java              # Known peers storage
│   ├── Peer.java                  # Peer information
│   ├── UserRegistration.java      # Multi-relay registration
│   ├── NodeEventListener.java     # Event callback interface
│   └── NatManager.java            # NAT traversal management
├── metrics/                       # Transport metering
│   ├── MeterListener.java         # Metering event listener interface
│   ├── MeterRecord.java           # Metering record (bytes, RTT, direction)
│   └── MeterDirection.java        # INBOUND/OUTBOUND enum
├── congestion/                    # Congestion control and RTT estimation
│   ├── CongestionControl.java     # CUBIC-based congestion control
│   └── RttEstimator.java          # RTT estimation (EWMA algorithm)
├── message/                       # Application-level message protocol
│   ├── AppMessage.java            # Base application message
│   ├── MessageType.java           # Message type enum
│   ├── MessageCodec.java          # Serialization/deserialization
│   ├── ChatMessage.java           # Chat message
│   ├── RequestMessage.java        # Request message
│   ├── ResponseMessage.java       # Response message
│   ├── FileMessage.java           # File transfer messages
│   └── RelayMessage.java          # Relay message with fee threshold
├── handler/                       # Message handlers
│   ├── MessageHandler.java        # Routes incoming messages
│   ├── ChatHandler.java           # Chat message handling
│   ├── RequestHandler.java        # Request/response handling
│   ├── FileHandler.java           # File transfer handling
│   └── RelayHandler.java          # Relay message processing
├── transfer/                      # File transfer
│   ├── FileTransfer.java          # Active transfer state
│   ├── FileChunker.java           # File chunking logic
│   ├── FileAssembler.java         # Chunk reassembly
│   ├── TransferState.java         # Transfer state enum
│   └── TransferManager.java       # Manages all transfers
├── relay/                         # Relay functionality
│   ├── RelayRouter.java           # Routing logic with caching
│   ├── RelayCache.java            # Cache for relay/FID info
│   ├── RelaySelector.java         # Select best relay
│   ├── AddressingClient.java      # Addressing service client
│   ├── UserStatus.java            # User online status
│   └── RelayStatus.java           # Relay status with trust
├── economics/                     # Economic mechanism
│   ├── FeeCalculator.java         # Fee computation
│   ├── FeeThresholdChecker.java   # Check max fee threshold
│   ├── ReceiverRewardManager.java # Receiver reward calculation
│   ├── PaymentRecord.java         # Payment record
│   └── SettlementClient.java      # Settlement service client
├── trust/                         # Trust system
│   ├── ServiceTrust.java          # tRate/tCdd from chain
│   ├── TrustScorer.java           # Calculate trust scores
│   ├── PeerQualityReporter.java   # Report peer quality (for upper layer)
│   └── ChainClient.java           # Query chain for trust data
├── status/                        # Status management
│   ├── UserStatusManager.java     # Manage user online status
│   ├── HeartbeatConfig.java       # Heartbeat configuration
│   └── OnlineStatus.java          # Online/offline state
└── cli/                           # CLI (separate, optional)
    ├── NodeCli.java               # CLI main class
    ├── StartNode.java             # Main entry point
    ├── CommandParser.java         # Command parsing
    └── ConsoleOutput.java         # Output formatting

FC-JDK/src/main/resources/
├── logback.xml                    # Default logging config
└── fudp-default-config.json       # Default node config
```

### 17.2 Maven Dependencies

```xml
<dependencies>
    <!-- Logging -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.9</version>
    </dependency>

    <!-- Desktop/Server logging implementation -->
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.4.11</version>
        <optional>true</optional>
    </dependency>

    <!-- Existing dependencies... -->
</dependencies>
```

### 17.3 Separation of CLI

The CLI can be built as a separate artifact or included optionally:

```xml
<profiles>
    <profile>
        <id>with-cli</id>
        <dependencies>
            <!-- CLI-specific dependencies like JLine for terminal -->
            <dependency>
                <groupId>org.jline</groupId>
                <artifactId>jline</artifactId>
                <version>3.23.0</version>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

---

## 18. Peer Quality Feedback Interface

As decided, the node layer provides a feedback interface for peer quality, with rating system implemented at upper layer.

```java
public interface PeerQualityReporter {
    /**
     * Report a successful interaction with peer
     */
    void reportSuccess(String peerId, InteractionType type, long latencyMs);

    /**
     * Report a failed interaction with peer
     */
    void reportFailure(String peerId, InteractionType type, String reason);

    /**
     * Report peer behavior (spam, invalid messages, etc.)
     */
    void reportBehavior(String peerId, BehaviorType behavior);

    /**
     * Get current quality metrics for a peer
     */
    PeerQualityMetrics getMetrics(String peerId);
}

public enum InteractionType {
    MESSAGE_SEND,
    MESSAGE_RECEIVE,
    FILE_TRANSFER,
    REQUEST_RESPONSE,
    RELAY
}

public enum BehaviorType {
    SPAM,
    INVALID_MESSAGE,
    TIMEOUT,
    CONNECTION_REFUSED,
    PROTOCOL_VIOLATION
}

public class PeerQualityMetrics {
    private String peerId;
    private int totalInteractions;
    private int successCount;
    private int failureCount;
    private double averageLatencyMs;
    private List<BehaviorType> recentBehaviors;
    private long lastInteractionTime;
}
```

The upper layer (rating system) can use these metrics to:
- Calculate comprehensive peer scores
- Implement blacklisting/whitelisting
- Adjust connection priorities
- Generate on-chain ratings

---

## 19. Performance Monitoring

### 19.1 Overview

FUDP Node provides comprehensive performance monitoring for evaluating network quality, diagnosing connection issues, and optimizing transport performance.

### 19.2 NodeStats

Aggregated node-level statistics accessible via `node.getNodeStats()`:

```java
public class NodeStats {
    // Connection stats
    int totalConnections;
    int establishedConnections;
    
    // Packet stats (aggregated)
    long totalPacketsSent;
    long totalPacketsReceived;
    long totalBytesOut;
    long totalBytesIn;
    
    // Loss stats
    long totalRetransmits;
    long totalSuspectedLost;      // Packets that triggered retransmit
    long totalAckedAfterLost;     // False positives (later ACKed)
    long totalEffectiveLost;      // Real loss = suspected - recovered
    double averageLossRate;
    double averageRetransmitRate;
    
    // RTT stats (aggregated)
    long minRttMs;
    long avgSmoothedRttMs;
    long maxRttMs;
    
    // Congestion control
    long totalCongestionWindow;
    long totalBytesInFlight;
    
    // Per-peer stats
    List<PeerStats> peerStatsList;
}
```

### 19.3 Per-Peer Statistics

Detailed statistics for each connected peer:

```java
public class PeerStats {
    String peerId;
    ConnectionState state;
    
    // Packets
    long packetsSent;
    long packetsReceived;
    long bytesOut;
    long bytesIn;
    
    // Loss
    long retransmitCount;
    double retransmitRate;
    long suspectedLostCount;
    long ackedAfterSuspectedLost;
    long effectiveLostCount;
    double lossRate;
    
    // RTT
    long smoothedRttMs;
    long minRttMs;
    long rttVarianceMs;
    long rtoMs;
    
    // Congestion control
    long congestionWindow;
    long bytesInFlight;
    CongestionControl.State ccState;  // SLOW_START, CONGESTION_AVOIDANCE, RECOVERY
}
```

### 19.4 Loss Detection

Loss detection uses the following parameters:

| Parameter | Value | Description |
|-----------|-------|-------------|
| `TIME_THRESHOLD_MULTIPLIER` | 1.5 | Time threshold multiplier (QUIC default: 1.125) |
| `MIN_TIME_THRESHOLD_MS` | 50ms | Minimum time threshold |
| `PACKET_THRESHOLD` | 3 | Packet reordering threshold |

Time threshold formula:
```
timeThreshold = max(MIN_TIME_THRESHOLD, smoothedRtt × 1.5 + rttVariance)
```

**Important:** Only ACK-eliciting packets are tracked for loss detection. ACK-only packets do not require acknowledgment and are excluded from loss statistics.

### 19.5 MeterListener Interface

For transport-layer metering (used by economics layer):

```java
public interface MeterListener {
    void onMeter(MeterRecord record);
}

public class MeterRecord {
    String peerId;
    Long streamId;
    MessageType messageType;
    MeterDirection direction;  // INBOUND or OUTBOUND
    long payloadBytes;
    long sendTimestampMillis;
    long receiveTimestampMillis;
    Long rttMicros;
    int retransmitCount;
    Double lossRateHint;
}
```

### 19.6 Network Quality Assessment

Recommended thresholds for evaluating network quality:

| Metric | Excellent | Good | Fair | Poor |
|--------|-----------|------|------|------|
| RTT | < 50ms | 50-100ms | 100-200ms | > 200ms |
| Retransmit Rate | < 1% | 1-5% | 5-10% | > 10% |
| Loss Rate | 0% | < 1% | 1-5% | > 5% |

---

## 20. Error Handling and Recovery

### 20.1 Error Types

```java
public enum NodeErrorCode {
    // Connection errors
    PEER_NOT_FOUND(1001),           // Peer not in PeerBook
    CONNECTION_FAILED(1002),        // Unable to connect
    CONNECTION_TIMEOUT(1003),       // Connection timed out
    CONNECTION_CLOSED(1004),        // Peer closed connection

    // Message errors
    MESSAGE_TOO_LARGE(2001),        // Exceeds max message size
    INVALID_MESSAGE(2002),          // Malformed message
    DECRYPTION_FAILED(2003),        // Unable to decrypt

    // Transfer errors
    FILE_NOT_FOUND(3001),           // File does not exist
    TRANSFER_CANCELLED(3002),       // Transfer was cancelled
    TRANSFER_TIMEOUT(3003),         // Transfer timed out
    HASH_MISMATCH(3004),            // File integrity check failed

    // Relay errors
    RELAY_FAILED(4001),             // Relay delivery failed
    FEE_EXCEEDED(4002),             // Fee threshold exceeded
    TARGET_OFFLINE(4003),           // Target user offline
}
```

### 20.2 Error Recovery Strategies

#### 20.2.1 Connection Recovery

```java
public class ConnectionRecovery {
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long RECONNECT_DELAY_MS = 1000;

    public void attemptRecovery(String peerId, NodeErrorCode error) {
        switch (error) {
            case CONNECTION_TIMEOUT, CONNECTION_CLOSED -> {
                // Attempt reconnection with exponential backoff
                for (int i = 0; i < MAX_RECONNECT_ATTEMPTS; i++) {
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS * (1 << i));
                        reconnect(peerId);
                        return;
                    } catch (Exception e) {
                        // Continue to next attempt
                    }
                }
                // Report failure to quality tracker
                qualityReporter.reportFailure(peerId, InteractionType.MESSAGE_SEND, error.name());
            }

            case DECRYPTION_FAILED -> {
                // Request key refresh via AsyTwoWay
                requestKeyRefresh(peerId);
            }
        }
    }
}
```

#### 20.2.2 Transfer Recovery

File transfers support automatic resume on connection loss:

```java
public class TransferRecovery {
    public void onTransferInterrupted(FileTransfer transfer) {
        // Save transfer state
        TransferState state = transfer.saveState();
        pendingResume.put(transfer.getId(), state);

        // Attempt resume when connection restored
        onPeerReconnected(transfer.getPeerId(), () -> {
            resumeTransfer(transfer.getId());
        });
    }

    public void resumeTransfer(String transferId) {
        TransferState state = pendingResume.get(transferId);
        if (state == null) return;

        // Send FILE_ACCEPT with resume offset
        FileAcceptMessage accept = new FileAcceptMessage(
            state.getTransferId(),
            state.getReceivedChunks()  // Bitmap of received chunks
        );

        sendMessage(state.getPeerId(), accept);
    }
}
```

### 20.3 Exception Handling in Handlers

```java
public class MessageHandler {
    public void handleIncomingData(String peerId, byte[] data) {
        try {
            AppMessage message = MessageCodec.decode(data);
            routeMessage(peerId, message);
        } catch (InvalidMessageException e) {
            // Log and report bad peer behavior
            logger.warn("Invalid message from {}: {}", peerId, e.getMessage());
            qualityReporter.reportBehavior(peerId, BehaviorType.INVALID_MESSAGE);
        } catch (Exception e) {
            // Unexpected error - log but don't crash
            logger.error("Error handling message from {}", peerId, e);
        }
    }
}
```

### 20.4 Graceful Degradation

When features fail, the node degrades gracefully:

| Failure | Degradation |
|---------|-------------|
| Symmetric key expired | Fall back to AsyTwoWay (ECDH) |
| Relay unreachable | Try alternate relays from addressing service |
| File transfer timeout | Pause and resume on reconnect |
| Addressing service down | Use cached relay information |

---

## 21. Next Steps

1. Review this updated plan and confirm design decisions
2. Set up project structure with logging configuration
3. Prioritize features for Phase 1:
   - `FudpNode` wrapper
   - `MessageCodec` serialization
   - `ChatMessage` and `ChatHandler`
   - `PeerBook` for peer storage
   - Basic CLI (optional for initial testing)
4. Begin implementation of core node
5. Write unit tests for message codec

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **FID** | Freecash ID, derived from public key (e.g., `FHLxyzabc...`) |
| **Stream** | A bidirectional or unidirectional data channel within a connection |
| **AppMessage** | Application-layer message (Chat, Request, File, etc.) |
| **Frame** | Protocol-layer unit (STREAM, ACK, SYMKEY_PROPOSAL, etc.) |
| **PeerBook** | Local storage of known peers and their addresses |
| **tRate** | On-chain credit rating for services |
| **tCdd** | Coin-day destroyed, a measure of stake/commitment |

---

*Document created: 2025-11-20*
*Last updated: 2025-12-21*
*Author: Claude Code*

**Revision History:**
- 2025-12-21: Added Performance Monitoring section (Section 19) - NodeStats, PeerStats, MeterListener, loss detection parameters; Updated NodeEventListener.onChatAck to include RTT parameter
- 2025-12-18: Updated SessionManager design - removed persistence, sessions now in-memory only
- 2025-11-22: Added Protocol integration details, NAT traversal, error handling, logging architecture
- 2025-11-21: Added design decisions for peer discovery, message persistence, rate limiting
- 2025-11-20: Initial version with core architecture and message protocols
