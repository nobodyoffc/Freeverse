package fudp;

import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import fudp.transport.DatagramResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static fudp.DatagramTestSupport.*;

/**
 * The throwaway "forward everything" relay for the Phase 2 audio spike
 * (VOICE_SPEC §14). Every DATAGRAM from one joined phone goes to every other,
 * unread. No admission, no charging, no speaker selection: that is the CALL
 * component, Phase 3 and 4.
 * <p>
 * Run on the relay host, with UDP {@value #PORT} open:
 * {@code mvn -f FC-JDK/pom.xml test -Dtest=VoiceSpikeRelay -Dsurefire.failIfNoSpecifiedTests=false}.
 * It serves for {@code -Dspike.minutes} (default 240). Phones join with the
 * app's Voice test screen in relay mode; the key is a constant that the app
 * shares ({@code SpikeTransport.relayKey}).
 * <p>
 * For the gate's network faults, on the relay host:
 * {@code sudo tc qdisc add dev <iface> root netem loss 5% delay 30ms 30ms distribution normal}
 * (remove with {@code sudo tc qdisc del dev <iface> root}). That shapes what
 * the relay sends, so each phone hears the other through a lossy, jittery path.
 */
public class VoiceSpikeRelay {

    static final int PORT = 19900;

    @Test
    public void serve() throws Exception {
        long minutes = Long.getLong("spike.minutes", 240);
        byte[] priv = MessageDigest.getInstance("SHA-256")
                .digest("FreerVoiceSpike relay".getBytes(StandardCharsets.UTF_8));
        NodeConfig config = new NodeConfig();
        config.setDatagramRateBps(1_000_000); // several speakers to each phone
        NodeBundle relay = createNode(PORT, config, priv);
        FudpNode node = relay.node();
        List<Long> joined = new CopyOnWriteArrayList<>();
        AtomicLong in = new AtomicLong(), out = new AtomicLong(), drops = new AtomicLong();

        node.setEventListener(new NodeEventListener() {
            @Override
            public void onRequestReceived(String peerId, long connectionId, long requestId,
                                          String serviceName, byte[] data) {
                switch (serviceName) {
                    case "join" -> {
                        node.enableDatagrams(connectionId);
                        if (!joined.contains(connectionId)) joined.add(connectionId);
                        System.out.println("[VoiceSpikeRelay] joined " + peerId + " (" + joined.size() + " in)");
                    }
                    case "leave" -> {
                        joined.remove(connectionId);
                        System.out.println("[VoiceSpikeRelay] left " + peerId + " (" + joined.size() + " in)");
                    }
                    default -> { }
                }
                try {
                    node.respond(peerId, connectionId, requestId, ResponseMessage.STATUS_SUCCESS, new byte[1]);
                } catch (Exception e) {
                    System.out.println("[VoiceSpikeRelay] respond failed: " + e.getMessage());
                }
            }

            @Override
            public void onPeerDisconnected(String peerId, long connectionId) {
                if (joined.remove(connectionId)) {
                    System.out.println("[VoiceSpikeRelay] gone " + peerId + " (" + joined.size() + " in)");
                }
            }

            @Override
            public void onDatagram(String peerId, long connectionId, byte[] data) {
                in.incrementAndGet();
                for (long c : joined) {
                    if (c == connectionId) continue; // nobody hears themselves
                    if (node.sendDatagram(c, data) == DatagramResult.SENT) out.incrementAndGet();
                    else drops.incrementAndGet();
                }
            }
        });
        node.start();
        System.out.println("[VoiceSpikeRelay] on UDP " + PORT + " as " + relay.fid() + " for " + minutes + " min");
        long end = System.nanoTime() + TimeUnit.MINUTES.toNanos(minutes);
        try {
            while (System.nanoTime() < end) {
                Thread.sleep(10_000);
                System.out.println("[VoiceSpikeRelay] " + joined.size() + " in; frames in " + in.get()
                        + ", out " + out.get() + ", dropped " + drops.get());
            }
        } finally {
            node.stop();
        }
    }
}
