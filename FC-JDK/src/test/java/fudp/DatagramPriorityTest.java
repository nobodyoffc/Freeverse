package fudp;

import fudp.connection.PeerConnection;
import fudp.message.ResponseMessage;
import fudp.transport.DatagramBudget;
import fudp.transport.DatagramResult;
import fudp.util.ByteUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static fudp.DatagramTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Audio against a bulk stream upload on the same connection.
 * <p>
 * What FUDP guarantees is sender-side: a datagram skips the congestion
 * window, the pacer and any wait for the socket buffer, so it never queues
 * behind stream data inside the sender. What it cannot guarantee on its own
 * is the rest of the path. Loss-based congestion control fills whatever queue
 * sits downstream — here the receiver's socket buffer, on a real network a
 * bottleneck router — and audio waits in that queue behind the upload.
 * {@link #senderNeverWaitsBehindUpload} measures that and prints it.
 * <p>
 * The mitigation is for the call layer to cap bulk transfers below the path
 * rate while a call is live ({@code FudpNode.setStreamRateCap});
 * {@link #streamRateCapKeepsAudioFlat} shows it keeps audio delay flat.
 * A transport-level fix (a delay-based limit on streams while datagrams flow)
 * is tracked separately.
 */
public class DatagramPriorityTest {

    private static final int FRAME_MS = 20; // one Opus frame
    private static final int FRAME_BYTES = 120;

    private NodeBundle server;
    private NodeBundle client;
    private final List<Long> delaysUs = new CopyOnWriteArrayList<>();
    private long connId;

    @BeforeEach
    void setUp() throws Exception {
        server = createNode(19731);
        client = createNode(19732);
        server.node().setEventListener(respondingListener(server.node(),
                (peer, conn, data) -> delaysUs.add(ageMicros(data))));
        client.node().setEventListener(respondingListener(client.node(), null));
        server.node().start();
        client.node().start();
        introduce(client, server, server.port());
        connId = connectWithDatagrams(client, server)[0];

        // Warm up the JIT so the idle baseline is steady state, not interpreted code.
        client.node().setDatagramRate(connId, 100_000_000);
        for (int i = 0; i < 3000; i++) client.node().sendDatagram(connId, stamped(-1, FRAME_BYTES));
        client.node().setDatagramRate(connId, DatagramBudget.DEFAULT_RATE_BPS);
        Thread.sleep(500);
        delaysUs.clear();
    }

    @AfterEach
    void tearDown() {
        client.node().stop();
        server.node().stop();
    }

    /**
     * Uncapped upload: every datagram is sent at once, without waiting, and
     * none is dropped. The delay it then meets downstream is printed, not
     * asserted — it is the queue the upload's congestion control builds.
     */
    @Test
    public void senderNeverWaitsBehindUpload() throws Exception {
        Audio idle = sendAudio(3000, null);
        long[] idleDelay = drainDelays();

        byte[] upload = ByteUtils.randomBytes(32 * 1024 * 1024);
        CompletableFuture<ResponseMessage> transfer = uploadAsync(upload);
        Thread.sleep(200); // let the upload ramp up
        delaysUs.clear();
        Audio busy = sendAudio(30_000, transfer);
        assertUploadComplete(transfer, upload);
        Thread.sleep(1000); // let queued audio drain before counting
        long[] busyDelay = drainDelays();

        PeerConnection conn = client.node().getProtocol().getConnectionManager().getByConnectionId(connId);
        report("idle", idle, idleDelay);
        report("uncapped upload", busy, busyDelay);
        System.out.println("[DatagramPriorityTest] upload connection: sRTT=" + conn.getRttEstimator().getSmoothedRtt()
                + "ms minRtt=" + conn.getRttEstimator().getMinRtt() + "ms retransmits=" + conn.getRetransmitCount()
                + " -- the downstream queue the upload built");

        assertTrue(busy.sent >= 50, "the upload must last long enough to measure (sent " + busy.sent + ")");
        assertEquals(0, busy.notSent, "no datagram may be dropped or refused at the sender during the upload");
        // A datagram that waited behind stream data (congestion window, pacer,
        // socket-buffer backpressure) would show as a systematic delay in the
        // median, or as tens of ms. Isolated slow calls are the thread being
        // descheduled on a loaded machine: stack samples of them land on
        // trivial code with no hotspot and no monitor blocking.
        long callP50 = percentile(busy.callUs, 50);
        long callP99 = percentile(busy.callUs, 99);
        assertTrue(callP50 <= Math.max(percentile(idle.callUs, 50), 100) + 200,
                "sendDatagram p50 during the upload (" + callP50 + "us) vs idle (" + percentile(idle.callUs, 50)
                        + "us): the sender must not make audio wait");
        assertTrue(callP99 < 10_000, "sendDatagram p99 during the upload (" + callP99
                + "us) must stay under half a 20 ms frame");
        assertTrue(busyDelay.length >= busy.sent * 0.98,
                "datagrams must not be lost behind the upload on loopback (" + busyDelay.length + "/" + busy.sent + ")");
    }

    /**
     * Upload capped at 8 Mbit/s, below the ~25 Mbit/s this loopback receiver
     * drains: no queue builds, and audio delay stays within 5 ms of idle.
     */
    @Test
    public void streamRateCapKeepsAudioFlat() throws Exception {
        Audio idle = sendAudio(3000, null);
        long[] idleDelay = drainDelays();

        long capBps = 8_000_000;
        assertTrue(client.node().setStreamRateCap(connId, capBps));
        byte[] upload = ByteUtils.randomBytes(6 * 1024 * 1024);
        long t0 = System.nanoTime();
        CompletableFuture<ResponseMessage> transfer = uploadAsync(upload);
        Thread.sleep(200);
        delaysUs.clear();
        Audio busy = sendAudio(30_000, transfer);
        assertUploadComplete(transfer, upload);
        double uploadS = (System.nanoTime() - t0) / 1e9;
        long[] busyDelay = drainDelays();

        report("idle", idle, idleDelay);
        report("capped upload", busy, busyDelay);
        double mbps = upload.length * 8 / uploadS / 1e6;
        System.out.printf("[DatagramPriorityTest] capped upload: %.1f Mbit/s (cap %.1f)%n", mbps, capBps / 1e6);

        assertTrue(mbps <= capBps / 1e6 * 1.15, "the cap must hold the upload near " + capBps / 1e6
                + " Mbit/s, measured " + mbps);
        assertTrue(busy.sent >= 50, "the upload must last long enough to measure (sent " + busy.sent + ")");
        assertEquals(0, busy.notSent);
        assertTrue(busyDelay.length >= busy.sent * 0.99,
                "loopback loses nothing below the cap (" + busyDelay.length + "/" + busy.sent + ")");
        long p99Busy = percentile(busyDelay, 99);
        long p99Idle = percentile(idleDelay, 99);
        assertTrue(p99Busy <= p99Idle + 5_000,
                "p99 audio delay under the capped upload (" + p99Busy + "us) must stay within 5 ms of idle ("
                        + p99Idle + "us)");
    }

    // ----- helpers -----

    /** Frames sent, frames refused/dropped at the sender, and sendDatagram call times (sorted, us). */
    record Audio(int sent, int notSent, long[] callUs, long blockedCount, long blockedMs, long slowCallCpuUs) {}

    /** Send one frame every FRAME_MS for up to {@code maxMs}, or until {@code until} completes. */
    private Audio sendAudio(long maxMs, CompletableFuture<?> until) {
        List<Long> calls = new ArrayList<>();
        java.lang.management.ThreadMXBean mx = java.lang.management.ManagementFactory.getThreadMXBean();
        if (mx.isThreadContentionMonitoringSupported()) mx.setThreadContentionMonitoringEnabled(true);
        long tid = Thread.currentThread().getId();
        var info0 = mx.getThreadInfo(tid);
        long slowCallCpuNs = 0;
        int sent = 0, notSent = 0;
        long seq = 0;
        long start = System.nanoTime();
        long next = start;
        while ((System.nanoTime() - start) / 1_000_000 < maxMs && (until == null || !until.isDone())) {
            long cpu = mx.getCurrentThreadCpuTime();
            long t = System.nanoTime();
            DatagramResult r = client.node().sendDatagram(connId, stamped(seq++, FRAME_BYTES));
            long wallUs = (System.nanoTime() - t) / 1000;
            calls.add(wallUs);
            // For slow calls, how much was actual work: little CPU means the
            // thread was waiting (on a lock, or for a core).
            if (wallUs > 2_000) slowCallCpuNs = Math.max(slowCallCpuNs, mx.getCurrentThreadCpuTime() - cpu);
            if (r == DatagramResult.SENT) sent++;
            else notSent++;
            next += FRAME_MS * 1_000_000L;
            long wait = next - System.nanoTime();
            if (wait > 0) LockSupport.parkNanos(wait);
        }
        var info1 = mx.getThreadInfo(tid);
        return new Audio(sent, notSent, sortedCopy(calls), info1.getBlockedCount() - info0.getBlockedCount(),
                info1.getBlockedTime() - info0.getBlockedTime(), slowCallCpuNs / 1000);
    }

    private long[] drainDelays() throws InterruptedException {
        Thread.sleep(100);
        long[] d = sortedCopy(delaysUs);
        delaysUs.clear();
        return d;
    }

    /** request() sends the whole body before returning, so run it on its own thread. */
    private CompletableFuture<ResponseMessage> uploadAsync(byte[] upload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.node().request(server.fid(), "upload", upload).get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void assertUploadComplete(CompletableFuture<ResponseMessage> transfer, byte[] upload)
            throws Exception {
        ResponseMessage resp = transfer.get(120, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
        assertEquals(upload.length, ByteBuffer.wrap(resp.getData()).getInt(), "the whole upload must arrive");
    }

    private static void report(String phase, Audio a, long[] delay) {
        System.out.printf("[DatagramPriorityTest] %-16s sent=%d notSent=%d recv=%d | sendDatagram p50=%dus p99=%dus"
                        + " max=%dus (monitor-blocked %dx/%dms, slowest-call cpu %dus) | delay p50=%dus p99=%dus max=%dus%n",
                phase, a.sent, a.notSent, delay.length, percentile(a.callUs, 50), percentile(a.callUs, 99),
                percentile(a.callUs, 100), a.blockedCount, a.blockedMs, a.slowCallCpuUs,
                percentile(delay, 50), percentile(delay, 99), percentile(delay, 100));
    }
}
