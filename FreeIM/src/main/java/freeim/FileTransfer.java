package freeim;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import fudp.node.NodeStats;
import ui.ProgressBar;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * File transfer using FUDP REQUEST/RESPONSE.
 *
 * Protocol:
 *   1. Sender → REQUEST(service="file-offer", data={"name","size","hash"})
 *   2. Receiver → RESPONSE(200=accept, 403=reject)
 *   3. Sender → REQUEST+stream(service="file-data", data={"name","size","offerRequestId"}, stream=file bytes)
 *   4. Receiver → RESPONSE(200=saved)
 *
 * The "offerRequestId" in step 3 links the data transfer to the specific offer,
 * enabling concurrent file transfers from the same peer on different connections.
 */
public class FileTransfer {
    private static final Gson GSON = new Gson();
    private static final long OFFER_TIMEOUT_MS = 60_000;   // 1 minute to accept/reject
    private static final long TRANSFER_TIMEOUT_MS = 300_000; // 5 minutes for transfer

    private final FudpNode node;
    private final Path downloadDir;

    // Pending incoming file offers: requestId → offer info
    public record PendingOffer(String peerId, long connectionId, long requestId,
                               String fileName, long fileSize, String hash, long receivedAt) {}

    private final Map<Long, PendingOffer> pendingOffers = new ConcurrentHashMap<>();

    // Track receive timing and progress per offer (keyed by offerRequestId, not peerId)
    private final Map<Long, Long> receiveStartNanos = new ConcurrentHashMap<>();
    private final Map<Long, Long> expectedSizes = new ConcurrentHashMap<>();
    // peerId → offerRequestId mapping for progress callback (which only has peerId)
    private final Map<String, Long> activeReceiveByPeer = new ConcurrentHashMap<>();
    // Track latest received bytes per offer (updated silently from background thread)
    private final Map<Long, Long> lastRecvBytes = new ConcurrentHashMap<>();
    // Receiver-side progress bars per offer
    private final Map<Long, ProgressBar> receiveProgressBars = new ConcurrentHashMap<>();

    public FileTransfer(FudpNode node, Path downloadDir) {
        this.node = node;
        this.downloadDir = downloadDir;
        try {
            Files.createDirectories(downloadDir);
        } catch (IOException e) {
            System.err.println("Failed to create download directory: " + e.getMessage());
        }
    }

    /**
     * Send a file to a peer. Runs in background thread so menu stays usable.
     */
    public void sendFile(String peerId, File file) {
        if (!file.exists() || !file.isFile()) {
            System.out.println("  File not found: " + file.getAbsolutePath());
            return;
        }

        long size = file.length();
        System.out.printf("  Offering %s (%s) to %s...%n", file.getName(), formatSize(size), shortFid(peerId));

        // Run in background so menu is not blocked
        Thread t = new Thread(() -> doSendFile(peerId, file, size), "file-send");
        t.setDaemon(true);
        t.start();
    }

