package fudp;

import core.crypto.KeyTools;
import fudp.message.*;
import fudp.node.*;
import fudp.security.DDoSConfig;
import org.bitcoinj.core.ECKey;
import ui.Menu;
import utils.Hex;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CLI for FUDP Node - encrypted P2P communication
 */
public class StartFudpNode implements NodeEventListener {

    private static final String APP_NAME = "FUDP Node";
    private static final String VERSION = "1.0.0";

    private FudpNode node;
    private byte[] privateKey;
    private BufferedReader br;
    private boolean running = false;

    public static void main(String[] args) {
        StartFudpNode app = new StartFudpNode();
        app.run();
    }

    public void run() {
        br = new BufferedReader(new InputStreamReader(System.in));

        Menu.welcome(APP_NAME + " v" + VERSION);
        System.out.println("Encrypted P2P UDP Communication\n");

        // Initialize or load key
        if (!initializeKey()) {
            System.out.println("Failed to initialize key. Exiting.");
            return;
        }

        // Main menu loop
        Menu mainMenu = new Menu(APP_NAME, this::shutdown);
        mainMenu.add("Start Node", this::startNode);
        mainMenu.add("Stop Node", this::stopNode);
        mainMenu.add("Node Status", this::showStatus);
        mainMenu.add("Performance Stats", this::showPerformanceStats);
        mainMenu.add("Peer Management", this::peerMenu);
        mainMenu.add("Send Notify", this::sendNotifyMenu);
        mainMenu.add("Ping Peer", this::pingPeer);
        mainMenu.add("Send Request", this::sendRequest);
        mainMenu.add("Generate New Key", this::generateNewKey);
        mainMenu.add("DDoS Defence", this::ddosDefenceMenu);

        mainMenu.showAndSelect(br);
    }

