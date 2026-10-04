package fapi.components;

import config.Settings;
import core.crypto.KeyTools;
import data.feipData.Service;
import fapi.client.FapiClient;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A failed relay target is counted once in road.stats (FAPI15 §6): a target
 * the server has never heard of is NOT_FOUND, not also DELIVERY_FAILED. And the
 * client decodes the server's error replies, keeping their data.
 */
public class RoadStatsCountTest {

    private static final SecureRandom RNG = new SecureRandom();

    private static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    private static FudpNode node(byte[] priv, int port) throws Exception {
        NodeConfig c = new NodeConfig();
        c.setPort(port);
        c.setDataDir(Files.createTempDirectory("road_stats_" + port).toString());
        return new FudpNode(priv, c);
    }

    @Test
    public void anUnknownTargetCountsOnceAsNotFound() throws Exception {
        byte[] serverPriv = key();
        byte[] serverPub = KeyTools.prikeyToPubkey(serverPriv);
        String serverFid = KeyTools.pubkeyToFchAddr(serverPub);
        FudpNode serverNode = node(serverPriv, 19970);

        Service service = new Service(); // off chain and free: no billing in the way
        service.setId("road-stats-test");
        service.setType("FAPI@No1_NrC7");
        service.setPricePerKB("0");
        service.setPricePerKBIn("0");
        service.setPricePerKBOut("0");
        Settings settings = mock(Settings.class);
        when(settings.getService()).thenReturn(service);
        when(settings.getMainFid()).thenReturn(serverFid);
        when(settings.getDbDir()).thenReturn(Files.createTempDirectory("road_stats_db").toString());
        FapiServer server = new FapiServer(settings);
        server.setFudpNode(serverNode);
        server.initialize();
        server.registerComponent(new MapComponent());
        server.registerComponent(new RoadComponent());
        serverNode.setEventListener(server);

        FudpNode senderNode = node(key(), 19971);
        try {
            serverNode.start();
            senderNode.start();
            senderNode.addPeer(serverFid, serverPub, "127.0.0.1", 19970);
            FapiClient sender = new FapiClient(senderNode, serverFid, "road-stats-test");

            String nobody = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(key()));
            FapiClient.RoadRelayResult r = sender.roadRelay(nobody, "hello".getBytes(StandardCharsets.UTF_8));
            assertNotNull(r, "relay answered");
            assertFalse(r.success());
            assertEquals(502, r.code(), "every target failed");
            // The 502 reply still carries the per-target results.
            FapiClient.TargetRelayResult t = r.getResult(nobody);
            assertNotNull(t, "a result for the target");
            assertFalse(t.success());
            assertEquals(404, t.code());
            assertEquals("Relayed to 0/1 targets", r.message(), "the server's message, decoded");

            // Any error reply is decoded, not kept as raw bytes.
            assertNull(sender.mapFind(nobody));
            assertEquals(404, sender.getLastResponse().getCode());
            assertEquals("FID not registered: " + nobody, sender.getLastResponse().getMessage());

            Map<String, Object> stats = sender.roadStats();
            assertNotNull(stats, "road.stats answered");
            @SuppressWarnings("unchecked")
            Map<String, Object> errors = (Map<String, Object>) stats.get("errorCounts");
            assertEquals(1, ((Number) errors.get("NOT_FOUND")).intValue(), "NOT_FOUND: " + errors);
            assertEquals(0, ((Number) errors.get("DELIVERY_FAILED")).intValue(), "DELIVERY_FAILED: " + errors);
            assertEquals(1, ((Number) stats.get("failedRelays")).intValue());
        } finally {
            senderNode.stop();
            serverNode.stop();
        }
    }
}
