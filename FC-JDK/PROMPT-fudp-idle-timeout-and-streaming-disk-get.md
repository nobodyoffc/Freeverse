# Task: Port idle-based request timeout + stream disk.get responses from disk

Two improvements to this FC-JDK server project. Both are already designed and partially
implemented on the client side in the Freer Android project — mirror them here.

## Background

The Freer app's chat file downloads used to fail with timeout errors on large files or
slow networks. Root cause: `FudpNode.request()` schedules a hard timeout of
`config.getRequestTimeoutMs()` (30s default) plus an allowance based only on the
*request* payload size, so it kills the pending future even while response bytes are
still actively arriving. This was fixed in the Freer project (FC-AJDK module) by making
the timeout **idle-based**: the deadline extends whenever inbound stream data arrives
from the peer; a request only times out after ~30s of *silence*.

FC-JDK has the identical code (the two `FudpNode.java` files are forks) and the same bug
bites server-to-server sync: `fapi/components/disk/DiskSyncManager.java` calls
`client.diskGet(id, tempFile)` — any file needing more than ~30s to transfer between
DISK servers fails today.

**Reference implementation (completed, compiles, mirror it):**
`/Users/liuchangyong/AndroidStudioProjects/Freer/FC-AJDK/src/main/java/com/fc/fc_ajdk/fudp/node/FudpNode.java`
and
`/Users/liuchangyong/AndroidStudioProjects/Freer/FC-AJDK/src/main/java/com/fc/fc_ajdk/fapi/client/FapiClient.java`

## Part 1 — Idle-based request timeout (port from FC-AJDK)

Target files:
- `src/main/java/fudp/node/FudpNode.java`
- `src/main/java/fapi/client/FapiClient.java`

Port these pieces from the reference FudpNode (search for these names there):
1. `pendingRequestWatch` map + `PendingRequest` inner class (peerId, future,
   optional `LongConsumer receiveProgress`, volatile `lastActivityMs`).
2. `watchPendingRequest(...)` + `scheduleIdleCheck(...)`: replaces the one-shot
   `scheduler.schedule(...)` timeout in both `request(...)` and `requestWithStream(...)`.
   The scheduled check re-arms itself while `lastActivityMs` keeps advancing and only
   completes the future exceptionally (TimeoutException) after `idleTimeoutMs` of no
   activity. Keep the existing size-based formula as the *initial* idle budget.
   Handle `RejectedExecutionException` (scheduler stopped) by failing the request.
3. `touchPendingRequests(peerId, bytesAssembled)`: called from `onPacketReceived`
   right after feeding stream chunks into the `MessageFrameAssembler` (capture
   `assembler.getBufferSize()` inside the synchronized block, call touch outside).
   It refreshes `lastActivityMs` of all pending requests to that peer and drives
   their `receiveProgress` callbacks.
4. New `request(...)` overload taking a `LongConsumer receiveProgress` (the old
   signature delegates with null).

Then in FC-JDK's `FapiClient`:
5. `requestWithBinaryData(...)`: add the receive-progress overload; replace the fixed
   `future.get(timeoutSeconds, SECONDS)` with the `awaitWithIdleTimeout(...)` loop from
   the reference (1s `future.get` slices; give up only when `now - lastActivityMs`
   exceeds the idle timeout + 2s grace). Unwrap `ExecutionException` whose cause is
   `TimeoutException` into the 408 path so the transport's idle timer reports as a
   timeout, not a 500.
6. `diskGet(did, outputFile, progressCallback)`: pass the callback through as receive
   progress (see reference; the old write-phase chunked progress reporting was removed).

Wire protocol is unchanged — no compatibility concerns with existing clients.

## Part 2 — Stream disk.get responses from disk (server memory fix)

Currently `fapi/components/DiskComponent.java` serves `disk.get` by loading the whole
file into memory: `handleJsonGet` (~line 396) and the deprecated `handleBinaryGet`
(~line 510) both call `diskHandler.retrieve(did)` which returns the full `byte[]`,
then encode it into a single response buffer. A 500MB file allocates 500MB+ on the
server per request.

The transport already supports streaming responses: `FudpNode.respondWithStream(peerId,
requestId, statusCode, headerData, dataStream, dataStreamLength)` builds the response
envelope and streams the payload from an `InputStream` without full memory load
(`fudp/node/FudpNode.java` ~line 452). `FapiDiskHandler` already has a streaming
*store* path — add the symmetric retrieve:

1. Add `FapiDiskHandler.retrieveStream(String did)` (or `getFilePath(did)`) returning an
   `InputStream` + length (a `File` is fine since disk items are stored as files),
   keeping the existing existence/expiry checks.
2. Rework the `disk.get` path so that instead of returning one fully-materialized
   `byte[]` through the generic handler-return path, it responds via
   `respondWithStream` with the UnifiedCodec response header (metadata JSON) as
   `headerData` and the file stream as the payload. Follow how the request dispatch in
   `fapi/service/FapiServer.java` currently turns a handler's `byte[]` return into a
   `respond(...)` call, and add a streaming escape hatch for disk.get (e.g. the handler
   returns a stream descriptor instead of bytes, or disk.get is special-cased before
   the generic path).
3. Keep the in-memory path for small files (< 1MB) if that simplifies the dispatch.
4. Don't forget the charge/metering and `extendExpire` logic currently in
   `handleJsonGet` — it must still run on the streaming path.

Note: the *client* (FC-AJDK `MessageFrameAssembler`) still reassembles the response in
memory; fixing that is a separate future task in the Freer project, out of scope here.
Server-side streaming is still worthwhile: it removes the per-request server allocation
and lets many large downloads run concurrently.

## Verification

- `mvn -q compile` (or this project's build command) must pass.
- Manual end-to-end: `fapi/StartFapiServer.java` + `fapi/StartFapiClient.java` exist for
  local testing. Put a large file (≥ 100MB) via `diskPut`, then `diskGet` it back and
  verify the hash matches, watching server heap (e.g. `jcmd <pid> GC.heap_info`).
- Idle timeout: temporarily set `NodeConfig.requestTimeoutMs` to something small (5s)
  and confirm a multi-second transfer still completes (deadline extends while data
  flows), while a request to a dead peer still fails after ~5s.
