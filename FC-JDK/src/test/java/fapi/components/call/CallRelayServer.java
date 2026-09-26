package fapi.components.call;

import config.Settings;
import core.crypto.KeyTools;
import data.feipData.Service;
import fapi.components.CallComponent;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A CALL relay for testing real phones before one is registered on chain
 * (VOICE_SPEC §7): a FapiServer with CallComponent, advertising an off-chain
 * FAPI service at price zero, so the app's bootstrapFromUrl finds it.
 * <p>
 * Run on the relay host, UDP {@code -Dcall.port} (default 19950) open:
 * {@code mvn -f FC-JDK/pom.xml test -Dtest=CallRelayServer -Dsurefire.failIfNoSpecifiedTests=false}.
 * Serves for {@code -Dcall.minutes} (default 240); {@code -Dcall.maxPacket} caps the UDP payload
 * (default 1350). On the phones, set the
 * Voice test screen's "Call relay for real calls" to {@code fudp://<host>:<port>}.
 */
public class CallRelayServer {

    @Test
    public void serve() throws Exception {
        int port = Integer.getInteger("call.port", 19950);
        long minutes = Long.getLong("call.minutes", 240);
        byte[] priv = MessageDigest.getInstance("SHA-256")
                .digest("FreerCall test relay".getBytes(StandardCharsets.UTF_8));
        String fid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(priv));

        Service service = new Service();
        service.setId("call-relay-test-" + fid);
        service.setStdName("CALL test relay");
        service.setType("FAPI@No1_NrC7");
        service.setVer("1");
        service.setPricePerKB("0");
        service.setPricePerKBIn("0");
        service.setPricePerKBOut("0");

        Settings settings = mock(Settings.class);
        when(settings.getService()).thenReturn(service);
        when(settings.getMainFid()).thenReturn(fid);
        when(settings.getDbDir()).thenReturn(Files.createTempDirectory("call-relay-db").toString());

        FapiServer server = new FapiServer(settings);
        NodeConfig config = new NodeConfig();
        config.setPort(port);
        // Mobile paths through IPv6 translation may carry only ~1280 bytes; QUIC keeps to 1200.
        config.setMaxPacketSize(Integer.getInteger("call.maxPacket", 1350));
        config.setDataDir(Files.createTempDirectory("call-relay-fudp").toString());
        config.setPongDataProvider(server::buildAdvertiseData); // what ServiceBootstrap does
        FudpNode node = new FudpNode(priv, config);
        server.setFudpNode(node);
        server.initialize();
        server.registerComponent(new CallComponent());
        node.setEventListener(server);
        node.start();
        System.out.println("[CallRelayServer] CALL relay on UDP " + port + " as " + fid + " for " + minutes + " min");
        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(minutes));
        } finally {
            node.stop();
        }
    }
}
