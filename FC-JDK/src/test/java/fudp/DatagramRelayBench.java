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
import java.net.InetAddress;
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
 *   <li>{@code clients}: against the relay at {@code -Dbench.relayHost}, one of
 *   ({@code -Dbench.part}): {@code all} (default), the sender and the 60
 *   receivers; {@code sender}, which drives the steps and asserts the gate once
 *   60 receivers have joined; or {@code receivers}, which joins, reports what
 *   it received to the relay, and leaves when the sender finishes.</li>
 * </ul>
 * The relay's key is fixed (derived from a constant), so clients know it
 * without an exchange. A bench key, not for anything else.
 *
 * <h2>Measuring the gate across hosts</h2>
 * Run the relay and {@code part=sender} on one host, {@code part=receivers} on
 * another. The sender's datagrams then reach the relay over loopback, so
 * inbound delay is on one clock and holds no internet path — only the wait
 * before the relay's listener runs, which is what the gate means — while the
 * relay's 60 copies of each go out through its real network interface. With
 * the sender remote as well ({@code part=all}), inbound is reported above the
 * step's floor, which cancels the clock offset and base latency but not the
 * path's jitter: Europe to Singapore alone varied by ~6 ms one way at p99.
 * <p>
 * Each step drains before the next: the driver polls the relay's counters
 * until they stop moving, so no step starts behind a backlog left by the one
 * before. Rates are over the span from the step's first to last datagram at
 * the relay, on its clock, so a slow poll does not stretch them. A step the
 * relay cannot keep up with shows as fwd/s below offer/s; with relay CPU well
 * under 100% as well, the relay thread was starved of CPU rather than
 * saturated.
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
                InetAddress addr;
                try {
                    addr = InetAddress.getByName(host);
                } catch (Exception e) {
                    throw new AssertionError("bench.relayHost '" + host + "' does not resolve", e);
                }
                String part = System.getProperty("bench.part", "all");
                switch (part) {
                    case "all" -> runDriver(host, addr.isLoopbackAddress(), true);
                    case "sender" -> runDriver(host, addr.isLoopbackAddress(), false);
                    case "receivers" -> runReceivers(host);
                    default -> fail("bench.part must be all, sender or receivers, not " + part);
                }
            }
            case "local" -> {
                Relay relay = new Relay();
                try {
                    runDriver("127.0.0.1", true, true);
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
     * {@code speak} join, {@code reset} starts a step's samples, {@code stats}
     * reports counters and the step's percentiles, {@code delivered} carries a
     * remote receivers' total (and answers whether the run has finished), and
     * {@code finish} ends the run.
     */
    static final class Relay {
        final NodeBundle bundle;
        final List<Long> receiverConns = new CopyOnWriteArrayList<>();
        final ConcurrentLinkedQueue<Long> inboundUs = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Long> holdUs = new ConcurrentLinkedQueue<>();
        final AtomicLong received = new AtomicLong();
        final AtomicLong forwarded = new AtomicLong();
        final AtomicLong forwardDrops = new AtomicLong();
        final AtomicLong delivered = new AtomicLong();
        /** Relay clock at the step's first and latest datagram; 0 before the first. */
        final AtomicLong firstNs = new AtomicLong(), lastNs = new AtomicLong();
        volatile boolean finished;
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
                        case "begin" -> {
                            receiverConns.clear();
                            delivered.set(0);
                            finished = false;
                        }
                        case "listen" -> {
                            node.enableDatagrams(connectionId);
                            receiverConns.add(connectionId);
                        }
                        case "speak" -> node.enableDatagrams(connectionId);
                        case "reset" -> {
                            inboundUs.clear();
                            holdUs.clear();
                            firstNs.set(0);
                        }
                        case "delivered" -> {
                            delivered.set(Long.parseLong(new String(data, StandardCharsets.UTF_8)));
                            reply = new byte[]{(byte) (finished ? 1 : 0)};
                        }
                        case "finish" -> {
                            // Forget the receivers too, or the next sender sees
                            // these 60 and starts before its own have joined.
                            finished = true;
                            receiverConns.clear();
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
                    firstNs.compareAndSet(0, entry);
                    lastNs.set(entry);
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
            m.put("delivered", delivered.get());
            m.put("spanNs", firstNs.get() == 0 ? 0 : lastNs.get() - firstNs.get());
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

    private static String relayFid() throws Exception {
        return KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(relayPrivKey()));
    }

    private static List<NodeBundle> joinAsReceivers(String relayHost, AtomicLong delivered,
                                                    ConcurrentLinkedQueue<Long> endToEndUs) throws Exception {
        byte[] relayPub = KeyTools.prikeyToPubkey(relayPrivKey());
        String relayFid = relayFid();
        List<NodeBundle> receivers = new ArrayList<>();
        for (int i = 0; i < RECEIVERS; i++) receivers.add(createNode(RELAY_PORT + 2 + i));
        for (NodeBundle r : receivers) {
            r.node().setEventListener(new NodeEventListener() {
                @Override
                public void onDatagram(String peerId, long connectionId, byte[] data) {
                    if (endToEndUs != null) endToEndUs.add(ageMicros(data));
                    delivered.incrementAndGet();
                }
            });
        }
        for (NodeBundle r : receivers) r.node().start();
        for (NodeBundle r : receivers) {
            r.node().addPeer(relayFid, relayPub, relayHost, RELAY_PORT);
            call(r.node(), relayFid, "listen", "");
        }
        return receivers;
    }

    /** {@code part=receivers}: join, report deliveries to the relay, leave when the sender finishes. */
    private static void runReceivers(String relayHost) throws Exception {
        String relayFid = relayFid();
        AtomicLong delivered = new AtomicLong();
        NodeBundle reporter = createNode(RELAY_PORT + 1);
        List<NodeBundle> receivers = new ArrayList<>();
        try {
            reporter.node().start();
            reporter.node().addPeer(relayFid, KeyTools.prikeyToPubkey(relayPrivKey()), relayHost, RELAY_PORT);
            call(reporter.node(), relayFid, "begin", "");
            receivers = joinAsReceivers(relayHost, delivered, null);
            System.out.println("[DatagramRelayBench] " + RECEIVERS + " receivers joined the relay at " + relayHost
                    + "; start the sender (bench.part=sender) on the relay host");
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30);
            while (System.nanoTime() < deadline) {
                Thread.sleep(200);
                byte[] finished = call(reporter.node(), relayFid, "delivered", String.valueOf(delivered.get()));
                if (finished.length > 0 && finished[0] == 1) break;
            }
            System.out.println("[DatagramRelayBench] sender finished; " + delivered.get() + " datagrams received");
        } finally {
            reporter.node().stop();
            for (NodeBundle n : receivers) n.node().stop();
        }
    }

    /**
     * Drive the steps and assert the gate. With {@code withReceivers} the 60
     * receivers are in this JVM; without, it waits for a {@code part=receivers}
     * run to join and takes deliveries from what that run reports to the relay.
     */
    private static void runDriver(String relayHost, boolean sameHost, boolean withReceivers) throws Exception {
        byte[] relayPub = KeyTools.prikeyToPubkey(relayPrivKey());
        String relayFid = relayFid();

        NodeBundle sender = createNode(withReceivers ? RELAY_PORT + 1 : RELAY_PORT + 2 + RECEIVERS);
        // Sender stamp -> receiver listener: one clock, when the receivers are here.
        ConcurrentLinkedQueue<Long> endToEndUs = new ConcurrentLinkedQueue<>();
        AtomicLong localDelivered = new AtomicLong();
        List<NodeBundle> all = new ArrayList<>();
        all.add(sender);
        try {
            FudpNode s = sender.node();
            s.start();
            s.addPeer(relayFid, relayPub, relayHost, RELAY_PORT);
            if (withReceivers) {
                call(s, relayFid, "begin", "");
                all.addAll(joinAsReceivers(relayHost, localDelivered, endToEndUs));
            } else {
                System.out.println("[DatagramRelayBench] waiting for " + RECEIVERS
                        + " receivers (bench.part=receivers) to join the relay...");
                long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
                while (stats(s, relayFid, "").get("receivers") < RECEIVERS) {
                    assertTrue(System.nanoTime() < deadline, "receivers did not join within 10 min");
                    Thread.sleep(500);
                }
            }
            call(s, relayFid, "speak", "");
            long senderConn = s.getProtocol().getConnectionManager().getAnyConnection(relayFid).getConnectionId();
            s.enableDatagrams(senderConn);
            s.setDatagramRate(senderConn, 4_000_000); // capacity runs exceed one voice
            assertEquals(RECEIVERS, stats(s, relayFid, "").get("receivers"), "relay must see every receiver");

            // Warm up the JIT, below what a small relay can take, then drain.
            long seq = run(s, senderConn, 0, 100, 3000);
            drain(s, relayFid);

            System.out.println("[DatagramRelayBench] " + (sameHost ? "sender on the relay's host" : "sender remote")
                    + ", receivers " + (withReceivers ? "with the sender" : "remote")
                    + "; inbound " + (sameHost ? "absolute" : "above its floor (clocks differ)"));
            System.out.println("[DatagramRelayBench] pps-in offer/s  fwd/s  fwd/cpu-s  relayCPU  lost  drops"
                    + "   inbound p50/p99     hold p50/p99    e2e p50/p99/max       relay gc");
            Result realistic = null, capacity = null;
            for (int pps : STEPS) {
                call(s, relayFid, "reset", "");
                endToEndUs.clear();
                Map<String, Long> s0 = stats(s, relayFid, "");
                long del0 = withReceivers ? localDelivered.get() : s0.get("delivered");

                int durationMs = pps == 25 ? 10_000 : 5_000;
                long seq0 = seq;
                seq = run(s, senderConn, seq, pps, durationMs);
                drain(s, relayFid);

                Map<String, Long> s1 = stats(s, relayFid, "pps=" + pps);
                // First to last datagram at the relay, plus one interval for the last.
                double spanS = s1.get("spanNs") / 1e9 + 1.0 / pps;
                double cpuS = (s1.get("cpuNs") - s0.get("cpuNs")) / 1e9;
                long fwd = s1.get("fwd") - s0.get("fwd");
                long sent = seq - seq0;
                long inFloor = sameHost ? 0 : s1.get("inMin");
                long del = (withReceivers ? localDelivered.get() : s1.get("delivered")) - del0;
                Result res = new Result(pps, (double) pps * RECEIVERS, fwd / spanS, fwd / cpuS,
                        Math.min(100, 100 * cpuS / spanS),
                        sent, sent - (s1.get("received") - s0.get("received")),
                        s1.get("drops") - s0.get("drops"), fwd, del,
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
                    + " copy handed to the socket; e2e = sender stamp -> each receiver's listener (-1: receivers"
                    + " remote).");

            // Realistic call: 25 pps to 60 receivers.
            assertEquals(0, realistic.drops, "relay must not drop at 1500 fwd/s");
            assertTrue(realistic.lostToRelay <= realistic.sent / 100,
                    "sender -> relay lost " + realistic.lostToRelay + " of " + realistic.sent + " at 25 pps");
            assertTrue(realistic.delivered >= realistic.fwd * 0.99,
                    "receivers got " + realistic.delivered + " of the " + realistic.fwd + " the relay sent");
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
                call(sender.node(), relayFid, "finish", ""); // remote receivers leave
                if (withReceivers) call(sender.node(), relayFid, "begin", ""); // leave the relay idle
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
     * Wait until the relay has taken in and forwarded everything queued for it,
     * and remote receivers have reported what they got: its counters unchanged
     * over two polls 250 ms apart, at most 60 s.
     */
    private static void drain(FudpNode node, String relayFid) throws Exception {
        List<Long> prev = List.of();
        int still = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (still < 2 && System.nanoTime() < deadline) {
            Thread.sleep(250);
            Map<String, Long> m = stats(node, relayFid, "");
            List<Long> now = List.of(m.get("received"), m.get("fwd"), m.get("delivered"));
            if (now.equals(prev)) {
                still++;
            } else {
                prev = now;
                still = 0;
            }
        }
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
