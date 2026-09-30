package fapi.service;

import config.Settings;
import core.crypto.KeyTools;
import fapi.FapiComponent;
import fapi.client.FapiClient;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.message.UnifiedCodec.UnifiedResponse;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import utils.Hex;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A large upload goes from the reassembly spill file to a component that takes
 * a stream, and never becomes one array on the server; the spill file is gone
 * once the request is answered. A small upload, or one for a method that takes
 * no stream, arrives as an array as before.
 */
public class StreamedUploadTest {

    private static final SecureRandom RNG = new SecureRandom();
    /** Past the assembler's 16 MB spill threshold. */
    private static final int LARGE = 20 * 1024 * 1024;

    private final List<FudpNode> nodes = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (FudpNode n : nodes) {
            try {
                n.stop();
            } catch (Exception ignore) {
                // best effort
            }
        }
    }

    /** Hashes what it is given and says how it came: "stream" or "bytes". */
    static final class UploadComponent implements FapiComponent {
        @Override
        public String getName() {
            return "UP";
        }

        @Override
        public List<String> getApiList() {
            return List.of("up.put", "up.echo");
        }

        @Override
        public void initialize(FapiServer server) {}

        @Override
        public FapiResponse handleRequest(FapiRequest request, String peerId) {
            return FapiResponse.success(request.getId(), Map.of("via", "none"));
        }

        @Override
        public boolean streamsUpload(String method) {
            return "put".equals(method);
        }

        @Override
        public UnifiedResponse handleUnifiedUpload(FapiRequest request, InputStream data, long length,
                                                   String peerId) throws IOException {
            MessageDigest md = sha256();
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            for (int n; (n = data.read(buf)) > 0; ) {
                md.update(buf, 0, n);
                total += n;
            }
            return answer(request, "stream", md.digest(), total, length);
        }

        @Override
        public UnifiedResponse handleUnifiedRequest(FapiRequest request, byte[] binaryData, String peerId) {
            MessageDigest md = sha256();
            md.update(binaryData);
            return answer(request, "bytes", md.digest(), binaryData.length, binaryData.length);
        }

        private static UnifiedResponse answer(FapiRequest request, String via, byte[] sha, long read, long length) {
            return new UnifiedResponse(FapiResponse.success(request.getId(),
                    Map.of("via", via, "sha", Hex.toHex(sha), "read", read, "length", length)), null);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    private FudpNode node(byte[] priv, int port, String dataDir) throws Exception {
        NodeConfig c = new NodeConfig();
        c.setPort(port);
        c.setMaxPacketSize(1400);
        c.setSocketBufferSize(2 * 1024 * 1024);
        c.setDataDir(dataDir);
        FudpNode n = new FudpNode(priv, c);
        nodes.add(n);
        return n;
    }

    private record Setup(FapiClient client, File spillDir) {}

    private Setup setUp(int port) throws Exception {
        byte[] serverPriv = key();
        byte[] serverPub = KeyTools.prikeyToPubkey(serverPriv);
        String serverFid = KeyTools.pubkeyToFchAddr(serverPub);
        String dataDir = System.getProperty("java.io.tmpdir") + "/streamed_upload_" + port + "_" + System.nanoTime();
        FudpNode serverNode = node(serverPriv, port, dataDir);

        Settings settings = mock(Settings.class);
        when(settings.getMainFid()).thenReturn(serverFid);
        FapiServer server = new FapiServer(settings); // no on-chain Service: no billing
        server.setFudpNode(serverNode);
        server.initialize();
        server.registerComponent(new UploadComponent());
        serverNode.setEventListener(server);
        serverNode.start();

        FudpNode clientNode = node(key(), port + 1,
                System.getProperty("java.io.tmpdir") + "/streamed_upload_client_" + port + "_" + System.nanoTime());
        clientNode.start();
        clientNode.addPeer(serverFid, serverPub, "127.0.0.1", port);
        FapiClient client = new FapiClient(clientNode, serverFid, "upload-test");
        return new Setup(client, new File(dataDir, "recv-spill"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> upload(FapiClient client, String api, byte[] content) {
        FapiRequest request = FapiRequest.operation(api, Map.of());
        UnifiedResponse r = client.requestWithBinaryData(request, content, 120);
        assertNotNull(r, api + ": no response: " + client.getLastError());
        assertTrue(r.response().isSuccess(), api + " refused: " + r.response().getCode() + " "
                + r.response().getMessage());
        return (Map<String, Object>) r.response().getData();
    }

    private static byte[] random(int size) {
        byte[] b = new byte[size];
        RNG.nextBytes(b);
        return b;
    }

    private static void assertSame(byte[] content, Map<String, Object> got) {
        MessageDigest md = sha256();
        assertEquals(Hex.toHex(md.digest(content)), got.get("sha"), "the server read exactly what was sent");
        assertEquals(content.length, ((Number) got.get("read")).longValue());
        assertEquals(content.length, ((Number) got.get("length")).longValue());
    }

    /** The server's spill directory holds no file, allowing a moment for the release after the answer. */
    private static void assertSpillEmpty(File spillDir) throws InterruptedException {
        String[] left = null;
        for (int i = 0; i < 50; i++) {
            left = spillDir.list();
            if (left == null || left.length == 0) return;
            Thread.sleep(100);
        }
        fail("spill files left behind: " + String.join(", ", left));
    }

    @Test
    @Timeout(120)
    void aSmallUploadArrivesAsBytes() throws Exception {
        Setup s = setUp(19961);
        byte[] content = random(1024 * 1024);
        Map<String, Object> got = upload(s.client(), "up.put", content);
        assertEquals("bytes", got.get("via"));
        assertSame(content, got);
    }

    @Test
    @Timeout(300)
    void aLargeUploadIsStreamedFromTheSpillFile() throws Exception {
        Setup s = setUp(19963);
        byte[] content = random(LARGE);
        Map<String, Object> got = upload(s.client(), "up.put", content);
        assertEquals("stream", got.get("via"), "a spilled upload reaches a streaming component as a stream");
        assertSame(content, got);
        assertSpillEmpty(s.spillDir());
    }

    @Test
    @Timeout(300)
    void aLargeUploadForAMethodWithoutStreamingArrivesAsBytes() throws Exception {
        Setup s = setUp(19965);
        byte[] content = random(LARGE);
        Map<String, Object> got = upload(s.client(), "up.echo", content);
        assertEquals("bytes", got.get("via"));
        assertSame(content, got);
        assertSpillEmpty(s.spillDir());
    }
}
