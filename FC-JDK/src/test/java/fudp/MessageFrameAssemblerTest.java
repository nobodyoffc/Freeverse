package fudp;

import fudp.node.AssembledMessage;
import fudp.node.MessageFrameAssembler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The assembler buffers one whole application message before it can be
 * extracted, so its cap must cover the largest file upload. A message over
 * the cap must fail loudly (IllegalStateException) and leave the buffer
 * empty, never corrupt silently.
 */
public class MessageFrameAssemblerTest {

    @Test
    public void overflowThrowsAndResetsBuffer() {
        MessageFrameAssembler assembler = new MessageFrameAssembler(1000);

        assembler.addData(new byte[600]);
        assembler.addData(new byte[400]); // exactly at the cap is allowed
        assertEquals(1000, assembler.getBufferSize());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> assembler.addData(new byte[1]));
        assertTrue(e.getMessage().contains("max=1000"), e.getMessage());

        // The in-flight message is unrecoverable; buffer must be reset so the
        // failure is clean rather than a corrupted reassembly.
        assertEquals(0, assembler.getBufferSize());
        assertFalse(assembler.hasPendingData());
    }

    /** Build a MessageCodec-framed message: type+msgId+flags+varint(len)+payload. */
    private static byte[] frame(byte[] payload) {
        byte[] len = fudp.util.Varint.encode(payload.length);
        byte[] msg = new byte[10 + len.length + payload.length];
        msg[0] = 1; // type
        // msgId (8 bytes) + flags (1 byte) left as zeros
        System.arraycopy(len, 0, msg, 10, len.length);
        System.arraycopy(payload, 0, msg, 10 + len.length, payload.length);
        return msg;
    }

    @Test
    public void extractsMessagesAcrossChunkBoundaries() {
        MessageFrameAssembler assembler = new MessageFrameAssembler();
        byte[] payloadA = new byte[70_000];
        byte[] payloadB = new byte[123];
        for (int i = 0; i < payloadA.length; i++) payloadA[i] = (byte) i;
        for (int i = 0; i < payloadB.length; i++) payloadB[i] = (byte) (i + 7);
        byte[] msgA = frame(payloadA);
        byte[] msgB = frame(payloadB);

        // Two messages back-to-back, fed in chunks that straddle the boundary
        byte[] all = new byte[msgA.length + msgB.length];
        System.arraycopy(msgA, 0, all, 0, msgA.length);
        System.arraycopy(msgB, 0, all, msgA.length, msgB.length);

        java.util.List<AssembledMessage> got = new java.util.ArrayList<>();
        int chunkSize = 1400;
        for (int off = 0; off < all.length; off += chunkSize) {
            int n = Math.min(chunkSize, all.length - off);
            byte[] chunk = new byte[n];
            System.arraycopy(all, off, chunk, 0, n);
            assembler.addData(chunk);
            got.addAll(assembler.extractMessages());
        }

        assertEquals(2, got.size());
        // 70KB / 123B are both under the spill threshold → in-RAM (bytes-backed).
        assertArrayEquals(msgA, got.get(0).bytes());
        assertArrayEquals(msgB, got.get(1).bytes());
        assertFalse(assembler.hasPendingData());
    }

    @Test
    public void oversizedDeclaredLengthFailsFastFromHeader() {
        MessageFrameAssembler assembler = new MessageFrameAssembler(1024);
        byte[] msg = frame(new byte[2048]); // declares 2048 > cap 1024
        byte[] headerChunk = new byte[64];
        System.arraycopy(msg, 0, headerChunk, 0, 64);
        // The impossible declared length is detected from the header — either eagerly
        // in addData (the spill check parses the length) or in extractMessages.
        assertThrows(IllegalStateException.class, () -> {
            assembler.addData(headerChunk);
            assembler.extractMessages();
        });
        assertEquals(0, assembler.getBufferedBytes());
    }

    /**
     * Per-chunk cost must be O(chunk), not O(buffered). The previous
     * implementation copied the whole buffer on every packet (O(n^2) per
     * transfer), which starved the single receive thread on multi-MB uploads:
     * ACKs lagged, the sender detected phantom loss, and the congestion window
     * collapsed. 32MB in MTU-sized chunks must assemble in well under a second;
     * quadratic behaviour takes minutes.
     */
    @Test
    public void assemblyIsLinearInTransferSize() {
        int size = 32 * 1024 * 1024;
        // Keep this exercising the in-RAM path: spill threshold above the message size.
        MessageFrameAssembler assembler = new MessageFrameAssembler(
                64L * 1024 * 1024, 64L * 1024 * 1024, null);
        byte[] msg = frame(new byte[size]);

        long start = System.currentTimeMillis();
        int chunkSize = 1400;
        java.util.List<AssembledMessage> got = java.util.Collections.emptyList();
        for (int off = 0; off < msg.length; off += chunkSize) {
            int n = Math.min(chunkSize, msg.length - off);
            byte[] chunk = new byte[n];
            System.arraycopy(msg, off, chunk, 0, n);
            assembler.addData(chunk);
            got = assembler.extractMessages();
        }
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(1, got.size());
        assertEquals(msg.length, got.get(0).length());
        assertTrue(elapsed < 5000, "32MB assembly took " + elapsed + "ms — per-chunk cost is not O(chunk)");
    }

    @Test
    public void largerCapAcceptsMessageOverTheOldDefault() {
        // Regression: uploads over 64MB (e.g. a 64.4MB jar via disk.carve) used
        // to overflow the hardcoded 64MB default and be dropped silently —
        // the server never saw the request and the client timed out.
        long overDefault = MessageFrameAssembler.DEFAULT_MAX_BUFFER_SIZE + 8 * 1024 * 1024;
        MessageFrameAssembler assembler = new MessageFrameAssembler(overDefault + 1024);

        byte[] chunk = new byte[8 * 1024 * 1024];
        long fed = 0;
        while (fed < overDefault) {
            assembler.addData(chunk);
            fed += chunk.length;
        }
        assertEquals(fed, assembler.getBufferedBytes());
    }

    /**
     * A message above the spill threshold is streamed to a temp file and delivered
     * as a file-backed {@link AssembledMessage}; its bytes must match the input and
     * a file-backed {@link fudp.message.ResponseMessage} must round-trip the payload.
     */
    @Test
    public void largeMessageSpillsToDiskAndRoundTrips() throws Exception {
        byte[] status = new byte[]{0, 0}; // statusCode 0
        byte[] body = new byte[200_000];
        for (int i = 0; i < body.length; i++) body[i] = (byte) (i * 31 + 7);
        byte[] respData = new byte[status.length + body.length];
        System.arraycopy(status, 0, respData, 0, 2);
        System.arraycopy(body, 0, respData, 2, body.length);

        // Frame as a RESPONSE (type=2) message.
        byte[] len = fudp.util.Varint.encode(respData.length);
        byte[] wire = new byte[10 + len.length + respData.length];
        wire[0] = (byte) fudp.message.MessageType.RESPONSE.getCode();
        System.arraycopy(len, 0, wire, 10, len.length);
        System.arraycopy(respData, 0, wire, 10 + len.length, respData.length);

        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "fudp-srvtest-" + System.nanoTime());
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        MessageFrameAssembler assembler = new MessageFrameAssembler(64L * 1024 * 1024, 4096, dir);

        java.util.List<AssembledMessage> got = new java.util.ArrayList<>();
        for (int off = 0; off < wire.length; off += 6000) {
            int n = Math.min(6000, wire.length - off);
            byte[] chunk = new byte[n];
            System.arraycopy(wire, off, chunk, 0, n);
            assembler.addData(chunk);
            got.addAll(assembler.extractMessages());
        }

        assertEquals(1, got.size());
        AssembledMessage m = got.get(0);
        assertTrue(m.isFileBacked());
        assertEquals(wire.length, m.length());

        // Decode header (mirrors FudpNode.handleIncomingFileBacked): find the data region.
        byte[] header = m.readHeader(32);
        assertEquals(fudp.message.MessageType.RESPONSE, fudp.message.MessageCodec.peekType(header));
        fudp.util.Varint.DecodeResult vr = fudp.util.Varint.decode(header, 10);
        int payloadOffset = 10 + vr.bytesConsumed;
        long dataOffset = payloadOffset + 2L;
        long dataLength = vr.value - 2;

        fudp.message.ResponseMessage r = new fudp.message.ResponseMessage();
        r.setFileBackedData(m.file(), dataOffset, dataLength);
        assertTrue(r.isFileBacked());
        assertEquals(body.length, r.dataLength());
        // Lazy getData() materialises the exact body bytes.
        assertArrayEquals(body, r.getData());

        r.deleteBackingFile();
        assertFalse(m.file().exists());
    }
}
