package fudp;

import core.crypto.KeyTools;
import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import fudp.util.ByteUtils;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduction test for the disk.uploadAll failure pattern on unstable networks:
 * multi-frame requests are fully delivered and processed by the server, but the
 * client never receives the (small, single-frame) response and hits its idle
 * timeout, while subsequent small requests on the same connection succeed.
 *
 * Setup: client -> lossy UDP proxy -> server. The proxy drops packets in each
 * direction with a configurable probability, mimicking an unstable WAN.
 */
public class LossyRequestResponseTest {

    /** Simple lossy UDP forwarder between the client and the server. */
    static class LossyProxy implements Runnable {
        final DatagramSocket socket;
        final InetSocketAddress serverAddr;
        volatile SocketAddress clientAddr;
        volatile double dropClientToServer = 0.0;
        volatile double dropServerToClient = 0.0;
        /** Client-to-server bandwidth cap in packets/second (0 = unlimited).
         *  Excess packets are dropped, mimicking a congested slow uplink. */
        volatile int throttleC2SPps = 0;
        final AtomicInteger droppedC2S = new AtomicInteger();
        final AtomicInteger droppedS2C = new AtomicInteger();
        volatile boolean running = true;
        private long throttleWindowStart = 0;
        private int throttleWindowCount = 0;

        LossyProxy(int listenPort, int serverPort) throws Exception {
            this.socket = new DatagramSocket(listenPort);
            this.serverAddr = new InetSocketAddress("127.0.0.1", serverPort);
        }

        private boolean throttledOut() {
            int pps = throttleC2SPps;
            if (pps <= 0) return false;
            long now = System.currentTimeMillis();
            if (now - throttleWindowStart >= 1000) {
                throttleWindowStart = now;
                throttleWindowCount = 0;
            }
            if (throttleWindowCount >= pps) return true;
            throttleWindowCount++;
            return false;
        }

