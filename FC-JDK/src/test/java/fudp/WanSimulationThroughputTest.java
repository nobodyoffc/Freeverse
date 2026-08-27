package fudp;

import core.crypto.KeyTools;
import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import fudp.util.ByteUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Throughput tests over a simulated WAN path (latency + loss + bandwidth
 * bottleneck), guarding against the field failure where the congestion window
 * pinned at its 14400-byte floor and a 253MB upload to a 1MB/s Singapore VPS
 * crawled at ~35KB/s:
 * - fire-once ACK frames: one lost ACK orphaned its packet numbers forever,
 *   causing continuous false loss detection (fixed: ACKs re-advertise ranges);
 * - CUBIC computed in bytes instead of MSS units: K ~= 47s, so the window
 *   could shrink every second but needed a minute to grow (fixed: MSS units).
 *
 * The WAN test asserts the effective throughput reaches a healthy fraction of
 * the simulated bottleneck — with a pinned window it would run ~25x slower.
 */
public class WanSimulationThroughputTest {

    /**
     * UDP forwarder simulating a WAN path: one-way delay in both directions,
     * a small random loss rate, and a client-to-server bandwidth bottleneck
     * modelled as a real router: a finite FIFO queue drained at the bottleneck
     * rate, dropping only on queue overflow (tail drop). This is how actual
     * paths behave — packets are queued (adding delay) before any are lost.
     */
    static class WanProxy implements Runnable {
        /** Max queuing delay the bottleneck buffer can hold (typical router). */
        static final long QUEUE_CAP_MS = 150;

        final DatagramSocket socket;
        /**
         * Server-facing socket: c2s packets exit through it, so its local port
         * is the "client address" the server observes. {@link #rebind()}
         * replaces it to simulate a NAT rebinding the client's mapping — the
         * server suddenly sees the same client traffic from a new source
         * address, and its packets to the old address are lost.
         */
        volatile DatagramSocket serverSide;
        final InetSocketAddress serverAddr;
        volatile java.net.SocketAddress clientAddr;
        final long delayMs;
        final double lossRate;
        final long bandwidthBps; // client->server cap, 0 = unlimited

        // Periodic RTT jitter spikes (0 = disabled): every jitterPeriodMs, all
        // packets within a jitterDurationMs window get +jitterExtraMs delay in
        // BOTH directions — models the multi-second RTT spikes seen on
        // congested international routes.
        volatile long jitterPeriodMs = 0;
        volatile long jitterDurationMs = 0;
        volatile long jitterExtraMs = 0;

        // Per-packet random extra delay 0..reorderJitterMs (0 = disabled):
        // packets scheduled independently overtake each other — models the
        // deep packet reordering of load-balanced international routes, which
        // must not be misread as loss by gap-based detection.
        volatile long reorderJitterMs = 0;

        volatile boolean running = true;

        private final ScheduledExecutorService delayer =
                Executors.newScheduledThreadPool(2, r -> {
                    Thread t = new Thread(r, "wan-proxy-delayer");
                    t.setDaemon(true);
                    return t;
                });

        // Virtual clock of when the bottleneck link becomes free (ns).
        private long bottleneckFreeAtNs = 0;
        final java.util.concurrent.atomic.AtomicInteger droppedByLoss = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger droppedByBottleneck = new java.util.concurrent.atomic.AtomicInteger();

        WanProxy(int listenPort, int serverPort, long delayMs, double lossRate, long bandwidthBps) throws Exception {
            this.socket = new DatagramSocket(listenPort);
            this.serverSide = new DatagramSocket();
            this.serverAddr = new InetSocketAddress("127.0.0.1", serverPort);
            this.delayMs = delayMs;
            this.lossRate = lossRate;
            this.bandwidthBps = bandwidthBps;
        }

        /** Simulate a NAT rebind: the server-facing source address changes. */
        synchronized void rebind() throws Exception {
            DatagramSocket old = serverSide;
            serverSide = new DatagramSocket();
            startServerSideReader();
            old.close();
        }

        private void startServerSideReader() {
            DatagramSocket s = serverSide;
            Thread t = new Thread(() -> serverSideLoop(s), "wan-proxy-s2c-" + s.getLocalPort());
            t.setDaemon(true);
            t.start();
        }

