package fudp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fudp.packet.Frame;
import fudp.packet.Packet;
import fudp.packet.frames.DatagramFrame;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DATAGRAM (FUDP7) against the cross-client vectors that FreerForMac's
 * {@code tools/vector-gen} writes to {@code fudpVectors.json}. The same file
 * is checked by FC-JDK, FC-AJDK and the Mac's FCTransport; regenerate it
 * there and copy it here when the wire format changes.
 */
public class DatagramVectorTest {

    private static JsonObject vectors() throws Exception {
        try (InputStream in = DatagramVectorTest.class.getClassLoader().getResourceAsStream("fudpVectors.json")) {
            assertNotNull(in, "fudpVectors.json missing from test resources");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    public void datagramFrameMatchesVectors() throws Exception {
        JsonArray cases = vectors().getAsJsonArray("datagram_frame");
        assertFalse(cases.isEmpty());
        for (JsonElement e : cases) {
            JsonObject c = e.getAsJsonObject();
            String label = c.get("label").getAsString();
            byte[] data = Hex.decode(c.get("data_hex").getAsString());
            byte[] wire = Hex.decode(c.get("encoded_hex").getAsString());

            assertArrayEquals(wire, new DatagramFrame(data).toBytes(), label);
            ByteBuffer buf = ByteBuffer.wrap(wire);
            buf.get(); // type byte, consumed by Packet.parseFrames in real use
            assertArrayEquals(data, DatagramFrame.parse(buf).getData(), label);
            assertFalse(buf.hasRemaining(), label + ": parse must consume exactly the frame");
        }
    }

    @Test
    public void datagramPayloadMatchesVectors() throws Exception {
        JsonArray cases = vectors().getAsJsonArray("datagram_payload");
        assertFalse(cases.isEmpty());
        for (JsonElement e : cases) {
            JsonObject c = e.getAsJsonObject();
            String label = c.get("label").getAsString();
            boolean hasTs = c.get("include_timestamp").getAsBoolean();
            boolean hasEpoch = c.get("include_epoch").getAsBoolean();
            byte[] payload = Hex.decode(c.get("encoded_hex").getAsString());

            Packet in = new Packet(1, 1);
            in.getHeader().setHasTimestamp(hasTs);
            in.getHeader().setHasEpoch(hasEpoch);
            in.parseFrames(payload);
            if (hasEpoch) assertEquals(c.get("session_epoch").getAsLong(), in.getSessionEpoch(), label);

            // Every frame re-encodes to the vector's bytes, in order. PADDING
            // is skipped by the parser rather than returned as a frame.
            List<String> framesHex = new ArrayList<>();
            ByteArrayOutputStream joined = new ByteArrayOutputStream();
            joined.write(payload, 0, (hasTs ? 8 : 0) + (hasEpoch ? 8 : 0));
            for (JsonElement f : c.getAsJsonArray("frames_hex")) {
                byte[] bytes = Hex.decode(f.getAsString());
                joined.write(bytes);
                if (!f.getAsString().equals("00")) framesHex.add(f.getAsString());
            }
            assertArrayEquals(payload, joined.toByteArray(), label + ": vector frames must make up the payload");

            List<Frame> frames = in.getFrames();
            assertEquals(framesHex.size(), frames.size(), label);
            List<String> datagrams = new ArrayList<>();
            for (int i = 0; i < frames.size(); i++) {
                assertEquals(framesHex.get(i), Hex.toHexString(frames.get(i).toBytes()), label + " frame " + i);
                if (frames.get(i) instanceof DatagramFrame d) datagrams.add(Hex.toHexString(d.getData()));
            }

            List<String> expected = new ArrayList<>();
            for (JsonElement d : c.getAsJsonArray("datagrams_hex")) expected.add(d.getAsString());
            assertEquals(expected, datagrams, label);
            assertEquals(c.get("ack_eliciting").getAsBoolean(), in.isAckEliciting(), label);
        }
    }

    @Test
    public void maxDatagramSizeMatchesVectors() throws Exception {
        JsonArray cases = vectors().getAsJsonArray("datagram_max_size");
        assertFalse(cases.isEmpty());
        for (JsonElement e : cases) {
            JsonObject c = e.getAsJsonObject();
            int maxPacket = c.get("max_packet_size").getAsInt();
            assertEquals(c.get("max_datagram_size").getAsInt(), Protocol.maxDatagramSize(maxPacket),
                    "maxPacketSize=" + maxPacket);
        }
    }
}
