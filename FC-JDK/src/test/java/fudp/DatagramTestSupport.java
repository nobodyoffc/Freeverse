package fudp;

import core.crypto.KeyTools;
import fudp.connection.PeerConnection;
import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import fudp.util.ByteUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared helpers for the DATAGRAM (FUDP7) tests. */
final class DatagramTestSupport {

    private DatagramTestSupport() {}

    record NodeBundle(FudpNode node, String fid, byte[] pubKey, int port) {}

    static NodeBundle createNode(int port) throws Exception {
        return createNode(port, new NodeConfig());
    }

    static NodeBundle createNode(int port, NodeConfig config) throws Exception {
        byte[] privKey = ByteUtils.randomBytes(32);
        byte[] pubKey = KeyTools.prikeyToPubkey(privKey);
        config.setPort(port);
        config.setMaxPacketSize(1400);
        config.setDataDir(System.getProperty("java.io.tmpdir") + "/fudp_dgram_" + port + "_" + System.nanoTime());
        FudpNode node = new FudpNode(privKey, config);
        return new NodeBundle(node, node.getLocalFid(), pubKey, port);
    }

    /** A datagram as the listener saw it. */
    interface DatagramSink {
        void onDatagram(String peerId, long connectionId, byte[] data);
    }

    /**
     * Listener that answers every request with a small success response (off
     * the receive thread, like a real service) and hands datagrams to {@code sink}.
     */
    static NodeEventListener respondingListener(FudpNode node, DatagramSink sink) {
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "dgram-test-responder");
            t.setDaemon(true);
            return t;
        });
        return new NodeEventListener() {
            @Override
            public void onRequestReceived(String peerId, long connectionId, long requestId,
                                          String serviceName, byte[] data) {
                worker.submit(() -> {
                    try {
                        node.respond(peerId, connectionId, requestId, ResponseMessage.STATUS_SUCCESS,
                                ByteBuffer.allocate(4).putInt(data.length).array());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onDatagram(String peerId, long connectionId, byte[] data) {
                if (sink != null) sink.onDatagram(peerId, connectionId, data);
            }
        };
    }

    /** Make {@code from} know {@code to}, reachable at {@code port} (itself or a proxy). */
    static void introduce(NodeBundle from, NodeBundle to, int port) {
        from.node().addPeer(to.fid(), to.pubKey(), "127.0.0.1", port);
    }

    /**
     * Establish a connection from {@code client} to {@code server} with one
     * request, then enable datagrams on both ends of it.
     *
     * @return {clientConnectionId, serverConnectionId}
     */
    static long[] connectWithDatagrams(NodeBundle client, NodeBundle server) throws Exception {
        var resp = client.node().request(server.fid(), "hello", new byte[8]).get(15, TimeUnit.SECONDS);
        if (!resp.isSuccess()) throw new IllegalStateException("warmup request failed");
        PeerConnection c = client.node().getProtocol().getConnectionManager().getAnyConnection(server.fid());
        PeerConnection s = server.node().getProtocol().getConnectionManager().getAnyConnection(client.fid());
        client.node().enableDatagrams(c.getConnectionId());
        server.node().enableDatagrams(s.getConnectionId());
        return new long[]{c.getConnectionId(), s.getConnectionId()};
    }

    /** A datagram carrying a sequence number and its send time, padded to {@code size}. */
    static byte[] stamped(long seq, int size) {
        byte[] b = new byte[Math.max(16, size)];
        ByteBuffer.wrap(b).putLong(seq).putLong(System.nanoTime());
        return b;
    }

    static long seqOf(byte[] b) {
        return ByteBuffer.wrap(b).getLong(0);
    }

    /** Microseconds since the datagram was stamped (same JVM, so one clock). */
    static long ageMicros(byte[] b) {
        return (System.nanoTime() - ByteBuffer.wrap(b).getLong(8)) / 1000;
    }

    static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) return -1;
        int i = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, i))];
    }

    static long[] sortedCopy(java.util.Collection<Long> values) {
        long[] a = values.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(a);
        return a;
    }

    /**
     * UDP forwarder between one client and one server that drops packets with
     * a fixed probability in each direction.
     */
    static class LossyProxy implements Runnable {
        final DatagramSocket socket;
        final InetSocketAddress serverAddr;
        volatile SocketAddress clientAddr;
        volatile double dropRate;
        final AtomicInteger forwarded = new AtomicInteger();
        final AtomicInteger dropped = new AtomicInteger();
        private volatile boolean running = true;

        LossyProxy(int listenPort, int serverPort) throws Exception {
            this.socket = new DatagramSocket(listenPort);
            this.socket.setReceiveBufferSize(4 * 1024 * 1024);
            this.socket.setSendBufferSize(4 * 1024 * 1024);
            this.serverAddr = new InetSocketAddress("127.0.0.1", serverPort);
        }

        Thread start() {
            Thread t = new Thread(this, "dgram-lossy-proxy");
            t.setDaemon(true);
            t.start();
            return t;
        }

        @Override
        public void run() {
            byte[] buf = new byte[65535];
            while (running) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    boolean fromServer = p.getSocketAddress().equals(serverAddr);
                    SocketAddress to;
                    if (fromServer) {
                        to = clientAddr;
                        if (to == null) continue;
                    } else {
                        clientAddr = p.getSocketAddress();
                        to = serverAddr;
                    }
                    if (ThreadLocalRandom.current().nextDouble() < dropRate) {
                        dropped.incrementAndGet();
                        continue;
                    }
                    socket.send(new DatagramPacket(p.getData(), p.getLength(), to));
                    forwarded.incrementAndGet();
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
}