        /** Forward server->client packets arriving on one server-facing socket. */
        private void serverSideLoop(DatagramSocket s) {
            byte[] buf = new byte[65535];
            while (running && !s.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    s.receive(p);
                    byte[] data = java.util.Arrays.copyOf(p.getData(), p.getLength());
                    if (ThreadLocalRandom.current().nextDouble() < lossRate) {
                        droppedByLoss.incrementAndGet();
                        continue;
                    }
                    java.net.SocketAddress dest = clientAddr;
                    if (dest == null) continue;
                    long extraDelayMs = 0;
                    if (jitterPeriodMs > 0
                            && System.currentTimeMillis() % jitterPeriodMs < jitterDurationMs) {
                        extraDelayMs = jitterExtraMs;
                    }
                    long totalDelay = delayMs + extraDelayMs;
                    delayer.schedule(() -> {
                        try {
                            socket.send(new DatagramPacket(data, data.length, dest));
                        } catch (Exception ignored) {
                        }
                    }, totalDelay, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    if (running && !s.isClosed()) e.printStackTrace();
                }
            }
        }

        /**
         * @return additional queuing delay in ms for this packet, or -1 when
         *         the bottleneck queue is full (tail drop).
         */
        private synchronized long enqueueBottleneck(int bytes) {
            if (bandwidthBps <= 0) return 0;
            long now = System.nanoTime();
            long serializationNs = (long) (bytes * 1e9 / bandwidthBps);
            long startNs = Math.max(now, bottleneckFreeAtNs);
            long queueDelayNs = startNs - now;
            if (queueDelayNs > QUEUE_CAP_MS * 1_000_000L) {
                return -1; // queue full
            }
            bottleneckFreeAtNs = startNs + serializationNs;
            return (queueDelayNs + serializationNs) / 1_000_000L;
        }

        /** Client-facing loop: forward client->server through the (replaceable) server-facing socket. */
        @Override
        public void run() {
            startServerSideReader();
            byte[] buf = new byte[65535];
            while (running) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    byte[] data = java.util.Arrays.copyOf(p.getData(), p.getLength());
                    clientAddr = p.getSocketAddress();

                    if (ThreadLocalRandom.current().nextDouble() < lossRate) {
                        droppedByLoss.incrementAndGet();
                        continue;
                    }
                    long extraDelayMs = enqueueBottleneck(data.length);
                    if (extraDelayMs < 0) {
                        droppedByBottleneck.incrementAndGet();
                        continue;
                    }
                    if (jitterPeriodMs > 0
                            && System.currentTimeMillis() % jitterPeriodMs < jitterDurationMs) {
                        extraDelayMs += jitterExtraMs;
                    }
                    if (reorderJitterMs > 0) {
                        extraDelayMs += ThreadLocalRandom.current().nextLong(reorderJitterMs + 1);
                    }

                    delayer.schedule(() -> {
                        try {
                            serverSide.send(new DatagramPacket(data, data.length, serverAddr));
                        } catch (Exception ignored) {
                            // Socket replaced mid-rebind: realistic packet loss.
                        }
                    }, delayMs + extraDelayMs, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    if (running) e.printStackTrace();
                }
            }
        }

