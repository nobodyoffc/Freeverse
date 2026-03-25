package fudp.handler;

import fudp.message.*;
import fudp.node.NodeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Handles file transfer operations including sending and receiving files.
 */
public class FileHandler {
    private static final Logger log = LoggerFactory.getLogger(FileHandler.class);

    public static final int DEFAULT_CHUNK_SIZE = 32 * 1024; // 32KB

    private final NodeEventListener eventListener;
    private final MessageSender messageSender;
    private final ScheduledExecutorService scheduler;

    // Active transfers
    private final Map<String, OutgoingTransfer> outgoingTransfers = new ConcurrentHashMap<>();
    private final Map<String, IncomingTransfer> incomingTransfers = new ConcurrentHashMap<>();

    // Pending file offers (waiting for accept/reject)
    private final Map<String, PendingOffer> pendingOffers = new ConcurrentHashMap<>();
    
    // Relayed file transfers (via relay node)
    private final Map<String, RelayedTransfer> relayedTransfers = new ConcurrentHashMap<>();

    public interface MessageSender {
        void send(String peerId, AppMessage message) throws IOException;

        /**
         * Send pre-encoded message bytes on a single stream.
         * This avoids opening a new stream per message, which is critical
         * for file transfers where hundreds of chunks must be sent efficiently.
         * <p>
         * Default implementation falls back to decoding and sending one by one.
         */
        default void sendRawOnSingleStream(String peerId, byte[] concatenatedEncodedMessages) throws IOException {
            throw new UnsupportedOperationException("Bulk send not supported by this sender");
        }
    }

