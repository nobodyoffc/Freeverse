package fudp;

import core.crypto.KeyTools;
import fudp.handler.FileHandler;
import fudp.handler.RelayHandler;
import fudp.message.*;
import fudp.node.*;
import fudp.security.DDoSConfig;
import org.bitcoinj.core.ECKey;
import ui.Menu;
import utils.Hex;

import java.io.*;
import java.util.List;
import java.util.Map;
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
        mainMenu.add("Send Chat", this::sendChat);
        mainMenu.add("Send Bytes", this::sendBytesMenu);
        mainMenu.add("Relay Message", this::relayMenu);
        mainMenu.add("File Transfer", this::fileTransferMenu);
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

    private void sendChat() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Peer FID or alias: ");
            String peer = br.readLine().trim();

            System.out.print("Message: ");
            String message = br.readLine().trim();

            if (message.isEmpty()) {
                System.out.println("Empty message, not sent.");
                return;
            }

            long msgId = node.generateMessageId();

            node.sendChatWithAck(peer, message, msgId);
            System.out.println("Message sent to " + peer + " (ID: " + msgId + ")");

        } catch (Exception e) {
            System.out.println("Error sending chat: " + e.getMessage());
        }
    }

    // Bytes Transfer Menu

    private void sendBytesMenu() {
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
            byte[] data = dataStr.getBytes();

            System.out.print("Request ACK? (y/N): ");
            String ackStr = br.readLine().trim().toLowerCase();
            boolean wantAck = "y".equals(ackStr) || "yes".equals(ackStr);

            long msgId;
            if (wantAck) {
                msgId = node.sendBytesWithAck(peer, data, dataType);
                System.out.println("Bytes sent to " + peer + " (ID: " + msgId + ", waiting for ACK)");
            } else {
                msgId = node.sendBytes(peer, data, dataType);
                System.out.println("Bytes sent to " + peer + " (ID: " + msgId + ")");
            }

        } catch (Exception e) {
            System.out.println("Error sending bytes: " + e.getMessage());
        }
    }

    // Relay Menu

    private void relayMenu() {
        Menu relayMenuOptions = new Menu("Relay Message");
        relayMenuOptions.add("Send Chat via Relay", this::sendViaRelay);
        relayMenuOptions.add("Send File via Relay", this::sendFileViaRelay);
        relayMenuOptions.add("Add Peer by FID", this::addPeerByFid);
        relayMenuOptions.add("Relay Statistics", this::showRelayStats);

        relayMenuOptions.showAndSelect(br);
    }

    private void sendFileViaRelay() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.println("\n=== Send File via Relay ===");
            System.out.println("Note: File transfer via relay reveals your identity to the recipient");
            System.out.println("      (required for bidirectional communication).\n");

            System.out.print("Relay node FID or alias: ");
            String relayPeer = br.readLine().trim();

            System.out.print("Target FID (final destination): ");
            String targetFid = br.readLine().trim();

            System.out.print("File path: ");
            String filePath = br.readLine().trim();

            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("File not found: " + filePath);
                return;
            }
            if (!file.isFile()) {
                System.out.println("Not a file: " + filePath);
                return;
            }

            // Check file size against relay payload limit
            if (file.length() > 10 * 1024 * 1024) { // 10MB warning
                System.out.println("Warning: Large files may take a long time via relay.");
                System.out.print("Continue? (Y/n): ");
                String confirm = br.readLine().trim().toLowerCase();
                if ("n".equals(confirm) || "no".equals(confirm)) {
                    System.out.println("Cancelled.");
                    return;
                }
            }

            System.out.println("Sending file offer via relay " + relayPeer + " to " + targetFid + "...");
            long sessionId = node.sendFileOfferViaRelay(relayPeer, targetFid, file);
            
            System.out.println("\nFile offer sent!");
            System.out.println("  File: " + file.getName() + " (" + formatBytes(file.length()) + ")");
            System.out.println("  Session ID: " + sessionId);
            System.out.println("  Waiting for recipient to accept...");

        } catch (Exception e) {
            System.out.println("Error sending file via relay: " + e.getMessage());
        }
    }

    private void sendViaRelay() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Relay node FID or alias: ");
            String relayPeer = br.readLine().trim();

            System.out.print("Target FID (final destination): ");
            String targetFid = br.readLine().trim();

            System.out.print("Message to send: ");
            String messageText = br.readLine().trim();

            if (messageText.isEmpty()) {
                System.out.println("Empty message, not sent.");
                return;
            }

            // Create a chat message as the inner payload
            ChatMessage innerMessage = new ChatMessage(messageText);
            innerMessage.setMessageId(node.generateMessageId());

            long relayMsgId = node.sendViaRelay(relayPeer, targetFid, innerMessage);
            System.out.println("Message relayed via " + relayPeer + " to " + targetFid);
            System.out.println("Relay message ID: " + relayMsgId);
            System.out.println("Note: Target will NOT know your identity (privacy-preserving).");

        } catch (Exception e) {
            System.out.println("Error sending via relay: " + e.getMessage());
        }
    }

    private void addPeerByFid() {
        if (node == null) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.println("\nAdd peer by FID (for relay testing)");
            System.out.println("Note: You'll need the target's host/port. Public key will be discovered automatically.");
            System.out.println();

            System.out.print("Peer FID: ");
            String peerId = br.readLine().trim();

            System.out.print("Host (IP or hostname): ");
            String host = br.readLine().trim();

            System.out.print("Port: ");
            int port = Integer.parseInt(br.readLine().trim());

            System.out.print("Alias (optional): ");
            String alias = br.readLine().trim();

            // We'll discover the public key when connecting
            // For now, add with null pubkey - it will be discovered on first connect
            System.out.println("Discovering public key from " + host + ":" + port + "...");
            
            try {
                byte[] pubkey = node.discoverPublicKey(host, port, 5000)
                        .get(5, TimeUnit.SECONDS);
                
                String discoveredFid = core.crypto.KeyTools.pubkeyToFchAddr(pubkey);
                if (!discoveredFid.equals(peerId)) {
                    System.out.println("Warning: Discovered FID (" + discoveredFid + ") differs from entered FID (" + peerId + ")");
                    System.out.print("Use discovered FID? (Y/n): ");
                    String confirm = br.readLine().trim().toLowerCase();
                    if (!"n".equals(confirm) && !"no".equals(confirm)) {
                        peerId = discoveredFid;
                    }
                }

                if (alias.isEmpty()) {
                    node.addPeer(peerId, pubkey, host, port);
                } else {
                    node.addPeer(peerId, pubkey, host, port, alias);
                }

                System.out.println("Peer added: " + peerId);
                System.out.println("Public Key: " + utils.Hex.toHex(pubkey));

            } catch (Exception e) {
                System.out.println("Failed to discover public key: " + e.getMessage());
                System.out.println("Peer not added. Make sure the target node is running.");
            }

        } catch (Exception e) {
            System.out.println("Error adding peer: " + e.getMessage());
        }
    }

    private void showRelayStats() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        RelayHandler.RelayStats stats = node.getRelayStats();
        System.out.println("\n=== Relay Statistics ===");
        System.out.println("Relayed Messages: " + stats.relayedMessages());
        System.out.println("Relay Failures:   " + stats.failures());
        System.out.println("ACKs Received:    " + stats.acks());
        System.out.println("Pending Relays:   " + stats.pendingCount());
    }

    // File Transfer Menu

    private void fileTransferMenu() {
        Menu fileMenu = new Menu("File Transfer");
        fileMenu.add("Send File", this::sendFile);
        fileMenu.add("View Pending Offers", this::viewPendingOffers);
        fileMenu.add("Accept File", this::acceptFile);
        fileMenu.add("Reject File", this::rejectFile);
        fileMenu.add("Cancel Transfer", this::cancelTransfer);

        fileMenu.showAndSelect(br);
    }

    private void sendFile() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Peer FID or alias: ");
            String peer = br.readLine().trim();

            System.out.print("File path: ");
            String filePath = br.readLine().trim();

            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("File not found: " + filePath);
                return;
            }
            if (!file.isFile()) {
                System.out.println("Not a file: " + filePath);
                return;
            }

            System.out.println("Sending file: " + file.getName() + " (" + formatBytes(file.length()) + ")");
            String transferId = node.sendFile(peer, file);
            System.out.println("File offer sent. Transfer ID: " + transferId);
            System.out.println("Waiting for peer to accept...");

        } catch (Exception e) {
            System.out.println("Error sending file: " + e.getMessage());
        }
    }

    private void viewPendingOffers() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        Map<String, FileHandler.PendingOffer> offers = node.getPendingFileOffers();
        if (offers.isEmpty()) {
            System.out.println("No pending file offers.");
            return;
        }

        System.out.println("\n=== Pending File Offers ===");
        int i = 1;
        for (FileHandler.PendingOffer offer : offers.values()) {
            System.out.println(i + ". Transfer ID: " + offer.transferId);
            System.out.println("   From: " + offer.peerId);
            System.out.println("   File: " + offer.fileName);
            System.out.println("   Size: " + formatBytes(offer.fileSize));
            System.out.println("   Hash: " + offer.fileHash.substring(0, 16) + "...");
            System.out.println();
            i++;
        }
    }

    private void acceptFile() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            // Show pending offers first
            Map<String, FileHandler.PendingOffer> offers = node.getPendingFileOffers();
            if (offers.isEmpty()) {
                System.out.println("No pending file offers to accept.");
                return;
            }

            System.out.println("\n=== Pending File Offers ===");
            for (FileHandler.PendingOffer offer : offers.values()) {
                System.out.println("  [" + offer.transferId + "] " + offer.fileName +
                        " (" + formatBytes(offer.fileSize) + ") from " + offer.peerId);
            }
            System.out.println();

            System.out.print("Transfer ID to accept: ");
            String transferId = br.readLine().trim();

            System.out.print("Save directory (default: ./downloads): ");
            String saveDir = br.readLine().trim();
            if (saveDir.isEmpty()) {
                saveDir = "./downloads";
            }

            node.acceptFile(transferId, saveDir);
            System.out.println("File transfer accepted. Receiving file...");

        } catch (Exception e) {
            System.out.println("Error accepting file: " + e.getMessage());
        }
    }

    private void rejectFile() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            // Show pending offers first
            Map<String, FileHandler.PendingOffer> offers = node.getPendingFileOffers();
            if (offers.isEmpty()) {
                System.out.println("No pending file offers to reject.");
                return;
            }

            System.out.println("\n=== Pending File Offers ===");
            for (FileHandler.PendingOffer offer : offers.values()) {
                System.out.println("  [" + offer.transferId + "] " + offer.fileName +
                        " (" + formatBytes(offer.fileSize) + ") from " + offer.peerId);
            }
            System.out.println();

            System.out.print("Transfer ID to reject: ");
            String transferId = br.readLine().trim();

            System.out.print("Rejection reason (optional): ");
            String reason = br.readLine().trim();

            node.rejectFile(transferId, reason.isEmpty() ? "User rejected" : reason);
            System.out.println("File transfer rejected.");

        } catch (Exception e) {
            System.out.println("Error rejecting file: " + e.getMessage());
        }
    }

    private void cancelTransfer() {
        if (node == null || !node.isRunning()) {
            System.out.println("Start node first.");
            return;
        }

        try {
            System.out.print("Transfer ID to cancel: ");
            String transferId = br.readLine().trim();

            System.out.print("Cancellation reason (optional): ");
            String reason = br.readLine().trim();

            node.cancelTransfer(transferId, reason.isEmpty() ? "User cancelled" : reason);
            System.out.println("Transfer cancelled.");

        } catch (Exception e) {
            System.out.println("Error cancelling transfer: " + e.getMessage());
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
    public void onChatReceived(String peerId, long messageId, String message) {
        String msgIdStr = String.valueOf(messageId);
        String hexSuffix = msgIdStr.substring(msgIdStr.length()-4);
        System.out.println("\n[CHAT] From " + peerId + ": ["+"ID_"+hexSuffix +"] "  + message);

        // Auto-add unknown peer
        if (node.getPeer(peerId) == null) {
            String alias = peerId.length() > 4 ? peerId.substring(peerId.length() - 4) : peerId;
            if (node.addConnectedPeer(peerId, alias)) {
                System.out.println("[System] Auto-added new peer " + peerId + " as '" + alias + "'"+". IP: "+node.getPeer(peerId).getHost()+":"+node.getPeer(peerId).getPort());
            }
        }
    }

    @Override
    public void onChatAck(String peerId, long messageId, long rttMs) {
        System.out.println("\n[ACK] Message " + messageId + " delivered to " + peerId + " (RTT: " + rttMs + "ms)");
    }

    @Override
    public void onRequestReceived(String peerId, long requestId, String serviceName, byte[] data) {
        System.out.println("\n[REQUEST] From " + peerId + " - Service: " + serviceName);
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
    public void onPeerConnected(String peerId) {
        System.out.println("\n[CONNECTED] " + peerId);
    }

    @Override
    public void onPeerDisconnected(String peerId) {
        System.out.println("\n[DISCONNECTED] " + peerId);
    }

    @Override
    public void onError(String peerId, int errorCode, String message) {
        System.out.println("\n[ERROR] " + (peerId != null ? peerId + " - " : "") + "Code " + errorCode + ": " + message);
    }

    // File Transfer Event Listeners

    @Override
    public void onFileOfferReceived(String peerId, FileOffer offer) {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║                    FILE OFFER RECEIVED                      ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║ From: " + peerId);
        System.out.println("║ File: " + offer.getFileName());
        System.out.println("║ Size: " + formatBytes(offer.getFileSize()));
        System.out.println("║ Transfer ID: " + offer.getTransferId());
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║ Use 'File Transfer > Accept File' to receive              ║");
        System.out.println("║ Use 'File Transfer > Reject File' to decline              ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    @Override
    public void onFileProgress(String transferId, long transferred, long total) {
        int percent = (int) ((transferred * 100) / total);
        int barLength = 30;
        int filled = (int) ((percent / 100.0) * barLength);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filled) bar.append("█");
            else bar.append("░");
        }
        bar.append("]");
        System.out.print("\r[FILE] " + transferId + " " + bar + " " + percent + "% (" +
                formatBytes(transferred) + "/" + formatBytes(total) + ")");
        if (transferred >= total) {
            System.out.println();
        }
    }

    @Override
    public void onFileComplete(String transferId, String filePath) {
        System.out.println("\n[FILE COMPLETE] Transfer " + transferId + " finished successfully!");
        System.out.println("  Saved to: " + filePath);
    }

    @Override
    public void onFileError(String transferId, String error) {
        System.out.println("\n[FILE ERROR] Transfer " + transferId + ": " + error);
    }

    // Bytes Event Listeners

    @Override
    public void onBytesReceived(String peerId, long messageId, int dataType, byte[] data) {
        String typeStr = switch (dataType) {
            case BytesMessage.DATA_TYPE_RAW -> "raw";
            case BytesMessage.DATA_TYPE_JSON -> "json";
            case BytesMessage.DATA_TYPE_PROTOBUF -> "protobuf";
            case BytesMessage.DATA_TYPE_MSGPACK -> "msgpack";
            default -> "type-" + dataType;
        };
        System.out.println("\n[BYTES] From " + peerId + " (ID: " + messageId + ", type: " + typeStr + ")");
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
    public void onBytesAck(String peerId, long messageId, long rttMs) {
        System.out.println("\n[BYTES ACK] Message " + messageId + " delivered to " + peerId + " (RTT: " + rttMs + "ms)");
    }

    // Relay Event Listeners

    @Override
    public void onRelayedMessageReceived(String relayPeerId, AppMessage message) {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║              RELAYED MESSAGE RECEIVED                      ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║ Via Relay: " + relayPeerId);
        System.out.println("║ Origin: UNKNOWN (privacy-preserving)");
        printRelayedMessageContent(message);
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    @Override
    public void onRelayedMessageReceived(String relayPeerId, String senderFid, long sessionId, AppMessage message) {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║         IDENTIFIED RELAYED MESSAGE RECEIVED                ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║ Via Relay: " + relayPeerId);
        System.out.println("║ From: " + senderFid);
        System.out.println("║ Session: " + sessionId);
        printRelayedMessageContent(message);
        
        // Handle file-related messages
        if (message instanceof FileOfferMessage offer) {
            System.out.println("╠══════════════════════════════════════════════════════════╣");
            System.out.println("║ To accept: use Relay Message > (future: Accept Relayed File)");
            System.out.println("║ Transfer ID: " + offer.getTransferId());
            // Store pending offer for later accept/reject
            pendingRelayedOffers.put(offer.getTransferId(), 
                    new PendingRelayedOffer(relayPeerId, senderFid, sessionId, offer));
        }
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    private void printRelayedMessageContent(AppMessage message) {
        System.out.println("║ Message Type: " + message.getType());
        if (message instanceof ChatMessage chatMsg) {
            System.out.println("║ Content: " + chatMsg.getContent());
        } else if (message instanceof BytesMessage bytesMsg) {
            System.out.println("║ Data (" + bytesMsg.getData().length + " bytes): " + new String(bytesMsg.getData()));
        } else if (message instanceof FileOfferMessage offer) {
            System.out.println("║ File: " + offer.getFileName());
            System.out.println("║ Size: " + formatBytes(offer.getFileSize()));
            System.out.println("║ Transfer ID: " + offer.getTransferId());
        } else {
            System.out.println("║ Message ID: " + message.getMessageId());
        }
    }

    // Store pending relayed file offers
    private final Map<String, PendingRelayedOffer> pendingRelayedOffers = new java.util.concurrent.ConcurrentHashMap<>();

    private static class PendingRelayedOffer {
        final String relayPeerId;
        final String senderFid;
        final long sessionId;
        final FileOfferMessage offer;

        PendingRelayedOffer(String relayPeerId, String senderFid, long sessionId, FileOfferMessage offer) {
            this.relayPeerId = relayPeerId;
            this.senderFid = senderFid;
            this.sessionId = sessionId;
            this.offer = offer;
        }
    }

    @Override
    public void onRelayAck(long messageId, long rttMs) {
        System.out.println("\n[RELAY ACK] Message " + messageId + " delivered via relay (RTT: " + rttMs + "ms)");
    }

    @Override
    public void onRelayFailed(long messageId, int errorCode, String reason) {
        String errorDesc = RelayErrorCode.getDescription(errorCode);
        System.out.println("\n[RELAY FAILED] Message " + messageId + ": " + errorDesc);
        if (reason != null && !reason.isEmpty()) {
            System.out.println("  Details: " + reason);
        }
    }
}