        @Override
        public void run() {
            byte[] buf = new byte[65535];
            while (running) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    boolean fromServer = p.getSocketAddress().equals(serverAddr);
                    if (fromServer) {
                        if (clientAddr == null) continue;
                        if (ThreadLocalRandom.current().nextDouble() < dropServerToClient) {
                            droppedS2C.incrementAndGet();
                            continue;
                        }
                        socket.send(new DatagramPacket(p.getData(), p.getLength(), clientAddr));
                    } else {
                        clientAddr = p.getSocketAddress();
                        if (ThreadLocalRandom.current().nextDouble() < dropClientToServer
                                || throttledOut()) {
                            droppedC2S.incrementAndGet();
                            continue;
                        }
                        socket.send(new DatagramPacket(p.getData(), p.getLength(), serverAddr));
                    }
                } catch (Exception e) {
                    if (running) e.printStackTrace();
                }
            }
        }

        void stop() {
            running = false;
            socket.close();
        }
    }

    record NodeBundle(FudpNode node, String fid, byte[] pubKey, int port) {}

    private NodeBundle createNode(int port, long requestTimeoutMs) throws Exception {
        byte[] privKey = ByteUtils.randomBytes(32);
        byte[] pubKey = KeyTools.prikeyToPubkey(privKey);
        NodeConfig config = new NodeConfig();
        config.setPort(port);
        config.setMaxPacketSize(1400);
        config.setRequestTimeoutMs(requestTimeoutMs);
        config.setDataDir(System.getProperty("java.io.tmpdir") + "/fudp_lossy_" + port + "_" + System.nanoTime());
        FudpNode node = new FudpNode(privKey, config);
        return new NodeBundle(node, node.getLocalFid(), pubKey, port);
    }

    @Test
    public void testUploadAllPattern_withLoss() throws Exception {
        int serverPort = 19501;
        int clientPort = 19502;
        int proxyPort = 19503;

        NodeBundle server = createNode(serverPort, 10_000);
        NodeBundle client = createNode(clientPort, 10_000);
        LossyProxy proxy = new LossyProxy(proxyPort, serverPort);
        Thread proxyThread = new Thread(proxy, "lossy-proxy");
        proxyThread.setDaemon(true);

        // Server: emulate the FAPI worker — respond ~20ms after receiving each request
        ExecutorService worker = Executors.newSingleThreadExecutor();
        byte[] responseBody = ByteUtils.randomBytes(300); // small JSON-sized response
        server.node().setEventListener(new NodeEventListener() {
            @Override
            public void onRequestReceived(String peerId, long connectionId, long requestId,
                                          String serviceName, byte[] data) {
                worker.submit(() -> {
                    try {
                        Thread.sleep(20);
                        server.node().respond(peerId, connectionId, requestId,
                                ResponseMessage.STATUS_SUCCESS, responseBody);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        int carveFailures = 0;
        int checkFailures = 0;
        int cycles = 25;
        try {
            server.node().start();
            client.node().start();
            proxyThread.start();

            // Client talks to the server THROUGH the proxy
            client.node().addPeer(server.fid(), server.pubKey(), "127.0.0.1", proxyPort, "server");

            // Warmup with no loss
            ResponseMessage warm = client.node()
                    .request(server.fid(), "svc", ByteUtils.randomBytes(200))
                    .get(15, TimeUnit.SECONDS);
            assertTrue(warm.isSuccess(), "warmup should succeed with no loss");

            // Turn on loss: unstable network in both directions
            proxy.dropClientToServer = 0.25;
            proxy.dropServerToClient = 0.25;

            for (int i = 0; i < cycles; i++) {
                // "disk.check": small single-frame request
                if (!doRequest(client.node(), server.fid(), 200, 20)) {
                    checkFailures++;
                    System.out.println("CYCLE " + i + ": CHECK FAILED");
                }
                // "disk.carve": multi-frame request (~8.5KB like the failed files)
                if (!doRequest(client.node(), server.fid(), 8500, 20)) {
                    carveFailures++;
                    System.out.println("CYCLE " + i + ": CARVE FAILED");
                }
            }

            System.out.println("=== RESULT ===");
            System.out.println("check failures: " + checkFailures + "/" + cycles);
            System.out.println("carve failures: " + carveFailures + "/" + cycles);
            System.out.println("proxy dropped c2s=" + proxy.droppedC2S + " s2c=" + proxy.droppedS2C);

            assertTrue(carveFailures == 0 && checkFailures == 0,
                    "no request should fail: loss recovery must retransmit both request and response"
                            + " (check=" + checkFailures + ", carve=" + carveFailures + "/" + cycles + ")");
        } finally {
            proxy.stop();
            worker.shutdownNow();
            client.node().stop();
            server.node().stop();
        }
    }

    /**
     * A large upload on a slow uplink must not idle-time-out while it is still
     * progressing: the transfer takes far longer than the request idle budget,
     * but the server's ACKs are continuous proof of progress and must keep the
     * pending request alive. (Field failure: >30s disk.carve uploads always hit
     * "idle timeout without response data" because only inbound stream data
     * refreshed the deadline and an upload produces none until it completes.)
     */
    @Test
    public void testSlowLargeUpload_survivesIdleTimeout() throws Exception {
        int serverPort = 19511;
        int clientPort = 19512;
        int proxyPort = 19513;

        // Idle budget = 2s base + 1s/100KB size allowance = ~12s for 1MB.
        NodeBundle server = createNode(serverPort, 10_000);
        NodeBundle client = createNode(clientPort, 2_000);
        LossyProxy proxy = new LossyProxy(proxyPort, serverPort);
        Thread proxyThread = new Thread(proxy, "lossy-proxy-slow");
        proxyThread.setDaemon(true);

        ExecutorService worker = Executors.newSingleThreadExecutor();
        byte[] responseBody = ByteUtils.randomBytes(300);
        server.node().setEventListener(new NodeEventListener() {
            @Override
            public void onRequestReceived(String peerId, long connectionId, long requestId,
                                          String serviceName, byte[] data) {
                worker.submit(() -> {
                    try {
                        Thread.sleep(20);
                        server.node().respond(peerId, connectionId, requestId,
                                ResponseMessage.STATUS_SUCCESS, responseBody);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        try {
            server.node().start();
            client.node().start();
            proxyThread.start();
            client.node().addPeer(server.fid(), server.pubKey(), "127.0.0.1", proxyPort, "server");

            // Warmup unthrottled
            ResponseMessage warm = client.node()
                    .request(server.fid(), "svc", ByteUtils.randomBytes(200))
                    .get(15, TimeUnit.SECONDS);
            assertTrue(warm.isSuccess(), "warmup should succeed");

            // Cap the uplink so the 1MB transfer takes well beyond the ~12s idle budget
            proxy.throttleC2SPps = 40;

            long start = System.currentTimeMillis();
            CompletableFuture<ResponseMessage> f =
                    client.node().request(server.fid(), "svc", ByteUtils.randomBytes(1024 * 1024));
            ResponseMessage resp = f.get(180, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("slow upload finished in " + elapsed + "ms (dropped c2s=" + proxy.droppedC2S + ")");
            assertTrue(resp != null && resp.isSuccess(), "slow upload must succeed, not idle-time-out");
            assertTrue(elapsed > 13_000,
                    "transfer should have outlived the ~12s idle budget for the scenario to be meaningful, took " + elapsed + "ms");
        } finally {
            proxy.stop();
            worker.shutdownNow();
            client.node().stop();
            server.node().stop();
        }
    }

    private boolean doRequest(FudpNode clientNode, String serverFid, int size, int timeoutSec) {
        try {
            CompletableFuture<ResponseMessage> f =
                    clientNode.request(serverFid, "svc", ByteUtils.randomBytes(size));
            ResponseMessage resp = f.get(timeoutSec, TimeUnit.SECONDS);
            return resp != null && resp.isSuccess();
        } catch (Exception e) {
            System.out.println("request failed: " + e);
            return false;
        }
    }
}
