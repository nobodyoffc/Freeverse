# FUDP Encrypt Mode Improvement – App Impact Review

## Protocol Changes That Matter
- `EncryptMode` is now explicit per peer (PreferSymkey default, AsyOnly opt-in) and persists via `PeerBook`.
- App data in PreferSymkey should queue until `SYMKEY_ACK`; send paths may throw `QueueFullException` / `NegotiationTimeoutException` if negotiation stalls.
- `ERROR_ASY_ONLY` is emitted when a peer rejects symmetric mode; mode switches should be persisted to avoid retry loops.
- Retransmit now piggybacks deterministic `SYMKEY_PROPOSAL` with backoff; AsyBridge + TTL handles peer restarts instead of latching `forceAsyTwoWay`.

## Findings in Current Apps
- Missing persistence hook: `Protocol.setPeerBook` is never called in `fudp.node.FudpNode` (`src/main/java/fudp/node/FudpNode.java:43-55`), so per-peer `encryptMode`/ASY_ONLY decisions are not stored or reloaded.
- Send path bypasses the new queue: all application sends use `protocol.sendAndClose(...)` (`FudpNode` lines 103-112, 191-200, 216-226, 244-245, 495-538). `sendAndClose` immediately calls `sendFrame` without the PreferSymkey queue/backoff, so chat/FAPI traffic can stay in AsyTwoWay after a peer restart and never see `QueueFullException`/`NegotiationTimeoutException`.
- No CLI/config awareness: `StartFudpNode` and `StartFapiServer` build `NodeConfig` but expose no way to set the default `EncryptMode` or mark a peer as `AsyOnly`. Operators cannot opt into Asy-only peers or toggle defaults.
- Legacy/Asy-only interoperability: neither FUDP node nor FAPI client/server surfaces `ERROR_ASY_ONLY` to users; there is no automatic downgrade to AsyOnly for legacy peers, risking repeated proposal spam.
- Bootstrap timing: FAPI bootstrap (`FapiClient.discoverViaHelloAndPing`) relies on `pingAwaitPong` (sendAndClose). Once send gating is enforced, the extra 1-RTT for symkey plus proposal retries must fit within the current hello/ping timeouts (5s each); otherwise discovery will fail prematurely.

## Recommendations
1) **Wire persistence**: call `protocol.setPeerBook(peerBook)` in `FudpNode` after constructing both, so `encryptMode` changes (ASY_ONLY errors, manual overrides) survive restarts.  
2) **Symkey-aware send**: refactor `Protocol.sendAndClose` to reuse the `enqueueOrSend` path (queue + backoff + proposal retry) and propagate queue/timeout errors. Update FudpNode send sites to handle these errors (e.g., surface “negotiating key / queue full” to the UI or retry).  
3) **Config/CLI flags**: add a default encrypt-mode option to `NodeConfig`/starters and a per-peer override command (e.g., “set peer encrypt-mode asy-only|prefer-symkey”) that calls `protocol.setEncryptMode`. Use it when talking to known legacy Asy-only nodes.  
4) **Handle ASY_ONLY feedback**: when receiving `ERROR_ASY_ONLY`, mark the peer in `PeerBook` and stop symkey proposals; log a clear hint for operators. FAPI bootstrap should treat this as “peer reachable but Asy-only” instead of a hard failure.  
5) **Tune discovery timeouts**: if send gating is enabled, slightly raise ping/hello timeouts or allow a second attempt before giving up, so the extra negotiation round does not break FAPI discovery.  
6) **Validation**: add targeted tests once changes land—peer restart recovery with queued app data, ASY_ONLY downgrade path, bootstrap with PreferSymkey vs AsyOnly, and queue overflow/negotiation timeout surfacing to callers.
