package fudp;

import core.crypto.KeyTools;
import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import fudp.transport.DatagramResult;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static fudp.DatagramTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Relay benchmark (VOICE_SPEC §14 Phase 1): one sender, 60 receivers, the
 * relay forwarding every datagram to every receiver inline on its receive
 * thread, as the CALL component will.
 * <p>
 * Not picked up by a plain {@code mvn test} (no *Test suffix). Run with
 * {@code mvn -f FC-JDK/pom.xml test -Dtest=DatagramRelayBench}.
 * <p>
 * Gate: at least 20 000 datagrams/s forwarded per core, p99 added delay under
 * 5 ms. "Per core" is forwarded datagrams per second of CPU time on the
 * relay's receive thread, which does all the forwarding. "Added delay" is the
 * wait before the relay's listener runs (inbound) plus its fan-out to the last
 * receiver (hold). Throughput is always asserted; the delay gate only with
 * {@code -Dbench.latencyGate=true}.
 *
 * <h2>Roles ({@code -Dbench.role})</h2>
 * <ul>
 *   <li>{@code local} (default): relay, sender and receivers in this JVM, over
 *   loopback. On a small machine this measures the machine: the 61 client
 *   nodes compete with the relay thread for CPU, and on Linux the loopback
 *   receive work of each {@code sendto} is charged to the relay thread
 *   (without CONFIG_IRQ_TIME_ACCOUNTING). A 4-vCPU VPS starved the relay to
 *   ~40% of a core and put 11–25k fwd/cpu-s on it, varying run to run.</li>
 *   <li>{@code relay}: only the relay, on UDP {@value #RELAY_PORT}, which must be
 *   reachable. It serves client runs until killed, or for
 *   {@code -Dbench.relayMinutes} (default 60), and prints each step it sees.</li>
 *   <li>{@code clients}: the sender and receivers, against the relay at
 *   {@code -Dbench.relayHost}. The gate is asserted here, from counters the
 *   relay reports for each step.</li>
 * </ul>
 * The relay's key is fixed (derived from a constant), so clients know it
 * without an exchange. A bench key, not for anything else.
 * <p>
 * Across two hosts the clocks differ, so inbound delay is reported above its
 * floor: each datagram's (relay clock − sender stamp) minus the step's
 * minimum. The clock offset and the network's base latency cancel; queueing
 * before the relay's listener, and network jitter, remain. On one host the
 * clock is shared and inbound is absolute (sender stamp to relay listener).
 * <p>
 * Each step drains before the next: the clients poll the relay's counters
 * until they stop moving, so no step starts behind a backlog left by the one
 * before. A step the relay cannot keep up with shows as fwd/s below offer/s;
 * with relay CPU well under 100% as well, the relay thread was starved of CPU
 * rather than saturated.
 */
public class DatagramRelayBench {

    private static final int RECEIVERS = 60;
    private static final int FRAME_BYTES = 120;
    private static final int RELAY_PORT = 19800;
    private static final int[] STEPS = {25, 100, 200, 350, 500};

    @Test
    public void relayFansOutToSixtyReceivers() throws Exception {
        String role = System.getProperty("bench.role", "local");
        switch (role) {
            case "relay" -> serveRelay();
            case "clients" -> {
                String host = System.getProperty("bench.relayHost");
                assertNotNull(host, "bench.role=clients needs -Dbench.relayHost=<relay address>");
                runClients(host, false);
            }
            case "local" -> {
                Relay relay = new Relay();
                try {
                    runClients("127.0.0.1", true);
                } finally {
                    relay.stop();
                }
            }
            default -> fail("bench.role must be local, relay or clients, not " + role);
        }
    }

    // ===== Relay =====

    private static byte[] relayPrivKey() throws Exception {
        return MessageDigest.getInstance("SHA-256")
                .digest("FUDP DatagramRelayBench relay key".getBytes(StandardCharsets.UTF_8));
    }

    private static void serveRelay() throws Exception {
        long minutes = Long.getLong("bench.relayMinutes", 60);
        Relay relay = new Relay();
        System.out.println("[DatagramRelayBench] relay on UDP " + RELAY_PORT + " as " + relay.bundle.fid()
                + "; serving for " + minutes + " min (Ctrl-C to stop sooner)");
        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(minutes));
        } finally {
            relay.stop();
        }
    }

    /**
     * The forwarding relay, plus a few requests the clients drive it with:
     * {@code begin} forgets receivers from an earlier run, {@code listen} and
     * {@code speak} join, {@code reset} starts a step's samples, and
     * {@code stats} reports counters and the step's percentiles.
     */
    static final class Relay {
        final NodeBundle bundle;
        final List<Long> receiverConns = new CopyOnWriteArrayList<>();
        final ConcurrentLinkedQueue<Long> inboundUs = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Long> holdUs = new ConcurrentLinkedQueue<>();
        final AtomicLong received = new AtomicLong();
        final AtomicLong forwarded = new AtomicLong();
        final AtomicLong forwardDrops = new AtomicLong();
        final Thread thread;
        final ThreadMXBean mx = ManagementFactory.getThreadMXBean();

        Relay() throws Exception {
            NodeConfig config = new NodeConfig();
            config.setDatagramRateBps(1_000_000); // the relay's own sending side (§2.2)
            bundle = createNode(RELAY_PORT, config, relayPrivKey());
            FudpNode node = bundle.node();
            node.setEventListener(new NodeEventListener() {
                @Override
                public void onRequestReceived(String peerId, long connectionId, long requestId,
                                              String serviceName, byte[] data) {
                    byte[] reply = new byte[1];
                    switch (serviceName) {
                        case "begin" -> receiverConns.clear();
                        case "listen" -> {
                            node.enableDatagrams(connectionId);
                            receiverConns.add(connectionId);
                        }
                        case "speak" -> node.enableDatagrams(connectionId);
                        case "reset" -> {
                            inboundUs.clear();
                            holdUs.clear();
                        }
                        case "stats" -> reply = stats(new String(data, StandardCharsets.UTF_8))
                                .getBytes(StandardCharsets.UTF_8);
                        default -> { }
                    }
                    try {
                        node.respond(peerId, connectionId, requestId, ResponseMessage.STATUS_SUCCESS, reply);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onDatagram(String peerId, long connectionId, byte[] data) {
                    long entry = System.nanoTime();
                    received.incrementAndGet();
                    // Relay clock minus the sender's stamp: absolute on one
                    // host, offset by the clock difference across two.
                    inboundUs.add(ageMicros(data));
                    for (long conn : receiverConns) {
                        if (node.sendDatagram(conn, data) == DatagramResult.SENT) forwarded.incrementAndGet();
                        else forwardDrops.incrementAndGet();
                    }
                    // Time until the LAST copy was handed to the socket: the most any
                    // receiver waited on the relay's own work for this datagram.
                    holdUs.add((System.nanoTime() - entry) / 1000);
                }
            });
            node.start();
            thread = findThread("fudp-recv-" + RELAY_PORT);
            assertNotNull(thread, "relay receive thread");
        }

        /** Counters, and percentiles of the samples since {@code reset}. {@code label} is logged if not empty. */
        String stats(String label) {
            long[] in = sortedCopy(inboundUs), hold = sortedCopy(holdUs);
            long[] gc = gcTotals();
            Map<String, Long> m = new java.util.LinkedHashMap<>();
            m.put("received", received.get());
            m.put("fwd", forwarded.get());
            m.put("drops", forwardDrops.get());
            m.put("cpuNs", mx.getThreadCpuTime(thread.getId()));
            m.put("receivers", (long) receiverConns.size());
            m.put("inMin", in.length == 0 ? -1 : in[0]);
            m.put("inP50", percentile(in, 50));
            m.put("inP99", percentile(in, 99));
            m.put("holdP50", percentile(hold, 50));
            m.put("holdP99", percentile(hold, 99));
            m.put("gcCount", gc[0]);
            m.put("gcMs", gc[1]);
            StringBuilder sb = new StringBuilder();
            m.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
            if (!label.isEmpty()) System.out.println("[DatagramRelayBench] relay " + label + ": " + sb);
            return sb.toString();
        }

        void stop() {
            bundle.node().stop();
        }
    }

    // ===== Clients =====

    record Result(int pps, double offered, double fwdPerSec, double fwdPerCpuSec, double cpuPct,
                  long sent, long lostToRelay, long drops, long fwd, long delivered,
                  long inboundP50, long inboundP99, long holdP50, long holdP99) {}

    private static void runClients(String relayHost, boolean sameHost) throws Exception {
        byte[] relayPub = KeyTools.prikeyToPubkey(relayPrivKey());
        String relayFid = KeyTools.pubkeyToFchAddr(relayPub);

        NodeBundle sender = createNode(RELAY_PORT + 1);
        List<NodeBundle> receivers = new ArrayList<>();
        for (int i = 0; i < RECEIVERS; i++) receivers.add(createNode(RELAY_PORT + 2 + i));

        // Sender stamp -> receiver listener: one host's clock in both modes.
        ConcurrentLinkedQueue<Long> endToEndUs = new ConcurrentLinkedQueue<>();
        AtomicLong delivered = new AtomicLong();
        for (NodeBundle r : receivers) {
            r.node().setEventListener(new NodeEventListener() {
                @Override
                public void onDatagram(String peerId, long connectionId, byte[] data) {
                    endToEndUs.add(ageMicros(data));
                    delivered.incrementAndGet();
                }
            });
        }

        List<NodeBundle> all = new ArrayList<>(receivers);
        all.add(sender);
        try {
            for (NodeBundle n : all) n.node().start();
            FudpNode s = sender.node();
            s.addPeer(relayFid, relayPub, relayHost, RELAY_PORT);
            call(s, relayFid, "begin", "");
            for (NodeBundle r : receivers) {
                r.node().addPeer(relayFid, relayPub, relayHost, RELAY_PORT);
                call(r.node(), relayFid, "listen", "");
            }
            call(s, relayFid, "speak", "");
            long senderConn = s.getProtocol().getConnectionManager().getAnyConnection(relayFid).getConnectionId();
            s.enableDatagrams(senderConn);
            s.setDatagramRate(senderConn, 4_000_000); // capacity runs exceed one voice
            assertEquals(RECEIVERS, stats(s, relayFid, "").get("receivers"), "relay must see every receiver");

            // Warm up the JIT, below what a small relay can take, then drain.
            long seq = run(s, senderConn, 0, 100, 3000);
            drain(s, relayFid);

            System.out.println("[DatagramRelayBench] " + (sameHost ? "one host" : "relay at " + relayHost)
                    + "; inbound " + (sameHost ? "absolute" : "above its floor (clocks differ)"));
            System.out.println("[DatagramRelayBench] pps-in offer/s  fwd/s  fwd/cpu-s  relayCPU  lost  drops"
                    + "   inbound p50/p99     hold p50/p99    e2e p50/p99/max       relay gc");
            Result realistic = null, capacity = null;
            for (int pps : STEPS) {
                call(s, relayFid, "reset", "");
                endToEndUs.clear();
                Map<String, Long> s0 = stats(s, relayFid, "");
                long del0 = delivered.get();
                long wall0 = System.nanoTime();

                int durationMs = pps == 25 ? 10_000 : 5_000;
                long seq0 = seq;
                seq = run(s, senderConn, seq, pps, durationMs);
                long lastMove = drain(s, relayFid);

                Map<String, Long> s1 = stats(s, relayFid, "pps=" + pps);
                double wallS = (lastMove - wall0) / 1e9;
                double cpuS = (s1.get("cpuNs") - s0.get("cpuNs")) / 1e9;
                long fwd = s1.get("fwd") - s0.get("fwd");
                long sent = seq - seq0;
                long inFloor = sameHost ? 0 : s1.get("inMin");
                Result res = new Result(pps, (double) pps * RECEIVERS, fwd / wallS, fwd / cpuS, 100 * cpuS / wallS,
                        sent, sent - (s1.get("received") - s0.get("received")),
                        s1.get("drops") - s0.get("drops"), fwd, delivered.get() - del0,
                        s1.get("inP50") - inFloor, s1.get("inP99") - inFloor,
                        s1.get("holdP50"), s1.get("holdP99"));
                long[] e2e = sortedCopy(endToEndUs);
                System.out.printf("[DatagramRelayBench] %6d  %6.0f  %5.0f  %9.0f  %7.0f%%  %4d  %5d  %7dus/%7dus"
                                + "  %6dus/%6dus  %6dus/%6dus/%6dus  %d/%dms%n",
                        pps, res.offered, res.fwdPerSec, res.fwdPerCpuSec, res.cpuPct, res.lostToRelay, res.drops,
                        res.inboundP50, res.inboundP99, res.holdP50, res.holdP99,
                        percentile(e2e, 50), percentile(e2e, 99), percentile(e2e, 100),
                        s1.get("gcCount") - s0.get("gcCount"), s1.get("gcMs") - s0.get("gcMs"));
                if (res.fwdPerSec < res.offered * 0.95 && res.cpuPct < 90) {
                    System.out.printf("[DatagramRelayBench]   ^ relay kept up with %.0f%% of the offer on %.0f%% of"
                            + " a core: starved of CPU, not saturated%n", 100 * res.fwdPerSec / res.offered, res.cpuPct);
                }
                if (pps == 25) realistic = res;
                if (pps == 350) capacity = res;
            }
            System.out.println("[DatagramRelayBench] lost = sent but never reached the relay's listener; drops = refused"
                    + " by the relay's send; inbound = sender stamp -> relay listener; hold = relay listener -> last"
                    + " copy handed to the socket; e2e = sender stamp -> each receiver's listener.");

            // Realistic call: 25 pps to 60 receivers.
            assertEquals(0, realistic.drops, "relay must not drop at 1500 fwd/s");
            assertTrue(realistic.lostToRelay <= realistic.sent / 100,
                    "sender -> relay lost " + realistic.lostToRelay + " of " + realistic.sent + " at 25 pps");
            assertTrue(realistic.delivered >= realistic.fwd * 0.99, "receivers must get what the relay sent");
            // Gate, throughput: >= 20k forwards per CPU-second of the relay thread at ~21k fwd/s offered.
            assertTrue(capacity.fwdPerCpuSec >= 20_000,
                    "relay forwards " + (long) capacity.fwdPerCpuSec + " datagrams per CPU-second (gate 20 000)");
            // Gate, delay: what the relay adds — the wait before its listener runs
            // plus its own fan-out — stays under 5 ms at p99.
            if (Boolean.getBoolean("bench.latencyGate")) {
                assertTrue(realistic.inboundP99 + realistic.holdP99 < 5_000, "relay inbound+hold p99 at 25 pps: "
                        + realistic.inboundP99 + "+" + realistic.holdP99 + "us");
                assertTrue(capacity.inboundP99 + capacity.holdP99 < 5_000, "relay inbound+hold p99 at 21k fwd/s: "
                        + capacity.inboundP99 + "+" + capacity.holdP99 + "us");
            } else {
                System.out.println("[DatagramRelayBench] delay gate not asserted (run with -Dbench.latencyGate=true)");
            }
        } finally {
            try {
                call(sender.node(), relayFid, "begin", ""); // leave a remote relay idle
            } catch (Exception ignored) {
            }
            for (NodeBundle n : all) n.node().stop();
        }
    }

    private static byte[] call(FudpNode node, String relayFid, String service, String data) throws Exception {
        ResponseMessage resp = node.request(relayFid, service, data.getBytes(StandardCharsets.UTF_8))
                .get(15, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess(), service + " failed: " + resp.getStatusCode());
        return resp.getData();
    }

    private static Map<String, Long> stats(FudpNode node, String relayFid, String label) throws Exception {
        Map<String, Long> m = new HashMap<>();
        for (String kv : new String(call(node, relayFid, "stats", label), StandardCharsets.UTF_8).split(";")) {
            if (kv.isEmpty()) continue;
            int eq = kv.indexOf('=');
            m.put(kv.substring(0, eq), Long.parseLong(kv.substring(eq + 1)));
        }
        return m;
    }

    /**
     * Wait until the relay has taken in and forwarded everything queued for it:
     * its counters unchanged over two polls 100 ms apart, at most 60 s.
     *
     * @return when the counters last moved (System.nanoTime)
     */
    private static long drain(FudpNode node, String relayFid) throws Exception {
        long lastMove = System.nanoTime();
        long prev = -1;
        int still = 0;
        long deadline = lastMove + TimeUnit.SECONDS.toNanos(60);
        while (still < 2 && System.nanoTime() < deadline) {
            Thread.sleep(100);
            Map<String, Long> m = stats(node, relayFid, "");
            long now = m.get("received") * 1_000_003L + m.get("fwd");
            if (now != prev) {
                prev = now;
                lastMove = System.nanoTime();
                still = 0;
            } else {
                still++;
            }
        }
        return lastMove;
    }

    /** Send {@code pps} stamped frames per second for {@code durationMs}. @return next seq */
    private static long run(FudpNode sender, long conn, long seq, int pps, int durationMs) {
        long intervalNanos = 1_000_000_000L / pps;
        long start = System.nanoTime();
        long next = start;
        while (System.nanoTime() - start < durationMs * 1_000_000L) {
            DatagramResult r = sender.sendDatagram(conn, stamped(seq++, FRAME_BYTES));
            if (r != DatagramResult.SENT) throw new IllegalStateException("sender dropped: " + r);
            next += intervalNanos;
            long wait = next - System.nanoTime();
            if (wait > 0) LockSupport.parkNanos(wait);
        }
        return seq;
    }

    /** {collections, total collection ms} over all collectors. */
    private static long[] gcTotals() {
        long n = 0, ms = 0;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            n += Math.max(0, gc.getCollectionCount());
            ms += Math.max(0, gc.getCollectionTime());
        }
        return new long[]{n, ms};
    }

    private static Thread findThread(String name) {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals(name)) return t;
        }
        return null;
    }
}