    public FileHandler(NodeEventListener eventListener, MessageSender messageSender) {
        this.eventListener = eventListener;
        this.messageSender = messageSender;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    /**
     * Generate a unique transfer ID.
     */
    public String generateTransferId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Get the default chunk size.
     */
    public int getDefaultChunkSize() {
        return DEFAULT_CHUNK_SIZE;
    }

    /**
     * Calculate SHA-256 hash of a file.
     */
    public static String calculateFileHash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate file hash", e);
        }
    }

    /**
     * Initiate sending a file to a peer.
     */
    public String sendFile(String peerId, File file) throws IOException {
        return sendFile(peerId, file, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Initiate sending a file to a peer with custom chunk size.
     */
    public String sendFile(String peerId, File file, int chunkSize) throws IOException {
        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IOException("Not a file: " + file.getAbsolutePath());
        }

        String transferId = generateTransferId();
        String fileName = file.getName();
        long fileSize = file.length();
        String fileHash = calculateFileHash(file);

        // Create and store outgoing transfer
        OutgoingTransfer transfer = new OutgoingTransfer(transferId, peerId, file, fileSize, fileHash, chunkSize);
        outgoingTransfers.put(transferId, transfer);

        // Send file offer
        FileOfferMessage offer = new FileOfferMessage(transferId, fileName, fileSize, fileHash, chunkSize);
        messageSender.send(peerId, offer);

        log.info("[FileHandler] Sent file offer: {} ({} bytes) to {}", fileName, fileSize, peerId);
        return transferId;
    }

    /**
     * Accept a pending file offer.
     */
    public void acceptFile(String transferId, String saveDir) throws IOException {
        PendingOffer offer = pendingOffers.remove(transferId);
        if (offer == null) {
            throw new IOException("No pending offer with ID: " + transferId);
        }

        // Create save directory if needed
        Path savePath = Path.of(saveDir);
        if (!Files.exists(savePath)) {
            Files.createDirectories(savePath);
        }

        // Create incoming transfer
        File destFile = savePath.resolve(offer.fileName).toFile();
        IncomingTransfer transfer = new IncomingTransfer(
                transferId, offer.peerId, destFile, offer.fileSize, offer.fileHash, offer.chunkSize);
        incomingTransfers.put(transferId, transfer);

        // Send accept message
        FileAcceptMessage accept = new FileAcceptMessage(transferId);
        messageSender.send(offer.peerId, accept);

        log.info("[FileHandler] Accepted file transfer: {} -> {}", offer.fileName, destFile.getAbsolutePath());
    }

    /**
     * Reject a pending file offer.
     */
    public void rejectFile(String transferId, String reason) throws IOException {
        PendingOffer offer = pendingOffers.remove(transferId);
        if (offer == null) {
            throw new IOException("No pending offer with ID: " + transferId);
        }

        FileRejectMessage reject = new FileRejectMessage(transferId, reason);
        messageSender.send(offer.peerId, reject);

        log.info("[FileHandler] Rejected file transfer: {} - {}", transferId, reason);
    }

    /**
     * Cancel an ongoing transfer.
     */
    public void cancelTransfer(String transferId, String reason) throws IOException {
        OutgoingTransfer outgoing = outgoingTransfers.remove(transferId);
        IncomingTransfer incoming = incomingTransfers.remove(transferId);

        String peerId = null;
        if (outgoing != null) {
            outgoing.cancel();
            peerId = outgoing.peerId;
        }
        if (incoming != null) {
            incoming.cancel();
            peerId = incoming.peerId;
        }

        if (peerId != null) {
            FileCancelMessage cancel = new FileCancelMessage(transferId, reason);
            messageSender.send(peerId, cancel);
        }

        if (eventListener != null) {
            eventListener.onFileError(transferId, "Cancelled: " + reason);
        }
    }

    /**
     * Handle incoming file offer.
     */
    public void handleFileOffer(String peerId, FileOfferMessage offer) {
        String transferId = offer.getTransferId();

        // Store as pending offer
        PendingOffer pending = new PendingOffer(
                peerId, transferId, offer.getFileName(),
                offer.getFileSize(), offer.getFileHash(), offer.getChunkSize());
        pendingOffers.put(transferId, pending);

        log.info("[FileHandler] Received file offer from {}: {} ({} bytes)",
                peerId, offer.getFileName(), offer.getFileSize());

        // Notify listener
        if (eventListener != null) {
            NodeEventListener.FileOffer fileOffer = new NodeEventListener.FileOffer(
                    transferId, offer.getFileName(), offer.getFileSize(), offer.getFileHash());
            eventListener.onFileOfferReceived(peerId, fileOffer);
        }
    }

    /**
     * Handle file accept - start sending chunks.
     */
    public void handleFileAccept(String peerId, FileAcceptMessage accept) {
        String transferId = accept.getTransferId();
        OutgoingTransfer transfer = outgoingTransfers.get(transferId);

        if (transfer == null) {
            log.warn("[FileHandler] No outgoing transfer for accept: {}", transferId);
            return;
        }

        log.info("[FileHandler] File accepted, starting transfer: {}", transferId);

        // Start sending chunks in background
        scheduler.execute(() -> sendFileChunks(transfer));
    }

    /**
     * Handle file reject.
     */
    public void handleFileReject(String peerId, FileRejectMessage reject) {
        String transferId = reject.getTransferId();
        OutgoingTransfer transfer = outgoingTransfers.remove(transferId);

        if (transfer != null) {
            transfer.cancel();
        }

        log.info("[FileHandler] File rejected: {} - {}", transferId, reject.getReason());

        if (eventListener != null) {
            eventListener.onFileError(transferId, "Rejected: " + reject.getReason());
        }
    }

    /**
     * Handle incoming file chunk.
     */
    public void handleFileChunk(String peerId, FileChunkMessage chunk) {
        String transferId = chunk.getTransferId();
        IncomingTransfer transfer = incomingTransfers.get(transferId);

        if (transfer == null) {
            log.warn("[FileHandler] No incoming transfer for chunk: {}", transferId);
            return;
        }

        try {
            transfer.writeChunk(chunk.getChunkIndex(), chunk.getOffset(), chunk.getData());

            // Report progress
            if (eventListener != null) {
                eventListener.onFileProgress(transferId, transfer.getBytesReceived(), transfer.fileSize);
            }
        } catch (IOException e) {
            log.error("[FileHandler] Failed to write chunk: {}", e.getMessage());
            incomingTransfers.remove(transferId);
            transfer.cancel();
            if (eventListener != null) {
                eventListener.onFileError(transferId, "Write error: " + e.getMessage());
            }
        }
    }

    /**
     * Handle file complete message.
     * <p>
     * The FileCompleteMessage may arrive before all chunk messages because each chunk
     * is sent on a separate stream and can be reordered. We must wait for all bytes
     * to arrive before closing and verifying the file.
     */
    public void handleFileComplete(String peerId, FileCompleteMessage complete) {
        String transferId = complete.getTransferId();
        IncomingTransfer transfer = incomingTransfers.get(transferId);

        if (transfer == null) {
            log.warn("[FileHandler] No incoming transfer for complete: {}", transferId);
            return;
        }

        long expectedBytes = complete.getTotalBytes();
        transfer.markSenderDone(expectedBytes);

        // Schedule a polling task that waits for all bytes to arrive, then verifies
        scheduler.execute(() -> waitAndVerify(transferId, transfer, expectedBytes));
    }

    /**
     * Wait for all bytes to arrive, then close, verify, and notify.
     * Polls every 100ms for up to 60 seconds.
     */
    private void waitAndVerify(String transferId, IncomingTransfer transfer, long expectedBytes) {
        long deadline = System.currentTimeMillis() + 60_000; // 60s max wait
        long lastLog = 0;

        while (System.currentTimeMillis() < deadline && !transfer.isCancelled()) {
            long received = transfer.getBytesReceived();
            if (received >= expectedBytes) {
                break;
            }
            // Log progress periodically (every 2 seconds)
            long now = System.currentTimeMillis();
            if (now - lastLog > 2000) {
                log.debug("[FileHandler] Waiting for chunks: {}/{} bytes for transfer {}",
                        received, expectedBytes, transferId);
                lastLog = now;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Remove from active transfers so late duplicates don't keep writing
        incomingTransfers.remove(transferId);

        if (transfer.isCancelled()) {
            return;
        }

        try {
            transfer.close();

            long received = transfer.getBytesReceived();
            if (received < expectedBytes) {
                log.warn("[FileHandler] Incomplete transfer {}: received={}, expected={}",
                        transferId, received, expectedBytes);
                if (eventListener != null) {
                    eventListener.onFileError(transferId,
                            "Incomplete: received " + received + "/" + expectedBytes + " bytes");
                }
                return;
            }

            // Verify hash
            String actualHash = calculateFileHash(transfer.destFile);
            if (!actualHash.equals(transfer.expectedHash)) {
                log.warn("[FileHandler] Hash mismatch for {}: expected={}, actual={}",
                        transferId, transfer.expectedHash, actualHash);
                if (eventListener != null) {
                    eventListener.onFileError(transferId, "Hash verification failed");
                }
                return;
            }

            log.info("[FileHandler] File transfer complete: {} ({} bytes)",
                    transfer.destFile.getAbsolutePath(), received);

            if (eventListener != null) {
                eventListener.onFileComplete(transferId, transfer.destFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("[FileHandler] Failed to complete transfer: {}", e.getMessage());
            if (eventListener != null) {
                eventListener.onFileError(transferId, "Complete error: " + e.getMessage());
            }
        }
    }

    /**
     * Handle file cancel message.
     */
    public void handleFileCancel(String peerId, FileCancelMessage cancel) {
        String transferId = cancel.getTransferId();

        OutgoingTransfer outgoing = outgoingTransfers.remove(transferId);
        if (outgoing != null) {
            outgoing.cancel();
        }

        IncomingTransfer incoming = incomingTransfers.remove(transferId);
        if (incoming != null) {
            incoming.cancel();
        }

        log.info("[FileHandler] Transfer cancelled: {} - {}", transferId, cancel.getReason());

        if (eventListener != null) {
            eventListener.onFileError(transferId, "Cancelled by peer: " + cancel.getReason());
        }
    }

    /**
     * Send file chunks for an outgoing transfer.
     * <p>
     * Encodes all chunk messages + the complete message and sends them on a SINGLE
     * stream via {@link MessageSender#sendRawOnSingleStream}. This avoids the massive
     * overhead of opening a new stream per chunk (encryption handshake, ACK tracking,
     * congestion-window contention). The receiver's MessageFrameAssembler naturally
     * parses the concatenated messages from the stream.
     * <p>
     * Falls back to per-chunk sending if the sender does not support bulk mode.
     */
    private void sendFileChunks(OutgoingTransfer transfer) {
        try {
            sendFileChunksBulk(transfer);
        } catch (UnsupportedOperationException e) {
            // Fallback for senders that don't support bulk mode
            sendFileChunksLegacy(transfer);
        }
    }

    /**
     * Bulk mode: encode all chunks + complete message and send everything on ONE stream.
     * <p>
     * Previous design used one stream per 100KB batch (~170 streams for a 17MB file).
     * This caused race conditions in the receiver's multi-threaded stream assembly:
     * out-of-order frame delivery across 170+ streams meant some streams never had
     * their offset-0 frame delivered before the onPacketReceived callback ran.
     * <p>
     * New design sends ALL data on a single stream.  The stream's in-order reassembly
     * (via TreeMap + recvOffset) naturally handles any frame reordering within the one
     * stream, and the MessageFrameAssembler extracts individual chunk messages from
     * the concatenated byte stream.
     */
    private void sendFileChunksBulk(OutgoingTransfer transfer) {
        try (RandomAccessFile raf = new RandomAccessFile(transfer.file, "r")) {
            // Encode ALL chunks + complete message into one byte array.
            // Memory: ~fileSize + encoding overhead. Acceptable for files up to ~50MB.
            java.io.ByteArrayOutputStream allData = new java.io.ByteArrayOutputStream(
                    (int) Math.min(transfer.fileSize + 64 * 1024, Integer.MAX_VALUE));

            byte[] readBuf = new byte[transfer.chunkSize];
            int chunkIndex = 0;
            long offset = 0;

            while (offset < transfer.fileSize && !transfer.isCancelled()) {
                int bytesToRead = (int) Math.min(transfer.chunkSize, transfer.fileSize - offset);
                raf.seek(offset);
                int bytesRead = raf.read(readBuf, 0, bytesToRead);
                if (bytesRead <= 0) break;

                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(readBuf, 0, chunkData, 0, bytesRead);

                FileChunkMessage chunkMsg = new FileChunkMessage(
                        transfer.transferId, chunkIndex, offset, chunkData);
                byte[] encoded = MessageCodec.encode(chunkMsg);
                allData.write(encoded);

                offset += bytesRead;
                chunkIndex++;
                transfer.bytesSent = offset;

                // Report progress
                if (eventListener != null) {
                    eventListener.onFileProgress(transfer.transferId, offset, transfer.fileSize);
                }
            }

            if (!transfer.isCancelled()) {
                // Append the complete message
                FileCompleteMessage complete = new FileCompleteMessage(
                        transfer.transferId, chunkIndex, offset);
                byte[] encodedComplete = MessageCodec.encode(complete);
                allData.write(encodedComplete);

                // Send everything on ONE stream
                messageSender.sendRawOnSingleStream(transfer.peerId, allData.toByteArray());

                outgoingTransfers.remove(transfer.transferId);

                log.info("[FileHandler] Finished sending file: {} ({} chunks, {} bytes)",
                        transfer.file.getName(), chunkIndex, offset);

                if (eventListener != null) {
                    eventListener.onFileComplete(transfer.transferId, transfer.file.getAbsolutePath());
                }
            }
        } catch (UnsupportedOperationException e) {
            throw e; // Let caller handle fallback
        } catch (Exception e) {
            log.error("[FileHandler] Error sending file (bulk): {}", e.getMessage());
            outgoingTransfers.remove(transfer.transferId);
            if (eventListener != null) {
                eventListener.onFileError(transfer.transferId, "Send error: " + e.getMessage());
            }
        }
    }

    /**
     * Legacy mode: send one chunk per stream (slow but always works).
     */
    private void sendFileChunksLegacy(OutgoingTransfer transfer) {
        try (RandomAccessFile raf = new RandomAccessFile(transfer.file, "r")) {
            byte[] buffer = new byte[transfer.chunkSize];
            int chunkIndex = 0;
            long offset = 0;

            while (offset < transfer.fileSize && !transfer.isCancelled()) {
                int bytesToRead = (int) Math.min(transfer.chunkSize, transfer.fileSize - offset);
                raf.seek(offset);
                int bytesRead = raf.read(buffer, 0, bytesToRead);
                if (bytesRead <= 0) break;

                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                FileChunkMessage chunk = new FileChunkMessage(
                        transfer.transferId, chunkIndex, offset, chunkData);
                messageSender.send(transfer.peerId, chunk);

                offset += bytesRead;
                chunkIndex++;
                transfer.bytesSent = offset;

                if (eventListener != null) {
                    eventListener.onFileProgress(transfer.transferId, offset, transfer.fileSize);
                }

                Thread.sleep(1); // Minimal pacing
            }

            if (!transfer.isCancelled()) {
                FileCompleteMessage complete = new FileCompleteMessage(
                        transfer.transferId, chunkIndex, offset);
                messageSender.send(transfer.peerId, complete);
                outgoingTransfers.remove(transfer.transferId);

                log.info("[FileHandler] Finished sending file (legacy): {} ({} chunks, {} bytes)",
                        transfer.file.getName(), chunkIndex, offset);

                if (eventListener != null) {
                    eventListener.onFileComplete(transfer.transferId, transfer.file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            log.error("[FileHandler] Error sending file (legacy): {}", e.getMessage());
            outgoingTransfers.remove(transfer.transferId);
            if (eventListener != null) {
                eventListener.onFileError(transfer.transferId, "Send error: " + e.getMessage());
            }
        }
    }

    /**
     * Get list of pending file offers.
     */
    public Map<String, PendingOffer> getPendingOffers() {
        return new ConcurrentHashMap<>(pendingOffers);
    }

    /**
     * Shutdown the file handler.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Cancel all transfers
        for (OutgoingTransfer transfer : outgoingTransfers.values()) {
            transfer.cancel();
        }
        for (IncomingTransfer transfer : incomingTransfers.values()) {
            transfer.cancel();
        }
        outgoingTransfers.clear();
        incomingTransfers.clear();
        pendingOffers.clear();
        relayedTransfers.clear();
    }

    // --- Relayed file transfer support ---

    /**
     * Register a relayed file offer (sender side).
     */
    public void registerRelayedFileOffer(String transferId, long sessionId, String relayPeerId, 
            String targetFid, File file) {
        RelayedTransfer transfer = new RelayedTransfer(
                transferId, sessionId, relayPeerId, targetFid, getLocalFid(), 
                file.getName(), file.length(), true, file);
        relayedTransfers.put(transferId, transfer);
        log.debug("[FileHandler] Registered relayed file offer: {} (session={})", transferId, sessionId);
    }

    /**
     * Register a relayed file receive (receiver side).
     */
    public void registerRelayedFileReceive(String transferId, long sessionId, String relayPeerId,
            String senderFid, String saveDir) {
        PendingOffer offer = pendingOffers.get(transferId);
        if (offer == null) {
            log.warn("[FileHandler] No pending offer for relayed file receive: {}", transferId);
            return;
        }
        
        RelayedTransfer transfer = new RelayedTransfer(
                transferId, sessionId, relayPeerId, senderFid, getLocalFid(),
                offer.fileName, offer.fileSize, false, null);
        transfer.saveDir = saveDir;
        transfer.expectedHash = offer.fileHash;
        transfer.chunkSize = offer.chunkSize;
        relayedTransfers.put(transferId, transfer);
        pendingOffers.remove(transferId);
        log.debug("[FileHandler] Registered relayed file receive: {} (session={})", transferId, sessionId);
    }

    /**
     * Get relayed transfer info.
     */
    public RelayedTransfer getRelayedTransfer(String transferId) {
        return relayedTransfers.get(transferId);
    }

    /**
     * Get local FID for relay transfers.
     */
    private String getLocalFid() {
        // This will be set from context; for now return empty
        return "";
    }

    // --- Transfer state classes ---

    public static class PendingOffer {
        public final String peerId;
        public final String transferId;
        public final String fileName;
        public final long fileSize;
        public final String fileHash;
        public final int chunkSize;

        public PendingOffer(String peerId, String transferId, String fileName,
                           long fileSize, String fileHash, int chunkSize) {
            this.peerId = peerId;
            this.transferId = transferId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileHash = fileHash;
            this.chunkSize = chunkSize;
        }
    }

    private static class OutgoingTransfer {
        final String transferId;
        final String peerId;
        final File file;
        final long fileSize;
        final String fileHash;
        final int chunkSize;
        volatile long bytesSent = 0;
        volatile boolean cancelled = false;

        OutgoingTransfer(String transferId, String peerId, File file,
                        long fileSize, String fileHash, int chunkSize) {
            this.transferId = transferId;
            this.peerId = peerId;
            this.file = file;
            this.fileSize = fileSize;
            this.fileHash = fileHash;
            this.chunkSize = chunkSize;
        }

        void cancel() {
            cancelled = true;
        }

        boolean isCancelled() {
            return cancelled;
        }
    }

    private static class IncomingTransfer {
        final String transferId;
        final String peerId;
        final File destFile;
        final long fileSize;
        final String expectedHash;
        final int chunkSize;
        private RandomAccessFile raf;
        private volatile long bytesReceived = 0;
        private volatile boolean cancelled = false;
        private volatile boolean senderDone = false;
        private volatile long expectedTotalBytes = 0;

        // Track which chunks have been received to deduplicate retransmissions.
        // Without this, retransmitted chunks inflate bytesReceived, causing
        // waitAndVerify to proceed before all unique offsets are written.
        private final BitSet receivedChunks = new BitSet();

        IncomingTransfer(String transferId, String peerId, File destFile,
                        long fileSize, String expectedHash, int chunkSize) throws IOException {
            this.transferId = transferId;
            this.peerId = peerId;
            this.destFile = destFile;
            this.fileSize = fileSize;
            this.expectedHash = expectedHash;
            this.chunkSize = chunkSize;
            this.raf = new RandomAccessFile(destFile, "rw");
        }

        synchronized void writeChunk(int chunkIndex, long offset, byte[] data) throws IOException {
            if (cancelled) return;
            // Deduplicate: skip chunks that have already been received
            if (receivedChunks.get(chunkIndex)) {
                return;
            }
            receivedChunks.set(chunkIndex);
            raf.seek(offset);
            raf.write(data);
            bytesReceived += data.length;
        }

        long getBytesReceived() {
            return bytesReceived;
        }

        void markSenderDone(long totalBytes) {
            this.expectedTotalBytes = totalBytes;
            this.senderDone = true;
        }

        boolean isCancelled() {
            return cancelled;
        }

        synchronized void close() throws IOException {
            if (raf != null) {
                raf.close();
                raf = null;
            }
        }

        synchronized void cancel() {
            cancelled = true;
            try {
                if (raf != null) {
                    raf.close();
                    raf = null;
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * Relayed file transfer info.
     */
    public static class RelayedTransfer {
        public final String transferId;
        public final long sessionId;
        public final String relayPeerId;
        public final String remoteFid;  // Target (if sending) or Sender (if receiving)
        public final String localFid;
        public final String fileName;
        public final long fileSize;
        public final boolean isSending;
        public final File file;  // Only for sending
        
        public String saveDir;      // Only for receiving
        public String expectedHash; // Only for receiving
        public int chunkSize;
        public volatile long bytesTransferred = 0;
        public volatile boolean completed = false;
        public volatile boolean cancelled = false;

        public RelayedTransfer(String transferId, long sessionId, String relayPeerId,
                String remoteFid, String localFid, String fileName, long fileSize,
                boolean isSending, File file) {
            this.transferId = transferId;
            this.sessionId = sessionId;
            this.relayPeerId = relayPeerId;
            this.remoteFid = remoteFid;
            this.localFid = localFid;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.isSending = isSending;
            this.file = file;
        }
    }
}

