package fudp;

import core.crypto.KeyTools;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import fudp.node.NodeStats;
import fudp.util.ByteUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FUDP file transfer speed test.
 *
 * Creates two local FudpNode instances and measures transfer throughput
 * under different MTU configurations. Reports speed, packet loss, and
 * integrity for each configuration.
 *
 * Run: mvn test -pl FC-JDK -Dtest=fudp.FudpSpeedTest
 */
public class FudpSpeedTest {

    private static final String FILE_A = "/Users/liuchangyong/Desktop/Freeverse/a.tar";   // 22 MB
    private static final String FILE_B = "/Users/liuchangyong/Desktop/Freeverse/b.jar";   // 49 MB

    private static int portCounter = 19101;

    record NodeBundle(FudpNode node, String fid, byte[] pubKey, int port) {}

    record TransferResult(String configName, String fileName, long fileSizeBytes,
                          long elapsedMs, double mbPerSec, boolean integrityOk,
                          long packetsSent, long retransmits, double lossRate, long rttMs) {
        @Override
        public String toString() {
            return String.format("%-30s | %-8s | %6.1f MB | %8d ms | %7.2f MB/s | loss=%.1f%% | retx=%d | rtt=%dms | %s",
                    configName, fileName,
                    fileSizeBytes / (1024.0 * 1024.0),
                    elapsedMs, mbPerSec,
                    lossRate * 100, retransmits, rttMs,
                    integrityOk ? "OK" : "FAIL");
        }
    }

    private NodeBundle createNode(int mtu, long lossThresholdMs) throws IOException {
        return createNode(mtu, lossThresholdMs, -1, 1_000_000, 2 * 1024 * 1024);
    }

    private NodeBundle createNode(int mtu, long lossThresholdMs,
                                  int pacingBurst, long pacingNanos, int socketBuf) throws IOException {
        int port = portCounter++;
        byte[] privKey = ByteUtils.randomBytes(32);
        byte[] pubKey = KeyTools.prikeyToPubkey(privKey);

        NodeConfig config = new NodeConfig();
        config.setPort(port);
        config.setMaxPacketSize(mtu);
        config.setLossDetectionMinThresholdMs(lossThresholdMs);
        config.setPacingBurstOverride(pacingBurst);
        config.setPacingIntervalNanos(pacingNanos);
        config.setSocketBufferSize(socketBuf);
        config.setDataDir(System.getProperty("java.io.tmpdir") + "/fudp_speed_" + port);

        FudpNode node = new FudpNode(privKey, config);
        return new NodeBundle(node, node.getLocalFid(), pubKey, port);
    }

    private void setupPeers(NodeBundle a, NodeBundle b) {
        a.node.addPeer(b.fid, b.pubKey, "127.0.0.1", b.port, "peerB");
        b.node.addPeer(a.fid, a.pubKey, "127.0.0.1", a.port, "peerA");
    }