    private void doSendFile(String peerId, File file, long size) {
        try {
            String hash = computeHash(file);

            JsonObject offerJson = new JsonObject();
            offerJson.addProperty("name", file.getName());
            offerJson.addProperty("size", size);
            offerJson.addProperty("hash", hash);
            byte[] offerData = GSON.toJson(offerJson).getBytes(StandardCharsets.UTF_8);

            CompletableFuture<ResponseMessage> offerFuture = node.request(peerId, "file-offer", offerData);
            ResponseMessage offerResponse = offerFuture.get(OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (offerResponse == null) {
                System.out.println("\n  [SEND] No response to offer (peer may be unreachable).");
                return;
            }
            if (offerResponse.getStatusCode() != 200) {
                String reason = offerResponse.getData() != null ? new String(offerResponse.getData()) : "unknown";
                System.out.println("\n  [SEND] Rejected: " + reason);
                return;
            }

            // Extract offerRequestId from the acceptance response for linking
            long offerRequestId = offerResponse.getMessageId();

            System.out.println("\n  [SEND] Accepted. Transferring " + file.getName() + "...");

            // Include offerRequestId in metadata so receiver can match data to the correct offer
            JsonObject metaJson = new JsonObject();
            metaJson.addProperty("name", file.getName());
            metaJson.addProperty("size", size);
            metaJson.addProperty("offerRequestId", offerRequestId);
            byte[] metaData = GSON.toJson(metaJson).getBytes(StandardCharsets.UTF_8);

            long startNanos = System.nanoTime();
            try (FileInputStream fis = new FileInputStream(file)) {
                ProgressInputStream pis = new ProgressInputStream(fis, size, "SEND " + file.getName());
                CompletableFuture<ResponseMessage> transferFuture =
                    node.requestWithStream(peerId, "file-data", metaData, pis, size);
                ResponseMessage transferResponse = transferFuture.get(TRANSFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                long elapsedNanos = System.nanoTime() - startNanos;
                pis.finish();

                if (transferResponse != null && transferResponse.getStatusCode() == 200) {
                    System.out.printf("  [SEND] Done: %s (%s)%n", file.getName(), formatSize(size));
                    printTransferStats(peerId, size, elapsedNanos, true);
                } else {
                    int status = transferResponse != null ? transferResponse.getStatusCode() : -1;
                    System.out.println("  [SEND] Failed: status " + status);
                }
            }

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            System.out.println("\n  [SEND] Failed: " + msg);
        }
    }

    /**
     * Handle incoming request. Called from NodeEventListener.onRequestReceived().
     * Returns true if this was a file-related request.
     */
    public boolean onRequestReceived(String peerId, long connectionId, long requestId, String serviceName, byte[] data) {
        if ("file-offer".equals(serviceName)) {
            handleFileOffer(peerId, connectionId, requestId, data);
            return true;
        }
        if ("file-data".equals(serviceName)) {
            handleFileData(peerId, connectionId, requestId, data);
            return true;
        }
        return false;
    }

    private void handleFileOffer(String peerId, long connectionId, long requestId, byte[] data) {
        try {
            JsonObject json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            String name = json.get("name").getAsString();
            long size = json.get("size").getAsLong();
            String hash = json.has("hash") ? json.get("hash").getAsString() : "";

            PendingOffer offer = new PendingOffer(peerId, connectionId, requestId, name, size, hash, System.currentTimeMillis());
            pendingOffers.put(requestId, offer);

            System.out.printf("%n  [FILE OFFER] From %s: %s (%s)%n", shortFid(peerId), name, formatSize(size));
            System.out.println("  Use 'Pending File Offers' menu to accept/reject (requestId=" + requestId + ")");
        } catch (Exception e) {
            System.out.println("  Invalid file offer: " + e.getMessage());
            respondSafe(peerId, connectionId, requestId, 400, "Invalid offer");
        }
    }

    private void handleFileData(String peerId, long connectionId, long requestId, byte[] data) {
        try {
            // Parse metadata to find the linked offerRequestId
            // The metadata is the non-stream portion of the request data.
            // For requestWithStream, the data parameter contains the metadata bytes.
            long offerRequestId = -1;
            String metaFileName = null;
            try {
                String metaStr = new String(data, StandardCharsets.UTF_8);
                // The data could be just file bytes if metadata parsing fails.
                // Try to detect JSON metadata prefix — it's embedded by requestWithStream
                // as the header portion before the binary stream data.
                // Actually, the 'data' parameter in onRequestReceived for requestWithStream
                // is the FULL reassembled message (header metadata + stream bytes).
                // We need to check the protocol: requestWithStream sends metadata as the
                // request data field, and the file bytes as the stream.
                // But on the receiver side, onRequestReceived gets the complete assembled data.
                // Let's parse what we can from it.
            } catch (Exception ignored) {}

            // Match the offer: first try by offerRequestId from metadata, then by peerId
            String fileName = null;
            PendingOffer matchedOffer = null;

            // Try to extract offerRequestId from the beginning of data (JSON metadata)
            // The requestWithStream format: envelope + headerData + streamData
            // headerData is the JSON metadata we sent
            // But by the time we get 'data' in onRequestReceived, it's already decoded
            // as the full request payload (just the request data field, not the stream).
            // Actually, looking at FudpNode.handleIncomingData -> MessageHandler -> onRequestReceived:
            // the 'data' is RequestMessage.getData() which is the metadata bytes only.
            // The stream data is assembled separately... wait, no.
            // Let me re-check: requestWithStream builds envelope + metadata + stream bytes,
            // all sent on one stream. The receiver reassembles the full byte sequence,
            // decodes it as a RequestMessage. The RequestMessage.getData() is everything
            // after the SID field in the payload.
            // So 'data' here = metadata + file bytes concatenated.
            // The metadata is a small JSON, followed by raw file bytes.

            // The metadata was encoded as: statusCode(2) + headerData + streamData
            // for respondWithStream. But for requestWithStream, the format is:
            // type(1) + messageId(8) + flags(1) + payloadLen(varint) + sid + data
            // where data = metaData + fileBytes (concatenated by SequenceInputStream).
            // So 'data' parameter = metaData bytes + file bytes.
            // The metaData is JSON, so we can try to find the JSON boundary.

            // Simpler approach: look up offers by peerId, but prefer the one whose
            // offerRequestId matches if we have multiple pending offers.
            // Since the offerRequestId is included in metaData JSON (at the start of data),
            // try to extract it.
            try {
                // Find the end of JSON metadata (first '}' followed by non-JSON bytes)
                int jsonEnd = findJsonEnd(data);
                if (jsonEnd > 0) {
                    String metaJson = new String(data, 0, jsonEnd, StandardCharsets.UTF_8);
                    JsonObject meta = JsonParser.parseString(metaJson).getAsJsonObject();
                    if (meta.has("offerRequestId")) {
                        offerRequestId = meta.get("offerRequestId").getAsLong();
                    }
                    if (meta.has("name")) {
                        metaFileName = meta.get("name").getAsString();
                    }
                }
            } catch (Exception ignored) {
                // Metadata parsing failed; fall back to peerId match
            }

            // Match by offerRequestId first (precise), then by peerId (legacy fallback)
            if (offerRequestId > 0) {
                matchedOffer = pendingOffers.remove(offerRequestId);
            }
            if (matchedOffer == null) {
                // Fallback: match by peerId (for backward compatibility with old senders)
                for (PendingOffer offer : pendingOffers.values()) {
                    if (offer.peerId.equals(peerId)) {
                        matchedOffer = offer;
                        pendingOffers.remove(offer.requestId);
                        break;
                    }
                }
            }

            if (matchedOffer != null) {
                fileName = matchedOffer.fileName;
            } else if (metaFileName != null) {
                fileName = metaFileName;
            } else {
                fileName = "received_" + System.currentTimeMillis();
            }

            // Clear progress tracking for this offer
            long offerKey = (matchedOffer != null) ? matchedOffer.requestId : -1;
            Long startNanos = receiveStartNanos.remove(offerKey);
            expectedSizes.remove(offerKey);
            activeReceiveByPeer.remove(peerId);
            long elapsedNanos = (startNanos != null) ? System.nanoTime() - startNanos : 0;
            // Finish receiver progress bar
            ProgressBar pb = receiveProgressBars.remove(offerKey);
            if (pb != null) {
                pb.finish();
            }

            Path savePath = downloadDir.resolve(fileName);
            // Avoid overwriting
            int count = 1;
            while (Files.exists(savePath)) {
                String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
                savePath = downloadDir.resolve(base + "_" + count + ext);
                count++;
            }

            Files.write(savePath, data);
            System.out.printf("  [FILE SAVED] %s (%s) -> %s%n", fileName, formatSize(data.length), savePath);
            printTransferStats(peerId, data.length, elapsedNanos, false);

            // Use peerId-based respond for fallback if connection closed
            node.respond(peerId, requestId, 200, "OK".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.out.println("  Failed to receive file: " + e.getMessage());
            respondSafe(peerId, connectionId, requestId, 500, "Failed to save: " + e.getMessage());
        }
    }

    /**
     * Find the end position of a JSON object at the start of a byte array.
     * Returns the position after the closing '}', or -1 if not found.
     */
    private static int findJsonEnd(byte[] data) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < Math.min(data.length, 4096); i++) {
            byte b = data[i];
            if (escaped) {
                escaped = false;
                continue;
            }
            if (b == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (b == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (b == '{') depth++;
            else if (b == '}') {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return -1;
    }

    /**
     * Accept a pending file offer.
     */
    public void acceptOffer(long requestId) {
        PendingOffer offer = pendingOffers.get(requestId);
        if (offer == null) {
            System.out.println("  No pending offer with requestId=" + requestId);
            return;
        }
        try {
            // Register expected size for progress tracking (keyed by offerRequestId)
            expectedSizes.put(offer.requestId, offer.fileSize);
            receiveStartNanos.put(offer.requestId, System.nanoTime());
            activeReceiveByPeer.put(offer.peerId, offer.requestId);

            // Create receiver-side progress bar
            receiveProgressBars.put(offer.requestId,
                    new ProgressBar("  RECV " + offer.fileName, offer.fileSize));

            // Use peerId-based respond for fallback if the original connection closed
            node.respond(offer.peerId, offer.requestId, 200, "OK".getBytes(StandardCharsets.UTF_8));
            System.out.println("  Accepted. Receiving " + offer.fileName + " (" + formatSize(offer.fileSize) + ")...");
            // Don't remove from pendingOffers yet — handleFileData uses it for the filename
        } catch (Exception e) {
            System.out.println("  Failed to accept: " + e.getMessage());
        }
    }

    /**
     * Reject a pending file offer.
     */
    public void rejectOffer(long requestId) {
        PendingOffer offer = pendingOffers.remove(requestId);
        if (offer == null) {
            System.out.println("  No pending offer with requestId=" + requestId);
            return;
        }
        respondSafe(offer.peerId, offer.connectionId, offer.requestId, 403, "Rejected by user");
        System.out.println("  Rejected.");
    }

    /**
     * List pending file offers.
     */
    public void listPendingOffers() {
        // Clean expired offers
        long now = System.currentTimeMillis();
        pendingOffers.entrySet().removeIf(e -> now - e.getValue().receivedAt > OFFER_TIMEOUT_MS * 2);

        if (pendingOffers.isEmpty()) {
            System.out.println("  No pending file offers.");
            return;
        }
        System.out.println();
        for (PendingOffer offer : pendingOffers.values()) {
            long age = (now - offer.receivedAt) / 1000;
            System.out.printf("  [%d] %s from %s (%s) — %ds ago%n",
                offer.requestId, offer.fileName, shortFid(offer.peerId), formatSize(offer.fileSize), age);
        }
    }

    /**
     * Called from NodeEventListener.onStreamAssemblyProgress().
     * Shows receiver-side progress bar when we're expecting a file.
     */
    public void onAssemblyProgress(String peerId, long streamId, long bytesAssembled) {
        Long offerKey = activeReceiveByPeer.get(peerId);
        if (offerKey != null) {
            lastRecvBytes.put(offerKey, bytesAssembled);
            ProgressBar pb = receiveProgressBars.get(offerKey);
            if (pb != null) {
                pb.update(bytesAssembled);
            }
        }
    }

    private void printTransferStats(String peerId, long fileSize, long elapsedNanos, boolean isSender) {
        String direction = isSender ? "SEND" : "RECV";
        double seconds = elapsedNanos / 1_000_000_000.0;
        double mbPerSec = seconds > 0 ? (fileSize / (1024.0 * 1024.0)) / seconds : 0;
        long elapsedMs = elapsedNanos / 1_000_000;

        System.out.printf("  [%s STATS] %s in %dms (%.2f MB/s)%n", direction, formatSize(fileSize), elapsedMs, mbPerSec);

        try {
            NodeStats.PeerStats ps = node.getPeerStats(peerId);
            if (ps != null) {
                System.out.printf("  [%s STATS] Packets: sent=%d, recv=%d | Retransmits: %d (%s) | Loss: %s | RTT: %dms%n",
                        direction,
                        ps.getPacketsSent(), ps.getPacketsReceived(),
                        ps.getRetransmitCount(), ps.getRetransmitRatePercent(),
                        ps.getLossRatePercent(), ps.getSmoothedRttMs());
            }
        } catch (Exception e) {
            // Stats not available, skip
        }
    }

    /**
     * Respond with fallback: try peerId-based routing (with fallback to any connection),
     * then fall back to explicit connectionId if needed.
     */
    private void respondSafe(String peerId, long connectionId, long requestId, int status, String message) {
        try {
            node.respond(peerId, requestId, status, message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Fallback to explicit connectionId
            try {
                node.respond(connectionId, requestId, status, message.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e2) {
                System.err.println("  Failed to respond: " + e2.getMessage());
            }
        }
    }

    private static String computeHash(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String shortFid(String fid) {
        if (fid == null) return "?";
        return fid.length() > 10 ? fid.substring(0, 6) + ".." + fid.substring(fid.length() - 4) : fid;
    }

    /**
     * InputStream wrapper that shows a progress bar as data is read.
     */
    static class ProgressInputStream extends FilterInputStream {
        private final ProgressBar progressBar;
        private long bytesRead = 0;

        ProgressInputStream(InputStream in, long totalBytes, String label) {
            super(in);
            this.progressBar = new ProgressBar("  " + label, totalBytes);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                bytesRead++;
                progressBar.update(bytesRead);
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) {
                bytesRead += n;
                progressBar.update(bytesRead);
            }
            return n;
        }

        long getBytesRead() {
            return bytesRead;
        }

        void finish() {
            progressBar.finish();
        }
    }
}