    private boolean initializeKey() {
        System.out.println("Key Options:");
        System.out.println("1. Generate new key");
        System.out.println("2. Enter existing private key (hex)");
        System.out.print("Choice: ");

        try {
            String choice = br.readLine().trim();

            if ("1".equals(choice)) {
                ECKey key = new ECKey();
                privateKey = key.getPrivKeyBytes();
                String fid = KeyTools.pubkeyToFchAddr(key.getPubKey());
                System.out.println("\nGenerated new identity:");
                System.out.println("FID: " + fid);
                System.out.println("Private Key (save this!): " + Hex.toHex(privateKey));
                System.out.println("Public Key: " + Hex.toHex(key.getPubKey()));
                return true;
            } else if ("2".equals(choice)) {
                System.out.print("Enter private key (hex): ");
                String keyHex = br.readLine().trim();
                privateKey = Hex.fromHex(keyHex);
                if (privateKey.length != 32) {
                    System.out.println("Invalid private key length. Must be 32 bytes.");
                    return false;
                }
                ECKey key = ECKey.fromPrivate(privateKey);
                String fid = KeyTools.pubkeyToFchAddr(key.getPubKey());
                System.out.println("Loaded identity: " + fid);
                return true;
            } else {
                // Default: generate new key
                ECKey key = new ECKey();
                privateKey = key.getPrivKeyBytes();
                String fid = KeyTools.pubkeyToFchAddr(key.getPubKey());
                System.out.println("Generated new identity: " + fid);
                return true;
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return false;
        }
    }

    private void startNode() {
        if (node != null && node.isRunning()) {
            System.out.println("Node is already running.");
            return;
        }

        try {
            System.out.print("Enter port (default 9000): ");
            String portStr = br.readLine().trim();
            int port = portStr.isEmpty() ? 9000 : Integer.parseInt(portStr);

            NodeConfig config = new NodeConfig()
                    .setPort(port)
                    .setDataDir("fudp_data");

            node = new FudpNode(privateKey, config);
            node.setEventListener(this);
            node.start();
            running = true;

            System.out.println("\nNode started successfully!");
            System.out.println("FID: " + node.getLocalFid());
            System.out.println("Port: " + port);
            System.out.println("Public Key: " + Hex.toHex(node.getLocalPublicKey()));

        } catch (Exception e) {
            System.out.println("Failed to start node: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopNode() {
        if (node == null || !node.isRunning()) {
            System.out.println("Node is not running.");
            return;
        }

        node.stop();
        running = false;
        System.out.println("Node stopped.");
    }

    private void showStatus() {
        if (node == null) {
            System.out.println("Node not initialized.");
            return;
        }

        System.out.println("\n=== Node Status ===");
        System.out.println("Running: " + node.isRunning());
        System.out.println("FID: " + node.getLocalFid());
        System.out.println("Port: " + node.getConfig().getPort());
        System.out.println("Public Key: " + Hex.toHex(node.getLocalPublicKey()));

        List<Peer> peers = node.listPeers();
        System.out.println("\nKnown Peers: " + peers.size());
        for (Peer peer : peers) {
            String alias = peer.getAlias() != null ? " (" + peer.getAlias() + ")" : "";
            String address = peer.hasAddress() ? peer.getHost() + ":" + peer.getPort() : "no address";
            System.out.println("  - " + peer.getId() + alias + " @ " + address);
        }
    }

    private void showPerformanceStats() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        Menu statsMenu = new Menu("Performance Stats");
        statsMenu.add("Node Overview", this::showNodeOverview);
        statsMenu.add("Peer Details", this::showPeerDetails);
        statsMenu.add("Ping Test", this::pingTestWithStats);

        statsMenu.showAndSelect(br);
    }

    private void showNodeOverview() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        NodeStats stats = node.getNodeStats();
        System.out.println("\n" + stats.toString());

        if (!stats.getPeerStatsList().isEmpty()) {
            System.out.println("\n--- Per-Peer Summary ---");
            for (NodeStats.PeerStats ps : stats.getPeerStatsList()) {
                System.out.println(ps.toString());
            }
        }
    }

    private void showPeerDetails() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Peer FID or alias: ");
            String peer = br.readLine().trim();

            NodeStats.PeerStats stats = node.getPeerStats(peer);
            if (stats == null) {
                System.out.println("Peer not found or not connected: " + peer);
                return;
            }

            System.out.println("\n=== Peer Statistics: " + stats.getPeerId() + " ===");
            System.out.println("Connection State: " + stats.getState());
            System.out.println();
            System.out.println("--- Packet Stats ---");
            System.out.println("  Sent:     " + stats.getPacketsSent());
            System.out.println("  Received: " + stats.getPacketsReceived());
            System.out.println("  Bytes Out: " + formatBytes(stats.getBytesOut()));
            System.out.println("  Bytes In:  " + formatBytes(stats.getBytesIn()));
            System.out.println();
            System.out.println("--- Retransmit & Loss Stats ---");
            System.out.println("  Retransmits:      " + stats.getRetransmitCount() + " (" + stats.getRetransmitRatePercent() + " retransmit rate)");
            System.out.println("  Suspected Lost:   " + stats.getSuspectedLostCount() + " (triggered retransmit)");
            System.out.println("  Recovered (ACKed):" + stats.getAckedAfterSuspectedLost() + " (false positives)");
            System.out.println("  Effective Lost:   " + stats.getLostPacketCount() + " (" + stats.getLossRatePercent() + " loss rate)");
            System.out.println();
            System.out.println("--- RTT Stats ---");
            System.out.println("  Smoothed RTT: " + stats.getSmoothedRttMs() + " ms");
            System.out.println("  Min RTT:      " + stats.getMinRttMs() + " ms");
            System.out.println("  RTT Variance: " + stats.getRttVarianceMs() + " ms");
            System.out.println("  RTO:          " + stats.getRtoMs() + " ms");
            System.out.println();
            System.out.println("--- Congestion Control ---");
            System.out.println("  State:           " + stats.getCcState());
            System.out.println("  Congestion Wnd:  " + formatBytes(stats.getCongestionWindow()));
            System.out.println("  Bytes In Flight: " + formatBytes(stats.getBytesInFlight()));

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void pingTestWithStats() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Peer FID or alias: ");
            String peer = br.readLine().trim();

            System.out.print("Number of pings (default 5): ");
            String countStr = br.readLine().trim();
            int count = countStr.isEmpty() ? 5 : Integer.parseInt(countStr);

            System.out.println("\nPinging " + peer + " with " + count + " packets...\n");

            long totalRtt = 0;
            long minRtt = Long.MAX_VALUE;
            long maxRtt = 0;
            int success = 0;
            int failed = 0;

            for (int i = 0; i < count; i++) {
                try {
                    long start = System.currentTimeMillis();
                    PongMessage pong = node.pingAwaitPong(peer, false, 5000).get(5, TimeUnit.SECONDS);
                    long rtt = System.currentTimeMillis() - start;

                    System.out.printf("  Reply from %s: time=%d ms%n", peer, rtt);

                    totalRtt += rtt;
                    if (rtt < minRtt) minRtt = rtt;
                    if (rtt > maxRtt) maxRtt = rtt;
                    success++;
                } catch (Exception e) {
                    System.out.println("  Request timed out.");
                    failed++;
                }

                // Wait 1 second between pings
                if (i < count - 1) {
                    Thread.sleep(1000);
                }
            }

            System.out.println();
            System.out.println("--- Ping Statistics for " + peer + " ---");
            System.out.println("  Packets: Sent=" + count + ", Received=" + success + ", Lost=" + failed
                    + " (" + (failed * 100 / count) + "% loss)");
            if (success > 0) {
                System.out.println("  RTT: min=" + minRtt + "ms, avg=" + (totalRtt / success) + "ms, max=" + maxRtt + "ms");
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void peerMenu() {
        Menu peerMenu = new Menu("Peer Management");
        peerMenu.add("Add Peer", this::addPeer);
        peerMenu.add("List Peers", this::listPeers);
        peerMenu.add("Remove Peer", this::removePeer);
        peerMenu.add("Set Alias", this::setAlias);

        peerMenu.showAndSelect(br);
    }

    private void addPeer() {
        if (node == null) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Peer Public Key (hex): ");
            String pubkeyHex = br.readLine().trim();
            byte[] pubkey = Hex.fromHex(pubkeyHex);

            // Derive FID from public key
            String peerId = KeyTools.pubkeyToFchAddr(pubkey);

            System.out.print("Host (IP or hostname): ");
            String host = br.readLine().trim();

            System.out.print("Port: ");
            int port = Integer.parseInt(br.readLine().trim());

            System.out.print("Alias (optional): ");
            String alias = br.readLine().trim();

            if (alias.isEmpty()) {
                node.addPeer(peerId, pubkey, host, port);
            } else {
                node.addPeer(peerId, pubkey, host, port, alias);
            }

            System.out.println("Peer added: " + peerId);

        } catch (Exception e) {
            System.out.println("Error adding peer: " + e.getMessage());
        }
    }

    private void listPeers() {
        if (node == null) {
            System.out.println("Start node first.");
            return;
        }

        List<Peer> peers = node.listPeers();
        if (peers.isEmpty()) {
            System.out.println("No peers.");
            return;
        }

        System.out.println("\n=== Known Peers ===");
        for (int i = 0; i < peers.size(); i++) {
            Peer peer = peers.get(i);
            String alias = peer.getAlias() != null ? " (" + peer.getAlias() + ")" : "";
            String address = peer.hasAddress() ? peer.getHost() + ":" + peer.getPort() : "no address";
            System.out.println((i + 1) + ". " + peer.getId() + alias);
            System.out.println("   Address: " + address);
            System.out.println("   PubKey: " + Hex.toHex(peer.getPublicKey()));
        }
    }

    private void removePeer() {
        if (node == null) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Peer FID or alias to remove: ");
            String identifier = br.readLine().trim();
            node.removePeer(identifier);
            System.out.println("Peer removed.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void setAlias() {
        if (node == null) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Peer FID: ");
            String peerId = br.readLine().trim();

            System.out.print("New alias: ");
            String alias = br.readLine().trim();

            node.setAlias(peerId, alias);
            System.out.println("Alias set.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // Notify Menu (replaces Send Chat + Send Bytes)

    private void sendNotifyMenu() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Peer FID or alias: ");
            String peer = br.readLine().trim();

            System.out.print("Data type (0=raw, 1=json, 2=protobuf, default 0): ");
            String typeStr = br.readLine().trim();
            int dataType = typeStr.isEmpty() ? 0 : Integer.parseInt(typeStr);

            System.out.print("Data (text, will be converted to bytes): ");
            String dataStr = br.readLine().trim();
            byte[] data = dataStr.getBytes(StandardCharsets.UTF_8);

            System.out.print("Request ACK? (y/N): ");
            String ackStr = br.readLine().trim().toLowerCase();
            boolean wantAck = "y".equals(ackStr) || "yes".equals(ackStr);

            long msgId;
            if (wantAck) {
                msgId = node.sendNotifyWithAck(peer, data, dataType);
                System.out.println("Notify sent to " + peer + " (ID: " + msgId + ", waiting for ACK)");
            } else {
                msgId = node.sendNotify(peer, data, dataType);
                System.out.println("Notify sent to " + peer + " (ID: " + msgId + ")");
            }

        } catch (Exception e) {
            System.out.println("Error sending notify: " + e.getMessage());
        }
    }

    private void pingPeer() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Peer FID or alias: ");
            String peer = br.readLine().trim();

            System.out.println("Pinging " + peer + "...");
            node.ping(peer);

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void sendRequest() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Peer FID or alias: ");
            String peer = br.readLine().trim();

            System.out.print("Service name: ");
            String service = br.readLine().trim();

            System.out.print("Data: ");
            String dataStr = br.readLine().trim();
            byte[] data = dataStr.getBytes();

            System.out.println("Sending request...");
            CompletableFuture<ResponseMessage> future = node.request(peer, service, data);

            try {
                ResponseMessage response = future.get(10, TimeUnit.SECONDS);
                System.out.println("Response status: " + response.getStatusCode());
                if (response.getData() != null) {
                    System.out.println("Response data: " + new String(response.getData()));
                }
            } catch (Exception e) {
                System.out.println("Request failed: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void generateNewKey() {
        ECKey key = new ECKey();
        privateKey = key.getPrivKeyBytes();
        String fid = KeyTools.pubkeyToFchAddr(key.getPubKey());

        System.out.println("\nGenerated new identity:");
        System.out.println("FID: " + fid);
        System.out.println("Private Key: " + Hex.toHex(privateKey));
        System.out.println("Public Key: " + Hex.toHex(key.getPubKey()));

        if (node != null && node.isRunning()) {
            System.out.println("\nNote: Restart node to use new key.");
        }
    }

    private void ddosDefenceMenu() {
        if (node == null) {
            System.out.println("Start node first.");
            return;
        }

        DDoSConfig ddosConfig = node.getConfig().getDdosConfig();
        System.out.println("\n=== DDoS Defence ===");
        System.out.println("Current status: " + (ddosConfig.isEnabled() ? "ON" : "OFF"));
        System.out.println("Base difficulty: " + ddosConfig.getBaseDifficulty());
        System.out.println("Max difficulty: " + ddosConfig.getMaxDifficulty());
        System.out.println("Rate limit: " + ddosConfig.getMaxPacketsPerSecondPerIp() + " pkt/s per IP");
        System.out.println();

        try {
            System.out.print("Toggle DDoS defence? (y/N): ");
            String input = br.readLine().trim().toLowerCase();
            if ("y".equals(input) || "yes".equals(input)) {
                boolean newState = !ddosConfig.isEnabled();
                ddosConfig.setEnabled(newState);
                System.out.println("DDoS defence is now " + (newState ? "ON" : "OFF") + " (effective immediately)");
            } else {
                System.out.println("No change.");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void shutdown() {
        if (node != null && node.isRunning()) {
            node.stop();
        }
        System.out.println("Goodbye!");
    }

    // NodeEventListener implementation

    @Override
    public void onRequestReceived(String peerId, long connectionId, long requestId, String serviceName, byte[] data) {
        System.out.println("\n[REQUEST] From " + peerId + " (conn=" + connectionId + ") - Service: " + serviceName);
        if (data != null) {
            System.out.println("  Data: " + new String(data));
        }
        System.out.println("  RequestId: " + requestId + " (use respond command to reply)");
    }

    @Override
    public void onPingComplete(String peerId, long rttMs) {
        System.out.println("\n[PONG] From " + peerId + " - RTT: " + rttMs + "ms");
    }

    @Override
    public void onPeerConnected(String peerId, long connectionId) {
        System.out.println("\n[CONNECTED] " + peerId + " (conn=" + connectionId + ")");
    }

    @Override
    public void onPeerDisconnected(String peerId, long connectionId) {
        System.out.println("\n[DISCONNECTED] " + peerId + " (conn=" + connectionId + ")");
    }

    @Override
    public void onError(String peerId, int errorCode, String message) {
        System.out.println("\n[ERROR] " + (peerId != null ? peerId + " - " : "") + "Code " + errorCode + ": " + message);
    }

    // Notify Event Listeners

    @Override
    public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
        String typeStr = switch (dataType) {
            case NotifyMessage.DATA_TYPE_RAW -> "raw";
            case NotifyMessage.DATA_TYPE_JSON -> "json";
            case NotifyMessage.DATA_TYPE_PROTOBUF -> "protobuf";
            case NotifyMessage.DATA_TYPE_MSGPACK -> "msgpack";
            default -> "type-" + dataType;
        };
        System.out.println("\n[NOTIFY] From " + peerId + " (ID: " + messageId + ", type: " + typeStr + ")");
        System.out.println("  Data (" + data.length + " bytes): " + new String(data));

        // Auto-add unknown peer
        if (node.getPeer(peerId) == null) {
            String alias = peerId.length() > 4 ? peerId.substring(peerId.length() - 4) : peerId;
            if (node.addConnectedPeer(peerId, alias)) {
                System.out.println("[System] Auto-added new peer " + peerId + " as '" + alias + "'");
            }
        }
    }

    @Override
    public void onNotifyAck(String peerId, long messageId, long rttMs) {
        System.out.println("\n[NOTIFY ACK] Message " + messageId + " delivered to " + peerId + " (RTT: " + rttMs + "ms)");
    }
}