        void stop() {
            running = false;
            socket.close();
            serverSide.close();
            delayer.shutdownNow();
        }
    }

    record NodeBundle(FudpNode node, String fid, byte[] pubKey) {}

    private NodeBundle createNode(int port, long requestTimeoutMs) throws Exception {
        byte[] privKey = ByteUtils.randomBytes(32);
        byte[] pubKey = KeyTools.prikeyToPubkey(privKey);
        NodeConfig config = new NodeConfig();
        config.setPort(port);
        config.setMaxPacketSize(1400); // internet-like MTU
        config.setRequestTimeoutMs(requestTimeoutMs);
        config.setDataDir(System.getProperty("java.io.tmpdir") + "/fudp_wan_" + port + "_" + System.nanoTime());
        FudpNode node = new FudpNode(privKey, config);
        return new NodeBundle(node, node.getLocalFid(), pubKey);
    }

    private NodeEventListener echoServer(FudpNode serverNode) {
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wan-test-server-worker");
            t.setDaemon(true);
            return t;
        });
        byte[] responseBody = ByteUtils.randomBytes(300);
        return new NodeEventListener() {
            @Override
            public void onRequestReceived(String peerId, long connectionId, long requestId,
                                          String serviceName, byte[] data) {
                worker.submit(() -> {
                    try {
                        serverNode.respond(peerId, connectionId, requestId,
                                ResponseMessage.STATUS_SUCCESS, responseBody);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        };
    }

    /**
     * Simulated WAN modelled on the field path (SG VPS, scp measured 1.1MB/s):
     * 100ms one-way delay (200ms RTT), 0.1% random loss, and a 1.5MB/s
     * bottleneck with a 150ms tail-drop queue. A streamed upload must reach a
     * healthy fraction of the bottleneck; the historical failure modes ran at
     * ~35KB/s (pinned window) and ~100KB/s decaying (quadratic reassembly +
     * burst-clipped pacing).
     */
    @Test
    public void streamedUploadOverWanPath() throws Exception {
        int serverPort = 19601, clientPort = 19602, proxyPort = 19603;
        NodeBundle server = createNode(serverPort, 15_000);
        NodeBundle client = createNode(clientPort, 15_000);
        WanProxy proxy = new WanProxy(proxyPort, serverPort, 100, 0.001, 1_500_000);
        Thread proxyThread = new Thread(proxy, "wan-proxy");
        proxyThread.setDaemon(true);

        server.node().setEventListener(echoServer(server.node()));

        try {
            server.node().start();
            client.node().start();
            proxyThread.start();
            client.node().addPeer(server.fid(), server.pubKey(), "127.0.0.1", proxyPort, "server");

            ResponseMessage warm = client.node()
                    .request(server.fid(), "svc", ByteUtils.randomBytes(200))
                    .get(20, TimeUnit.SECONDS);
            assertTrue(warm.isSuccess(), "warmup through WAN proxy should succeed");

            // Default 6MB keeps the suite fast; override for field-scale runs:
            // mvn test -Dtest=WanSimulationThroughputTest -Dwan.test.mb=30
            int size = Integer.getInteger("wan.test.mb", 6) * 1024 * 1024;
            byte[] payload = ByteUtils.randomBytes(size);
            long start = System.currentTimeMillis();
            ResponseMessage resp = client.node().requestWithStream(
                            server.fid(), "svc", new byte[0],
                            new ByteArrayInputStream(payload), size, null)
                    .get(300, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - start;
            double kbps = size / 1024.0 / (elapsedMs / 1000.0);

            System.out.printf("WAN upload: %d bytes in %dms = %.0f KB/s (lossDrops=%d, bottleneckDrops=%d)%n",
                    size, elapsedMs, kbps, proxy.droppedByLoss.get(), proxy.droppedByBottleneck.get());

            assertTrue(resp != null && resp.isSuccess(), "WAN upload must succeed");
            // Healthy CC sustains 600KB/s-1.2MB/s on the simulated 1.5MB/s
            // bottleneck (run-to-run variance from random loss timing).
            // Historical failure modes: ~35KB/s (pinned window), ~100-270KB/s
            // decaying (quadratic reassembly, burst-clipped pacing). Assert
            // well above those with margin for CI timing variance.
            assertTrue(kbps > 400,
                    "throughput " + kbps + " KB/s — collapsed toward a known failure mode on the 1.5MB/s bottleneck");
        } finally {
            proxy.stop();
            client.node().stop();
            server.node().stop();
        }
    }

    /**
     * The field failure mode on the SG VPS path: periodic RTT jitter spikes
     * (congested international routes routinely add +1s for short windows).
     * Purely time-based loss detection expired the whole flight on every
     * spike and shrank the window each time, pinning throughput at ~55KB/s on
     * a 1MB/s path. With gap-based loss detection as the only congestion
     * signal (timeouts retransmit without shrinking), throughput must stay a
     * healthy fraction of the bottleneck despite the spikes.
     */
    @Test
    public void uploadSurvivesRttJitterSpikes() throws Exception {
        int serverPort = 19621, clientPort = 19622, proxyPort = 19623;
        NodeBundle server = createNode(serverPort, 15_000);
        NodeBundle client = createNode(clientPort, 15_000);
        WanProxy proxy = new WanProxy(proxyPort, serverPort, 100, 0.001, 1_500_000);
        proxy.jitterPeriodMs = 4000;
        proxy.jitterDurationMs = 600;
        proxy.jitterExtraMs = 900;
        Thread proxyThread = new Thread(proxy, "wan-proxy-jitter");
        proxyThread.setDaemon(true);

        server.node().setEventListener(echoServer(server.node()));

        try {
            server.node().start();
            client.node().start();
            proxyThread.start();
            client.node().addPeer(server.fid(), server.pubKey(), "127.0.0.1", proxyPort, "server");

            ResponseMessage warm = client.node()
                    .request(server.fid(), "svc", ByteUtils.randomBytes(200))
                    .get(20, TimeUnit.SECONDS);
            assertTrue(warm.isSuccess(), "warmup through jittery proxy should succeed");

            int size = 6 * 1024 * 1024;
            byte[] payload = ByteUtils.randomBytes(size);
            long start = System.currentTimeMillis();
            ResponseMessage resp = client.node().requestWithStream(
                            server.fid(), "svc", new byte[0],
                            new ByteArrayInputStream(payload), size, null)
                    .get(300, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - start;
            double kbps = size / 1024.0 / (elapsedMs / 1000.0);

            System.out.printf("jittery WAN upload: %d bytes in %dms = %.0f KB/s (lossDrops=%d, bottleneckDrops=%d)%n",
                    size, elapsedMs, kbps, proxy.droppedByLoss.get(), proxy.droppedByBottleneck.get());

            assertTrue(resp != null && resp.isSuccess(), "jittery WAN upload must succeed");
            // Spurious-timeout collapse ran at ~55KB/s in the field. Jitter
            // costs some throughput, but the window must not collapse.
            assertTrue(kbps > 250,
                    "throughput " + kbps + " KB/s — window collapsed under RTT jitter spikes");
        } finally {
            proxy.stop();
            client.node().stop();
            server.node().stop();
        }
    }

    /**
     * Deep packet reordering (field observation: heavily load-balanced
     * international routes reorder by dozens of packets). Gap-based loss
     * detection with a FIXED small packet threshold misreads reordering as
     * loss and multiplies the window down on every misfire (~50K window /
     * ~120KB/s in the field on a 900KB/s path). The adaptive RACK-style
     * threshold plus the RTT age guard must keep throughput healthy.
     */
    @Test
    public void uploadSurvivesDeepPacketReordering() throws Exception {
        int serverPort = 19641, clientPort = 19642, proxyPort = 19643;
        NodeBundle server = createNode(serverPort, 15_000);
        NodeBundle client = createNode(clientPort, 15_000);
        WanProxy proxy = new WanProxy(proxyPort, serverPort, 100, 0.001, 1_500_000);
        proxy.reorderJitterMs = 40; // up to 40ms swap ≈ dozens of packets deep
        Thread proxyThread = new Thread(proxy, "wan-proxy-reorder");
        proxyThread.setDaemon(true);

        server.node().setEventListener(echoServer(server.node()));

        try {
            server.node().start();
            client.node().start();
            proxyThread.start();
            client.node().addPeer(server.fid(), server.pubKey(), "127.0.0.1", proxyPort, "server");

            ResponseMessage warm = client.node()
                    .request(server.fid(), "svc", ByteUtils.randomBytes(200))
                    .get(20, TimeUnit.SECONDS);
            assertTrue(warm.isSuccess(), "warmup through reordering proxy should succeed");

            int size = 6 * 1024 * 1024;
            byte[] payload = ByteUtils.randomBytes(size);
            long start = System.currentTimeMillis();
            ResponseMessage resp = client.node().requestWithStream(
                            server.fid(), "svc", new byte[0],
                            new ByteArrayInputStream(payload), size, null)
                    .get(300, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - start;
            double kbps = size / 1024.0 / (elapsedMs / 1000.0);

            System.out.printf("reordering WAN upload: %d bytes in %dms = %.0f KB/s (lossDrops=%d, bottleneckDrops=%d)%n",
                    size, elapsedMs, kbps, proxy.droppedByLoss.get(), proxy.droppedByBottleneck.get());

            assertTrue(resp != null && resp.isSuccess(), "reordering WAN upload must succeed");
            assertTrue(kbps > 400,
                    "throughput " + kbps + " KB/s — window collapsed under packet reordering");
        } finally {
            proxy.stop();
            client.node().stop();
            server.node().stop();
        }
    }

    /**
     * NAT rebind mid-session (field failure 2026-07-14): the client's NAT
     * mapping changes, so the server suddenly sees the same connection's
     * packets from a NEW source address. Address-keyed connection resolution
     * used to create a phantom new server-side connection: fresh packet
     * numbers looked like replays, ACKs/responses went to the dead address,
     * and the restarted stream allocator collided with the client's retired
     * stream IDs — every request after the rebind timed out while uploads
     * were still stored server-side. With remote-connId-keyed resolution the
     * connection must MIGRATE and requests before, after, and ACROSS the
     * rebind must all succeed.
     */
    @Test
    public void survivesNatRebindMidSession() throws Exception {
        int serverPort = 19631, clientPort = 19632, proxyPort = 19633;
        NodeBundle server = createNode(serverPort, 10_000);
        NodeBundle client = createNode(clientPort, 10_000);
        WanProxy proxy = new WanProxy(proxyPort, serverPort, 50, 0.0, 3_000_000);
        Thread proxyThread = new Thread(proxy, "wan-proxy-rebind");
        proxyThread.setDaemon(true);

        server.node().setEventListener(echoServer(server.node()));

        try {
            server.node().start();
            client.node().start();
            proxyThread.start();
            client.node().addPeer(server.fid(), server.pubKey(), "127.0.0.1", proxyPort, "server");

            // Bootstrap requests advance the client's stream allocator past the
            // IDs a rebuilt server-side allocator would restart from — the
            // exact collision precondition seen in the field.
            for (int i = 0; i < 3; i++) {
                ResponseMessage r = client.node()
                        .request(server.fid(), "svc", ByteUtils.randomBytes(200))
                        .get(20, TimeUnit.SECONDS);
                assertTrue(r.isSuccess(), "bootstrap request " + i + " should succeed");
            }

            // Upload before the rebind
            byte[] payload = ByteUtils.randomBytes(2 * 1024 * 1024);
            ResponseMessage up1 = client.node().requestWithStream(
                            server.fid(), "svc", new byte[0],
                            new ByteArrayInputStream(payload), payload.length, null)
                    .get(60, TimeUnit.SECONDS);
            assertTrue(up1.isSuccess(), "upload before rebind must succeed");

            // NAT rebind: server now sees a new client address
            proxy.rebind();

            // Requests after the rebind — the field failure: responses were
            // swallowed and every one of these timed out.
            ResponseMessage check = client.node()
                    .request(server.fid(), "svc", ByteUtils.randomBytes(200))
                    .get(20, TimeUnit.SECONDS);
            assertTrue(check.isSuccess(), "small request after rebind must succeed");

            ResponseMessage up2 = client.node().requestWithStream(
                            server.fid(), "svc", new byte[0],
                            new ByteArrayInputStream(payload), payload.length, null)
                    .get(60, TimeUnit.SECONDS);
            assertTrue(up2.isSuccess(), "upload after rebind must succeed");

            // Rebind DURING a transfer: the upload must survive the migration.
            byte[] big = ByteUtils.randomBytes(4 * 1024 * 1024);
            CompletableFuture<ResponseMessage> f = client.node().requestWithStream(
                    server.fid(), "svc", new byte[0],
                    new ByteArrayInputStream(big), big.length, null);
            Thread.sleep(1500);
            proxy.rebind();
            ResponseMessage up3 = f.get(120, TimeUnit.SECONDS);
            assertTrue(up3.isSuccess(), "upload spanning a mid-transfer rebind must succeed");

            System.out.println("NAT rebind test passed: all requests succeeded across two rebinds");
        } finally {
            proxy.stop();
            client.node().stop();
            server.node().stop();
        }
    }

    /**
     * Localhost baseline (no proxy): guards against the congestion gate
     * regressing fast-path throughput. Direct 16MB streamed upload.
     */
    @Test
    public void localhostBaselineThroughput() throws Exception {
        int serverPort = 19611, clientPort = 19612;
        NodeBundle server = createNode(serverPort, 15_000);
        NodeBundle client = createNode(clientPort, 15_000);
        server.node().setEventListener(echoServer(server.node()));

        try {
            server.node().start();
            client.node().start();
            client.node().addPeer(server.fid(), server.pubKey(), "127.0.0.1", serverPort, "server");

            ResponseMessage warm = client.node()
                    .request(server.fid(), "svc", ByteUtils.randomBytes(200))
                    .get(15, TimeUnit.SECONDS);
            assertTrue(warm.isSuccess(), "warmup should succeed");

            int size = 16 * 1024 * 1024;
            byte[] payload = ByteUtils.randomBytes(size);
            long start = System.currentTimeMillis();
            ResponseMessage resp = client.node().requestWithStream(
                            server.fid(), "svc", new byte[0],
                            new ByteArrayInputStream(payload), size, null)
                    .get(120, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - start;
            double mbps = size / 1024.0 / 1024.0 / (elapsedMs / 1000.0);

            System.out.printf("localhost upload: %d bytes in %dms = %.1f MB/s%n", size, elapsedMs, mbps);
            assertTrue(resp != null && resp.isSuccess(), "localhost upload must succeed");
            assertTrue(mbps > 1.0, "localhost throughput regressed to " + mbps + " MB/s");
        } finally {
            client.node().stop();
            server.node().stop();
        }
    }
}
