# FUDP Code Logic Review Notes

This note records implementation behaviors in `FC-JDK/src/main/java/fudp/` that should be reviewed before they are treated as stable protocol design. The protocol documents were updated to describe the current Java behavior, but the items below may still deserve code changes.

## 1. Wire Connection ID Is Not the Primary Inbound Key

`Protocol.handleIncomingPacket` decrypts the packet, identifies the sender public key, then calls `ConnectionManager.getOrCreate(senderId, from)`. `ConnectionManager` maps active connections mainly by peer ID and source address and generates its own local connection ID. The packet header's connection ID is not the primary inbound lookup key.

This works for current peer/address routing, but it weakens the purpose of the wire Connection ID and can make connection migration or multiple simultaneous connections from the same socket difficult to reason about.

Recommended review: decide whether FUDP should use the header Connection ID as the canonical connection key, or formally keep the Java behavior as an endpoint-local implementation detail.

## 2. Congestion Control Tracks State But Does Not Gate Normal Sends

`CongestionControl` maintains `cwnd`, `bytesInFlight`, CUBIC states, and `canSend(...)`, but `Protocol.sendPacket(...)` records bytes and sends immediately. Large transfers are controlled mainly by MTU chunking and pacing.

This can be reasonable for LAN-oriented transfers, but it means the implementation is not truly congestion-window limited even though it reports CUBIC state. On shared WAN links this may be unfair or burstier than expected.

Recommended review: either enforce `canSend(...)` for new data or rename/document the current controller as telemetry plus loss reaction rather than strict congestion control.

## 3. Flow Control Is Only Partially Active

`StreamManager` exposes connection-level flow-control counters and `MAX_DATA` handling, and `Stream` exposes stream-level limits. However, automatic `MAX_DATA` / `MAX_STREAM_DATA` frame generation is not wired into the transport loop, and some streaming send paths prioritize chunking and pacing over strict send-limit checks.

The receive side does protect against excessive out-of-order stream buffering, which is useful, but the advertised flow-control contract is incomplete.

Recommended review: either implement full MAX_* generation and send gating, or simplify the protocol terminology to "receive-buffer protection" for the current implementation.

## 4. Stream ID Roles Do Not Distinguish Initiator and Responder

The stream ID format reserves low bits for initiator/responder and bidirectional/unidirectional meaning, but `StreamManager` currently opens local bidirectional streams as `0, 4, 8, ...` on every endpoint. Two peers can therefore choose the same local stream IDs independently.

The current high-level node usually sends one message per stream and scopes reassembly per connection, so this can still work in practice. It is not fully aligned with the role-encoded stream ID model.

Recommended review: make stream ID allocation depend on handshake role, or remove initiator/responder semantics from the stream ID specification.

## 5. Stream Limit Violations Are Dropped Instead of Closed

When `StreamManager.getOrCreateStream(...)` exceeds the remote stream limit, it returns `null`; `Protocol.handleFrame(...)` logs and drops the frame. The protocol-level expectation is usually to close the connection with `STREAM_LIMIT_ERROR`.

Silent dropping avoids extra traffic during abuse, but it can also cause hard-to-debug stalls for honest peers.

Recommended review: close with `CONNECTION_CLOSE(STREAM_LIMIT_ERROR)` for valid peers, possibly retaining silent drop only for pre-authentication or DDoS paths.

## 6. Session Epoch Zero Is Theoretically Possible

`CryptoManager` uses `SecureRandom.nextLong()` for the session epoch. The protocol reserves `0` as "unknown or omitted". The chance of generating zero is negligible, but the code does not explicitly reject it.

Recommended review: regenerate if the random epoch is `0` to preserve the invariant.

## 7. Public Key Control Payload Is Not Strictly Validated

`Protocol.handlePlaintextControl(...)` accepts a `PUBLIC_KEY` payload of length greater than or equal to 2 and copies all remaining bytes as the public key. The protocol expects a 33-byte compressed secp256k1 public key.

Recommended review: require exactly 34 control payload bytes for `PUBLIC_KEY` and validate the compressed-key prefix before completing pending public-key requests.
