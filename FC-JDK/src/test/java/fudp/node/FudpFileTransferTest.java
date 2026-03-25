package fudp.node;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FUDP file transfer.
 * Verifies that files can be transferred between two FudpNode instances,
 * that the transferred content is correct (SHA-256 hash), and that
 * the transfer speed is reasonable on localhost.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FudpFileTransferTest {

    private static FudpNode nodeA; // sender
    private static FudpNode nodeB; // receiver
    private static Path tempDirA;
    private static Path tempDirB;
    private static Path downloadDir;
    private static Path testFilesDir;

    private static final int PORT_A = 19101;
    private static final int PORT_B = 19102;

    // Shared state for receiver auto-accept
    private static final ConcurrentHashMap<String, CountDownLatch> completionLatches = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> completedFiles = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> transferErrors = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> lastProgressBytes = new ConcurrentHashMap<>();

    @BeforeAll
    public static void setUp() throws Exception {
        // Generate test keys
        SecureRandom random = new SecureRandom();
        byte[] privateKeyA = new byte[32];
        byte[] privateKeyB = new byte[32];
        random.nextBytes(privateKeyA);
        random.nextBytes(privateKeyB);

        // Create temp directories
        tempDirA = Files.createTempDirectory("fudp_ft_a");
        tempDirB = Files.createTempDirectory("fudp_ft_b");
        downloadDir = Files.createTempDirectory("fudp_ft_dl");
        testFilesDir = Files.createTempDirectory("fudp_ft_src");

        // Create configs with large packet size for localhost (reduces per-packet crypto overhead)
        NodeConfig configA = new NodeConfig();
        configA.setPort(PORT_A);
        configA.setDataDir(tempDirA.toString());
        configA.setMaxPacketSize(60000); // Large datagrams for localhost

        NodeConfig configB = new NodeConfig();
        configB.setPort(PORT_B);
        configB.setDataDir(tempDirB.toString());
        configB.setMaxPacketSize(60000);

        // Create nodes
        nodeA = new FudpNode(privateKeyA, configA);
        nodeB = new FudpNode(privateKeyB, configB);

        // Set up the receiver (nodeB) with auto-accept listener
        installReceiverListener();

        // Start nodes
        nodeA.start();
        nodeB.start();

        // Wait for UDP sockets to bind
        Thread.sleep(200);

        // Register each other as peers
        nodeA.addPeer(nodeB.getLocalFid(), nodeB.getLocalPublicKey(), "127.0.0.1", PORT_B);
        nodeB.addPeer(nodeA.getLocalFid(), nodeA.getLocalPublicKey(), "127.0.0.1", PORT_A);

        System.out.println("=== FUDP File Transfer Test Setup ===");
        System.out.println("Node A FID: " + nodeA.getLocalFid());
        System.out.println("Node B FID: " + nodeB.getLocalFid());
        System.out.println("Download dir: " + downloadDir);
        System.out.println("=====================================");
    }

    /**
     * Install a NodeEventListener on nodeB that auto-accepts file offers
     * and tracks completion/errors via shared latches.
     */
    private static void installReceiverListener() {
        nodeB.setEventListener(new NodeEventListener() {
            @Override
            public void onFileOfferReceived(String peerId, FileOffer offer) {
                String transferId = offer.getTransferId();
                System.out.println("[Receiver] File offer received: " + offer.getFileName()
                        + " (" + formatBytes(offer.getFileSize()) + "), transferId=" + transferId);

                // Register a latch for this transfer before accepting
                completionLatches.putIfAbsent(transferId, new CountDownLatch(1));

                try {
                    nodeB.acceptFile(transferId, downloadDir.toString());
                    System.out.println("[Receiver] Auto-accepted transfer: " + transferId);
                } catch (IOException e) {
                    System.err.println("[Receiver] Failed to accept file: " + e.getMessage());
                    transferErrors.put(transferId, "Accept failed: " + e.getMessage());
                    CountDownLatch latch = completionLatches.get(transferId);
                    if (latch != null) latch.countDown();
                }
            }

            @Override
            public void onFileProgress(String transferId, long transferred, long total) {
                lastProgressBytes.computeIfAbsent(transferId, k -> new AtomicLong(0)).set(transferred);
                int pct = (int) ((transferred * 100) / total);
                if (pct % 20 == 0 || transferred == total) {
                    System.out.println("[Receiver] Progress " + transferId + ": "
                            + formatBytes(transferred) + "/" + formatBytes(total) + " (" + pct + "%)");
                }
            }

            @Override
            public void onFileComplete(String transferId, String filePath) {
                System.out.println("[Receiver] Transfer complete: " + transferId + " -> " + filePath);
                completedFiles.put(transferId, filePath);
                CountDownLatch latch = completionLatches.get(transferId);
                if (latch != null) latch.countDown();
            }

            @Override
            public void onFileError(String transferId, String error) {
                System.err.println("[Receiver] Transfer error: " + transferId + " - " + error);
                transferErrors.put(transferId, error);
                CountDownLatch latch = completionLatches.get(transferId);
                if (latch != null) latch.countDown();
            }
        });
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (nodeA != null) nodeA.stop();
        if (nodeB != null) nodeB.stop();

        // Clean up temp directories
        deleteDirectory(tempDirA);
        deleteDirectory(tempDirB);
        deleteDirectory(downloadDir);
        deleteDirectory(testFilesDir);
    }

    // --- Test Cases ---

    @Test
    @Order(1)
    public void testNodesReady() {
        assertTrue(nodeA.isRunning(), "Node A should be running");
        assertTrue(nodeB.isRunning(), "Node B should be running");
        assertNotNull(nodeA.getPeer(nodeB.getLocalFid()), "A should know B");
        assertNotNull(nodeB.getPeer(nodeA.getLocalFid()), "B should know A");
    }

    @Test
    @Order(2)
    public void testSmallFileTransfer() throws Exception {
        int fileSize = 10 * 1024; // 10 KB
        File srcFile = createTestFile("small_test.bin", fileSize);

        TransferResult result = transferAndVerify(srcFile, 30);
        System.out.println("[SmallFile] " + result);

        assertTrue(result.success, "Small file transfer should succeed: " + result.error);
        assertEquals(fileSize, result.receivedSize, "Received file size should match");
        assertEquals(result.sourceHash, result.receivedHash, "SHA-256 hash should match");
    }

    @Test
    @Order(3)
    public void testMediumFileTransfer() throws Exception {
        int fileSize = 1024 * 1024; // 1 MB
        File srcFile = createTestFile("medium_test.bin", fileSize);

        TransferResult result = transferAndVerify(srcFile, 60);
        System.out.println("[MediumFile] " + result);

        assertTrue(result.success, "Medium file transfer should succeed: " + result.error);
        assertEquals(fileSize, result.receivedSize, "Received file size should match");
        assertEquals(result.sourceHash, result.receivedHash, "SHA-256 hash should match");
        assertTrue(result.speedMBps > 0, "Speed should be positive");
    }

    @Test
    @Order(4)
    public void testLargeFileTransfer() throws Exception {
        int fileSize = 5 * 1024 * 1024; // 5 MB
        File srcFile = createTestFile("large_test.bin", fileSize);

        TransferResult result = transferAndVerify(srcFile, 120);
        System.out.println("[LargeFile] " + result);

        assertTrue(result.success, "Large file transfer should succeed: " + result.error);
        assertEquals(fileSize, result.receivedSize, "Received file size should match");
        assertEquals(result.sourceHash, result.receivedHash, "SHA-256 hash should match");
        assertTrue(result.speedMBps > 0.5,
                "Transfer speed should be > 0.5 MB/s on localhost, was " + String.format("%.2f", result.speedMBps) + " MB/s");
    }

    @Test
    @Order(5)
    public void testRealApkFileTransfer() throws Exception {
        File apkFile = new File("/Users/liuchangyong/Desktop/download/FreeSign1.apk");
        if (!apkFile.exists()) {
            System.out.println("[APK] FreeSign1.apk not found at expected path, skipping.");
            return; // Skip gracefully if the file doesn't exist
        }

        System.out.println("[APK] Found FreeSign1.apk: " + formatBytes(apkFile.length()));

        // Use a generous timeout proportional to file size (at least 0.5 MB/s)
        long timeoutSec = Math.max(120, (apkFile.length() / (512 * 1024)) + 60);
        TransferResult result = transferAndVerify(apkFile, timeoutSec);
        System.out.println("[APK] " + result);

        assertTrue(result.success, "APK file transfer should succeed: " + result.error);
        assertEquals(apkFile.length(), result.receivedSize, "APK file size should match");
        assertEquals(result.sourceHash, result.receivedHash, "APK SHA-256 hash should match");
    }

    // --- Core transfer helper ---

    /**
     * Transfer a file from nodeA to nodeB and verify.
     * Returns a TransferResult with timing and hash info.
     */
    private TransferResult transferAndVerify(File srcFile, long timeoutSeconds) throws Exception {
        TransferResult result = new TransferResult();
        result.fileName = srcFile.getName();
        result.sourceSize = srcFile.length();
        result.sourceHash = calculateHash(srcFile);

        // Register a latch before initiating the transfer
        // The actual transferId will be set by the file handler, so we set up
        // a temporary mapping after sendFile returns the transferId.
        long startTime = System.currentTimeMillis();

        String transferId = nodeA.sendFile(nodeB.getLocalFid(), srcFile);
        System.out.println("[Transfer] Initiated: " + srcFile.getName()
                + " (" + formatBytes(srcFile.length()) + "), transferId=" + transferId);

        // Ensure a latch exists for this transfer (receiver's onFileOfferReceived may
        // create one, but let's make sure)
        CountDownLatch latch = completionLatches.computeIfAbsent(transferId, k -> new CountDownLatch(1));

        // Wait for completion
        boolean completed = latch.await(timeoutSeconds, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        if (!completed) {
            // Check if there was an error
            String error = transferErrors.get(transferId);
            if (error != null) {
                result.success = false;
                result.error = error;
            } else {
                result.success = false;
                result.error = "Timeout after " + timeoutSeconds + "s. "
                        + "Last progress: " + lastProgressBytes.getOrDefault(transferId, new AtomicLong(0)).get()
                        + "/" + srcFile.length() + " bytes";
            }
            return result;
        }

        // Check for errors
        String error = transferErrors.get(transferId);
        if (error != null) {
            result.success = false;
            result.error = error;
            return result;
        }

        // Verify received file
        String receivedPath = completedFiles.get(transferId);
        if (receivedPath == null) {
            result.success = false;
            result.error = "No completed file path recorded";
            return result;
        }

        File receivedFile = new File(receivedPath);
        if (!receivedFile.exists()) {
            result.success = false;
            result.error = "Received file does not exist: " + receivedPath;
            return result;
        }

        result.receivedSize = receivedFile.length();
        result.receivedHash = calculateHash(receivedFile);
        result.elapsedMs = elapsed;
        result.speedMBps = (result.sourceSize / (1024.0 * 1024.0)) / (elapsed / 1000.0);
        result.success = result.sourceHash.equals(result.receivedHash)
                && result.sourceSize == result.receivedSize;
        if (!result.success) {
            result.error = "Hash or size mismatch: sourceHash=" + result.sourceHash
                    + " receivedHash=" + result.receivedHash
                    + " sourceSize=" + result.sourceSize
                    + " receivedSize=" + result.receivedSize;
        }

        // Clean up the completion tracking
        completionLatches.remove(transferId);
        completedFiles.remove(transferId);
        transferErrors.remove(transferId);
        lastProgressBytes.remove(transferId);

        return result;
    }

    // --- Utility methods ---

    /**
     * Create a test file with random content.
     */
    private File createTestFile(String name, int size) throws IOException {
        File file = testFilesDir.resolve(name).toFile();
        SecureRandom random = new SecureRandom();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] buffer = new byte[Math.min(size, 64 * 1024)];
            int written = 0;
            while (written < size) {
                int toWrite = Math.min(buffer.length, size - written);
                random.nextBytes(buffer);
                raf.write(buffer, 0, toWrite);
                written += toWrite;
            }
        }
        return file;
    }

    /**
     * Calculate SHA-256 hash of a file (same algorithm as FileHandler.calculateFileHash).
     */
    private static String calculateHash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var is = new java.io.BufferedInputStream(new java.io.FileInputStream(file))) {
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
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static void deleteDirectory(Path dir) {
        if (dir != null) {
            try {
                Files.walk(dir)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try { Files.delete(path); } catch (IOException e) { /* ignore */ }
                        });
            } catch (IOException e) { /* ignore */ }
        }
    }

    /**
     * Transfer test using DEFAULT 1350-byte packets (production config).
     * This reproduces the real-world scenario where StartFudpNode transfers files.
     * Creates its own pair of nodes with default packet size.
     */
    @Test
    @Order(6)
    public void testDefaultPacketSizeTransfer() throws Exception {
        File apkFile = new File("/Users/liuchangyong/Desktop/download/FreeSign1.apk");
        // Fall back to 5MB random file if APK not available
        File srcFile;
        if (apkFile.exists()) {
            srcFile = apkFile;
            System.out.println("[DefaultPkt] Using FreeSign1.apk: " + formatBytes(apkFile.length()));
        } else {
            srcFile = createTestFile("default_pkt_test.bin", 5 * 1024 * 1024);
            System.out.println("[DefaultPkt] Using 5MB random file");
        }

        // Create separate nodes with DEFAULT 1350-byte packet size
        SecureRandom random = new SecureRandom();
        byte[] keyC = new byte[32], keyD = new byte[32];
        random.nextBytes(keyC);
        random.nextBytes(keyD);

        Path tmpC = Files.createTempDirectory("fudp_ft_c");
        Path tmpD = Files.createTempDirectory("fudp_ft_d");
        Path dlDir = Files.createTempDirectory("fudp_ft_dl2");

        int portC = 19201, portD = 19202;

        // DEFAULT config - no setMaxPacketSize (uses 1350)
        NodeConfig cfgC = new NodeConfig();
        cfgC.setPort(portC);
        cfgC.setDataDir(tmpC.toString());

        NodeConfig cfgD = new NodeConfig();
        cfgD.setPort(portD);
        cfgD.setDataDir(tmpD.toString());

        FudpNode senderNode = new FudpNode(keyC, cfgC);
        FudpNode receiverNode = new FudpNode(keyD, cfgD);

        // Track result
        CountDownLatch latch = new CountDownLatch(1);
        String[] resultPath = {null};
        String[] resultError = {null};

        receiverNode.setEventListener(new NodeEventListener() {
            @Override
            public void onFileOfferReceived(String peerId, FileOffer offer) {
                System.out.println("[DefaultPkt] Receiver got offer: " + offer.getFileName()
                        + " (" + formatBytes(offer.getFileSize()) + ")");
                try {
                    receiverNode.acceptFile(offer.getTransferId(), dlDir.toString());
                } catch (IOException e) {
                    resultError[0] = "Accept failed: " + e.getMessage();
                    latch.countDown();
                }
            }

            @Override
            public void onFileProgress(String transferId, long transferred, long total) {
                int pct = (int) ((transferred * 100) / total);
                if (pct % 10 == 0 || transferred == total) {
                    System.out.println("[DefaultPkt] Progress: " + formatBytes(transferred)
                            + "/" + formatBytes(total) + " (" + pct + "%)");
                }
            }

            @Override
            public void onFileComplete(String transferId, String filePath) {
                System.out.println("[DefaultPkt] Transfer complete: " + filePath);
                resultPath[0] = filePath;
                latch.countDown();
            }

            @Override
            public void onFileError(String transferId, String error) {
                System.err.println("[DefaultPkt] Error: " + error);
                resultError[0] = error;
                latch.countDown();
            }
        });

        senderNode.start();
        receiverNode.start();
        Thread.sleep(200);

        senderNode.addPeer(receiverNode.getLocalFid(), receiverNode.getLocalPublicKey(), "127.0.0.1", portD);
        receiverNode.addPeer(senderNode.getLocalFid(), senderNode.getLocalPublicKey(), "127.0.0.1", portC);

        // Send file
        long startTime = System.currentTimeMillis();
        String transferId = senderNode.sendFile(receiverNode.getLocalFid(), srcFile);
        System.out.println("[DefaultPkt] Sending " + srcFile.getName() + " (transferId=" + transferId + ")");

        // Wait with generous timeout (but we want it to be fast)
        boolean done = latch.await(120, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        // Cleanup
        senderNode.stop();
        receiverNode.stop();
        deleteDirectory(tmpC);
        deleteDirectory(tmpD);

        // Verify
        if (!done) {
            fail("[DefaultPkt] TIMEOUT after 120s for " + srcFile.getName()
                    + " (" + formatBytes(srcFile.length()) + ") with 1350-byte packets");
        }
        if (resultError[0] != null) {
            fail("[DefaultPkt] Error: " + resultError[0]);
        }
        assertNotNull(resultPath[0], "Should have a result path");

        File received = new File(resultPath[0]);
        assertEquals(srcFile.length(), received.length(), "File size should match");
        assertEquals(calculateHash(srcFile), calculateHash(received), "SHA-256 hash should match");

        double speedMBps = (srcFile.length() / (1024.0 * 1024.0)) / (elapsed / 1000.0);
        System.out.println(String.format("[DefaultPkt] OK: %s (%s) in %.1fs @ %.2f MB/s (1350-byte packets)",
                srcFile.getName(), formatBytes(srcFile.length()), elapsed / 1000.0, speedMBps));

        // Speed assertion: at least 0.5 MB/s with default packets
        assertTrue(speedMBps > 0.5,
                "Transfer with default 1350-byte packets should be > 0.5 MB/s, was " + String.format("%.2f", speedMBps));

        deleteDirectory(dlDir);
    }

    // --- Result holder ---

    private static class TransferResult {
        String fileName;
        long sourceSize;
        long receivedSize;
        String sourceHash;
        String receivedHash;
        long elapsedMs;
        double speedMBps;
        boolean success;
        String error;

        @Override
        public String toString() {
            if (success) {
                return String.format("OK: %s (%s) in %.1fs @ %.2f MB/s | hash=%s",
                        fileName, formatBytes(sourceSize), elapsedMs / 1000.0, speedMBps,
                        sourceHash.substring(0, 16) + "...");
            } else {
                return String.format("FAIL: %s (%s) - %s",
                        fileName, formatBytes(sourceSize), error);
            }
        }
    }
}
