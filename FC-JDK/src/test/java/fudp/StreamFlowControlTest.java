package fudp;

import fudp.stream.FlowControlViolationException;
import fudp.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link Stream} receiver-side flow control.
 * <p>
 * Guards the fix for the disk.uploadAll failure where a large upload buffered
 * ~100MB of out-of-order data behind a single lost early frame; the flow-control
 * cap then rejected the retransmitted gap-filling frame — the very frame that
 * would have drained the whole buffer — throwing a FlowControlViolationException
 * that tore down the entire connection.
 */
public class StreamFlowControlTest {

    // Mirror of Stream.INITIAL_MAX_STREAM_DATA (private there).
    private static final long MAX_RECV = 100_000_000L;

    /**
     * The core regression: fill the out-of-order buffer to the cap by holding
     * back the very first frame, then deliver that first (in-order) frame. It
     * must be accepted and drain the whole buffer, NOT throw.
     */
    @Test
    void inOrderGapFillerAcceptedWhenBufferAtCap() {
        Stream stream = new Stream(32);

        int chunk = 8_000;
        byte[] gapFrame = bytesOf(chunk);          // offset 0, held back
        // Buffer out-of-order frames [chunk .. ~MAX_RECV) behind the missing offset 0.
        long offset = chunk;
        while (offset + chunk <= MAX_RECV) {
            byte[] data = bytesOf(chunk);
            assertNull(stream.onDataReceived(offset, data, false),
                    "out-of-order frame should buffer, not assemble");
            offset += chunk;
        }
        assertTrue(stream.getRecvOffset() == 0,
                "no in-order data yet, recvOffset must still be 0");

        // The buffer is now within one chunk of the cap. Before the fix, feeding
        // the in-order offset-0 frame threw FlowControlViolationException because
        // recvData + chunk > maxRecvData. It must now be accepted and drain.
        byte[] assembled = assertDoesNotThrow(
                () -> stream.onDataReceived(0, gapFrame, false),
                "in-order gap-filling frame must never be rejected by flow control");
        assertNotNull(assembled, "gap fill must assemble a large contiguous run");
        assertEquals(offset, stream.getRecvOffset(),
                "recvOffset should advance across the whole drained buffer");
    }

    /** A genuinely abusive peer sending only out-of-order data still trips the cap. */
    @Test
    void outOfOrderFloodStillTripsCap() {
        Stream stream = new Stream(33);
        int chunk = 8_000;
        // Start at offset chunk (never fill offset 0) so nothing ever drains.
        long offset = chunk;
        assertThrows(FlowControlViolationException.class, () -> {
            long o = chunk;
            while (true) {
                stream.onDataReceived(o, bytesOf(chunk), false);
                o += chunk;
            }
        });
    }

    private static byte[] bytesOf(int n) {
        return new byte[n];
    }
}
