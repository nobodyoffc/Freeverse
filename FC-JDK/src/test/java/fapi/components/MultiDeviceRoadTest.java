package fapi.components;

import config.Settings;
import core.crypto.KeyTools;
import data.feipData.Service;
import fapi.client.FapiClient;
import fapi.components.map.MapEntry;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Several devices of one FID behind one MAP and ROAD, over real FUDP: they
 * share the FID's key and so its peer id, register separately, and a relay to
 * the FID reaches every one of them, not just the one that registered last.
 */
public class MultiDeviceRoadTest {

    private static final SecureRandom RNG = new SecureRandom();

    private static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    private static FudpNode node(byte[] priv, int port) throws Exception {
        NodeConfig c = new NodeConfig();
        c.setPort(port);
        c.setDataDir(Files.createTempDirectory("multi_device_" + port).toString());
        return new FudpNode(priv, c);
    }

    /** One device: a node under a FID's key, and a FAPI client to the server. */
    static final class Device {
        final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        final FudpNode node;
        final FapiClient fapi;

        Device(byte[] fidPriv, int port, String serverFid, byte[] serverPub, int serverPort) throws Exception {
            node = node(fidPriv, port);
            node.setEventListener(new NodeEventListener() {
                @Override
                public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                    received.add(new String(data, StandardCharsets.UTF_8));
                }
            });
            node.start();
            node.addPeer(serverFid, serverPub, "127.0.0.1", serverPort);
            fapi = new FapiClient(node, serverFid, "multi-device-test");
        }
    }

    @Test
    public void aRelayReachesEveryDeviceOfTheFid() throws Exception {
        byte[] serverPriv = key();
        byte[] serverPub = KeyTools.prikeyToPubkey(serverPriv);
        String serverFid = KeyTools.pubkeyToFchAddr(serverPub);
        FudpNode serverNode = node(serverPriv, 19960);

        Service service = new Service(); // off chain and free: no billing in the way
        service.setId("multi-device-test");
        service.setType("FAPI@No1_NrC7");
        service.setPricePerKB("0");
        service.setPricePerKBIn("0");
        service.setPricePerKBOut("0");
        Settings settings = mock(Settings.class);
        when(settings.getService()).thenReturn(service);
        when(settings.getMainFid()).thenReturn(serverFid);
        when(settings.getDbDir()).thenReturn(Files.createTempDirectory("multi_device_db").toString());
        FapiServer server = new FapiServer(settings);
        server.setFudpNode(serverNode);
        server.initialize();
        server.registerComponent(new MapComponent());
        server.registerComponent(new RoadComponent());
        serverNode.setEventListener(server);

        byte[] bobPriv = key();
        String bobFid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(bobPriv));
        List<Device> devices = new ArrayList<>();
        Device sender = null;
        try {
            serverNode.start();
            // Bob's phone and Bob's second phone: one FID, one key, two sockets.
            devices.add(new Device(bobPriv, 19961, serverFid, serverPub, 19960));
            devices.add(new Device(bobPriv, 19962, serverFid, serverPub, 19960));
            int port = 19961;
            for (Device d : devices) {
                MapEntry e = d.fapi.mapRegister();
                assertNotNull(e, "registered");
                assertEquals(port++, e.getObservedPort(), "each device under its own address");
            }
            MapComponent map = server.getComponent(MapComponent.class);
            assertEquals(2, map.getEntries(bobFid).size(), "both devices are in the MAP");

            sender = new Device(key(), 19963, serverFid, serverPub, 19960);
            FapiClient.RoadRelayResult r = sender.fapi.roadRelay(bobFid, "ring".getBytes(StandardCharsets.UTF_8));
            assertNotNull(r, "relayed");
            for (Device d : devices) {
                assertEquals("ring", d.received.poll(5, TimeUnit.SECONDS), "every device of the FID gets it");
            }
        } finally {
            if (sender != null) sender.node.stop();
            for (Device d : devices) d.node.stop();
            serverNode.stop();
        }
    }
}