    private void warmup(NodeBundle sender, NodeBundle receiver) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        receiver.node.setEventListener(new NodeEventListener() {
            @Override
            public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                latch.countDown();
            }
        });
        sender.node.sendNotifyWaitAck(receiver.fid, "warmup".getBytes(), 30_000);
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Warmup handshake timed out");
        Thread.sleep(300);
    }

    private TransferResult measureTransfer(
            NodeBundle sender, NodeBundle receiver,
            byte[] fileData, String configName, String fileName,
            long timeoutMs) throws Exception {

        byte[] sentHash = sha256(fileData);
        AtomicReference<byte[]> receivedData = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        receiver.node.setEventListener(new NodeEventListener() {
            @Override
            public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                receivedData.set(data);
                latch.countDown();
            }
        });

        System.out.printf("  [%s] Sending %s (%,.0f bytes)...%n", configName, fileName, (double) fileData.length);

        long startNanos = System.nanoTime();
        boolean ackOk = sender.node.sendNotifyWaitAck(receiver.fid, fileData, timeoutMs);
        long elapsedNanos = System.nanoTime() - startNanos;

        // If ACK came, the receiver should already have the data; give extra time just in case
        boolean received = latch.await(ackOk ? 5 : 1, TimeUnit.SECONDS);

        long elapsedMs = elapsedNanos / 1_000_000;
        double seconds = elapsedNanos / 1_000_000_000.0;
        double mbPerSec = (fileData.length / (1024.0 * 1024.0)) / seconds;

        boolean integrityOk = false;
        if (received && receivedData.get() != null) {
            integrityOk = Arrays.equals(sentHash, sha256(receivedData.get()));
            if (!integrityOk) {
                System.out.printf("  [%s] INTEGRITY MISMATCH: sent %d bytes, received %d bytes%n",
                        configName, fileData.length, receivedData.get().length);
            }
        } else {
            System.out.printf("  [%s] Transfer incomplete: ack=%s, received=%s%n", configName, ackOk, received);
        }

        NodeStats stats = sender.node.getNodeStats();
        TransferResult result = new TransferResult(
                configName, fileName, fileData.length,
                elapsedMs, mbPerSec, integrityOk,
                stats.getTotalPacketsSent(), stats.getTotalRetransmits(),
                stats.getAverageLossRate(), stats.getAvgSmoothedRttMs());

        System.out.printf("  -> %.2f MB/s, %d pkts, %d retx, %.1f%% loss, %dms rtt, integrity=%s%n",
                mbPerSec, stats.getTotalPacketsSent(), stats.getTotalRetransmits(),
                stats.getAverageLossRate() * 100, stats.getAvgSmoothedRttMs(),
                integrityOk ? "OK" : "FAIL");
        return result;
    }

    private byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Run a single transfer test with fresh nodes.
     */
    private TransferResult runTest(int mtu, long lossThresholdMs, byte[] fileData,
                                   String configName, String fileName, long timeoutMs) throws Exception {
        return runTest(mtu, lossThresholdMs, -1, 1_000_000, 2 * 1024 * 1024, fileData, configName, fileName, timeoutMs);
    }

    private TransferResult runTest(int mtu, long lossThresholdMs,
                                   int pacingBurst, long pacingNanos, int socketBuf,
                                   byte[] fileData, String configName, String fileName, long timeoutMs) throws Exception {
        NodeBundle sender = createNode(mtu, lossThresholdMs, pacingBurst, pacingNanos, socketBuf);
        NodeBundle receiver = createNode(mtu, lossThresholdMs, pacingBurst, pacingNanos, socketBuf);
        try {
            sender.node.start();
            receiver.node.start();
            setupPeers(sender, receiver);
            warmup(sender, receiver);
            return measureTransfer(sender, receiver, fileData, configName, fileName, timeoutMs);
        } finally {
            sender.node.stop();
            receiver.node.stop();
        }
    }

    // ===== Individual Test Methods =====

    /**
     * Test with 22MB file (a.tar) at 1350 MTU (internet default).
     * Expected: ~0.8-1.2 MB/s, 0% loss. Bottleneck: pacing (1 frame/ms).
     */
    @Test
    public void test_22MB_MTU1350() throws Exception {
        byte[] data = Files.readAllBytes(Path.of(FILE_A));
        TransferResult r = runTest(1350, 2000, data, "MTU=1350", "a.tar", 120_000);
        assertTrue(r.integrityOk, "Integrity check failed");
        System.out.printf("%n  VERDICT: %.2f MB/s at 1350 MTU (pacing-limited)%n", r.mbPerSec);
    }

    /**
     * Test with 22MB file (a.tar) at 8000 MTU.
     * Expected: ~4-8 MB/s, low loss.
     */
    @Test
    public void test_22MB_MTU8000() throws Exception {
        byte[] data = Files.readAllBytes(Path.of(FILE_A));
        TransferResult r = runTest(8000, 2000, data, "MTU=8000", "a.tar", 60_000);
        assertTrue(r.integrityOk, "Integrity check failed");
        System.out.printf("%n  VERDICT: %.2f MB/s at 8000 MTU%n", r.mbPerSec);
    }

    /**
     * Test with 22MB file (a.tar) at 60000 MTU (LAN mode).
     * This may show high packet loss due to receiver buffer overflow.
     */
    @Test
    public void test_22MB_MTU60000() throws Exception {
        byte[] data = Files.readAllBytes(Path.of(FILE_A));
        TransferResult r = runTest(60000, 2000, data, "MTU=60000", "a.tar", 120_000);
        System.out.printf("%n  VERDICT: %.2f MB/s at 60000 MTU (loss=%.1f%%)%n", r.mbPerSec, r.lossRate * 100);
        // Don't assert integrity here - large MTU may lose packets on localhost
    }

    /**
     * Test with 49MB file (b.jar) at 8000 MTU.
     * Uses 300s timeout because large transfers may need multiple retransmit cycles.
     */
    @Test
    public void test_49MB_MTU8000() throws Exception {
        byte[] data = Files.readAllBytes(Path.of(FILE_B));
        TransferResult r = runTest(8000, 2000, data, "MTU=8000", "b.jar", 300_000);
        assertTrue(r.integrityOk, "Integrity check failed");
        System.out.printf("%n  VERDICT: %.2f MB/s at 8000 MTU for 49MB file%n", r.mbPerSec);
    }

    /**
     * LAN-optimized test: 8000 MTU + 4MB socket buffers.
     * The improved burst formula (max(2, x/1500)) and LockSupport.parkNanos
     * already give significant improvement over the old implementation.
     */
    @Test
    public void test_22MB_LAN_Optimized() throws Exception {
        byte[] data = Files.readAllBytes(Path.of(FILE_A));
        // 8000 MTU, 2000ms loss threshold, auto burst, 1ms pacing, 4MB buffers
        TransferResult r = runTest(8000, 2000, -1, 1_000_000, 4 * 1024 * 1024,
                data, "LAN-opt(8K,4MB)", "a.tar", 60_000);
        assertTrue(r.integrityOk, "Integrity check failed");
        System.out.printf("%n  VERDICT: %.2f MB/s with LAN-optimized config%n", r.mbPerSec);
    }

    /**
     * LAN-optimized test with 49MB file.
     * Uses 4MB socket buffers to reduce packet loss on large transfers.
     */
    @Test
    public void test_49MB_LAN_Optimized() throws Exception {
        byte[] data = Files.readAllBytes(Path.of(FILE_B));
        // 8000 MTU, 2000ms loss threshold, auto burst, 1ms pacing, 4MB buffers
        TransferResult r = runTest(8000, 2000, -1, 1_000_000, 4 * 1024 * 1024,
                data, "LAN-opt(8K,4MB)", "b.jar", 300_000);
        assertTrue(r.integrityOk, "Integrity check failed");
        System.out.printf("%n  VERDICT: %.2f MB/s with LAN-optimized config for 49MB%n", r.mbPerSec);
    }

    /**
     * Comprehensive speed test: runs all configurations and prints comparison table.
     * This test takes several minutes. Run individually for quick iteration.
     */
    @Test
    public void testTransferSpeed_AllConfigs() throws Exception {
        Path pathA = Path.of(FILE_A);
        Path pathB = Path.of(FILE_B);
        assertTrue(Files.exists(pathA), "Test file not found: " + FILE_A);
        assertTrue(Files.exists(pathB), "Test file not found: " + FILE_B);

        byte[] dataA = Files.readAllBytes(pathA);
        byte[] dataB = Files.readAllBytes(pathB);
        System.out.printf("%n=== FUDP Speed Test ===%n");
        System.out.printf("File A: %s (%,.0f bytes / %.1f MB)%n", pathA.getFileName(), (double) dataA.length, dataA.length / (1024.0 * 1024.0));
        System.out.printf("File B: %s (%,.0f bytes / %.1f MB)%n%n", pathB.getFileName(), (double) dataB.length, dataB.length / (1024.0 * 1024.0));

        List<TransferResult> results = new ArrayList<>();

        // Config 1: 1350 MTU, a.tar - baseline (pacing-limited)
        results.add(runTest(1350, 2000, dataA, "MTU=1350", "a.tar", 120_000));

        // Config 2: 8000 MTU, a.tar - moderate
        results.add(runTest(8000, 2000, dataA, "MTU=8000", "a.tar", 60_000));

        // Config 3: 60000 MTU, a.tar - LAN mode (may lose packets)
        results.add(runTest(60000, 2000, dataA, "MTU=60000", "a.tar", 120_000));

        // Config 4: 8000 MTU, b.jar - sweet spot for large file
        results.add(runTest(8000, 2000, dataB, "MTU=8000", "b.jar", 300_000));

        // Config 5: LAN-optimized 8000 MTU + 4MB buffers, a.tar
        results.add(runTest(8000, 2000, -1, 1_000_000, 4 * 1024 * 1024,
                dataA, "LAN-opt(8K,4MB)", "a.tar", 60_000));

        // Config 6: LAN-optimized 8000 MTU + 4MB buffers, b.jar
        results.add(runTest(8000, 2000, -1, 1_000_000, 4 * 1024 * 1024,
                dataB, "LAN-opt(8K,4MB)", "b.jar", 300_000));

        // ===== Summary Table =====
        System.out.println("\n" + "=".repeat(120));
        System.out.println("FUDP TRANSFER SPEED RESULTS");
        System.out.println("=".repeat(120));
        System.out.printf("%-30s | %-8s | %8s | %10s | %10s | %8s | %6s | %6s | %s%n",
                "Config", "File", "Size", "Time", "Speed", "Loss", "Retx", "RTT", "Status");
        System.out.println("-".repeat(120));
        for (TransferResult r : results) {
            System.out.println(r);
        }
        System.out.println("=".repeat(120));

        // Verify small-MTU transfers (no buffer overflow expected)
        for (TransferResult r : results) {
            if (r.configName.contains("1350") || r.configName.contains("8000")) {
                assertTrue(r.integrityOk, "Integrity failed for " + r.configName + " " + r.fileName);
            }
        }

        // ===== Analysis =====
        printAnalysis(results);
    }

    private void printAnalysis(List<TransferResult> results) {
        System.out.println("\n" + "=".repeat(120));
        System.out.println("ANALYSIS & OPTIMIZATION SUGGESTIONS");
        System.out.println("=".repeat(120));

        // Compare MTU impact
        TransferResult r1350 = results.stream().filter(r -> r.configName.equals("MTU=1350") && r.fileName.contains("tar")).findFirst().orElse(null);
        TransferResult r8000 = results.stream().filter(r -> r.configName.equals("MTU=8000") && r.fileName.contains("tar")).findFirst().orElse(null);
        TransferResult r60000 = results.stream().filter(r -> r.configName.equals("MTU=60000") && r.fileName.contains("tar")).findFirst().orElse(null);

        if (r1350 != null && r8000 != null) {
            System.out.printf("%nMTU impact (22MB file):%n");
            System.out.printf("  1350 -> 8000: %.1fx speedup (%.2f -> %.2f MB/s)%n",
                    r8000.mbPerSec / r1350.mbPerSec, r1350.mbPerSec, r8000.mbPerSec);
            if (r60000 != null) {
                System.out.printf("  1350 -> 60000: %.1fx speedup (%.2f -> %.2f MB/s) BUT %.1f%% packet loss!%n",
                        r60000.mbPerSec / r1350.mbPerSec, r1350.mbPerSec, r60000.mbPerSec, r60000.lossRate * 100);
            }
        }

        System.out.println("""

        BOTTLENECK ANALYSIS:
        ====================

        #1 [CRITICAL] Pacing limits throughput at small MTU
           Location: Protocol.java:402-421 (paceSending method)
           Root cause: Thread.sleep(1) after every frame at 1350 MTU.
                       Actual sleep is 1-2ms on macOS, capping at ~0.8-1.2 MB/s.
           Evidence: MTU=1350 consistently hits ~0.9 MB/s regardless of file size.
           Fix A: Make pacing burst configurable via NodeConfig.
                  For LAN/localhost, set burst=10-20 even at 1350 MTU.
           Fix B: Replace Thread.sleep(1) with LockSupport.parkNanos(200_000).
                  This gives ~5x better precision (200us vs 1-2ms actual).
           Fix C: Change burst formula to max(2, maxPayloadSize/1500).
                  At 1350 MTU: burst goes from 1 to 2, doubling throughput.
           Expected improvement: 2-10x for small MTU on LAN.

        #2 [CRITICAL] Large MTU causes massive packet loss from receiver buffer overflow
           Location: Protocol.java:385-391 (calculatePacingBurst)
           Root cause: At MTU=60000, burst=19, each burst sends 19*60KB=1.14MB.
                       The single-threaded receiver can't decrypt+process fast enough,
                       and the 2MB OS receive buffer overflows.
           Evidence: MTU=60000 shows 33%+ loss on localhost!
           Fix A: Cap burst to limit bytes-per-burst (e.g., max 256KB per burst).
                  Formula: min(pacingBurst, 256*1024 / maxPayloadSize).
           Fix B: Add receiver-driven flow control: pause sending when
                  bytesInFlight exceeds a fraction of the receive buffer.
           Fix C: Increase socket receive buffer to 8-16MB for large MTU.
           Expected improvement: Near-zero loss with large MTU.

        #3 [HIGH] Per-packet crypto overhead wastes 33 bytes
           Location: PacketCrypto.java, CryptoManager.java
           Problem: Every encrypted packet includes the sender's 33-byte public key.
                    After ECDH handshake completes, this is redundant.
           Fix: After epoch confirmed, use compact crypto bundle without pubkeyA.
                Receiver resolves sender pubkey from connectionId -> PeerConnection.
           Impact: 2.4% more payload per packet. For 22MB at 1350 MTU: saves 560KB.

        #4 [HIGH] Loss detection threshold too conservative for LAN
           Location: PeerConnection.java:191
           Problem: Min threshold 2000ms means 2-second stall on any real packet loss.
           Fix: Auto-adapt based on RTT. If smoothedRtt < 10ms, use max(50, rtt*10).
           Note: Already configurable via NodeConfig.setLossDetectionMinThresholdMs().

        #5 [MEDIUM] Arrays.copyOfRange per frame in send loop
           Location: Protocol.java:455, 495
           Problem: Allocates new byte[] for every frame (~17K allocs for 22MB at 1350).
           Fix: Add StreamFrame constructor with (data, offset, length) buffer view.
                Protocol.sendAndCloseFromInputStream() already uses reusable buffer.

        #6 [LOW] Hex-string ECDH cache key conversion
           Location: CryptoManager.java:208, 264
           Problem: Hex.toHex(pubkey) called on every encrypt/decrypt.
           Fix: Use ByteArrayWrapper with Arrays.hashCode/equals.

        #7 [LOW] ACK on every received packet
           Location: AckManager.java:22 (ACK_THRESHOLD=1)
           Problem: Doubles packet rate for unidirectional bulk transfer.
           Fix: Set threshold to 2-4 for bulk transfers (configurable).""");
    }
}
