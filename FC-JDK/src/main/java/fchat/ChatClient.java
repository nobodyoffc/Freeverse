package fchat;

import core.crypto.KeyTools;
import fapi.client.FapiClient;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.Menu;
import utils.Hex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Terminal-based chat client for Freeverse IM.
 * Supports P2P, Group, Team, and Room conversations
 * using the same wire protocol as the Android Freer app.
 *
 * Usage:
 *   java fchat.ChatClient
 *   java fchat.ChatClient --prikey <hex> --port <port>
 */
public class ChatClient {
    private static final Logger log = LoggerFactory.getLogger(ChatClient.class);

    private final BufferedReader br;
    private FudpNode fudpNode;
    private FapiClient fapiClient;
    private ImManagerCli imManager;
    private String liveFid;

    public ChatClient() {
        this.br = new BufferedReader(new InputStreamReader(System.in));
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.start(args);
    }

    public void start(String[] args) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════╗");
        System.out.println("║       Freeverse Chat Client           ║");
        System.out.println("╚═══════════════════════════════════════╝");
        System.out.println();

        try {
            // Parse arguments or prompt
            String prikeyHex = getArg(args, "--prikey");
            int port = 9000;
            String portStr = getArg(args, "--port");
            if (portStr != null) port = Integer.parseInt(portStr);

            if (prikeyHex == null) {
                System.out.print("Enter private key (hex): ");
                prikeyHex = br.readLine().trim();
            }

            if (prikeyHex.isEmpty()) {
                System.out.println("Private key is required. Exiting.");
                return;
            }

            byte[] prikey = Hex.fromHex(prikeyHex);
            liveFid = KeyTools.prikeyToFid(prikey);
            System.out.println("Your FID: " + liveFid);

            // Configure and start FUDP node
            System.out.printf("Starting FUDP node on port %d...%n", port);
            NodeConfig config = new NodeConfig()
                    .setPort(port)
                    .setDataDir("~/.freer_chat/" + liveFid);
            fudpNode = new FudpNode(prikey, config);
            fudpNode.start();
            System.out.println("FUDP node started.");

            // Initialize IM manager
            Path dataDir = Paths.get(System.getProperty("user.home"), ".freer_chat", liveFid, "im_data");
            imManager = new ImManagerCli(liveFid, fudpNode, dataDir);
            System.out.println("IM manager initialized.");

            // Optionally connect to FAPI service
            promptFapiSetup();

            // Main menu loop
            mainMenu();

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            log.error("Fatal error", e);
        } finally {
            shutdown();
        }
    }

    private void promptFapiSetup() {
        System.out.println();
        System.out.print("Connect to a FAPI service? (y/n): ");
        try {
            String answer = br.readLine().trim();
            if ("y".equalsIgnoreCase(answer)) {
                System.out.print("FAPI service peer FID: ");
                String peerFid = br.readLine().trim();
                System.out.print("FAPI service SID: ");
                String sid = br.readLine().trim();
                if (!peerFid.isEmpty() && !sid.isEmpty()) {
                    fapiClient = new FapiClient(fudpNode, peerFid, sid);
                    imManager.setFapiClient(fapiClient);
                    System.out.println("FAPI client connected.");
                }
            }
        } catch (IOException e) {
            log.warn("FAPI setup failed: {}", e.getMessage());
        }
    }

    // ========== Main Menu ==========

    private void mainMenu() {
        Menu menu = new Menu("Chat Client - " + shortFid(liveFid));
        menu.add("P2P Conversations", this::showP2pConversations);
        menu.add("Group Conversations", this::showGroupConversations);
        menu.add("Team Conversations", this::showTeamConversations);
        menu.add("Room Conversations", this::showRoomConversations);
        menu.add("New P2P Chat", this::newP2pChat);
        menu.add("Connect to Peer", this::connectToPeer);
        menu.add("Fetch DOCK Messages", this::fetchDock);
        menu.showAndSelect(br);
    }

    // ========== Conversation List Views ==========

    private void showP2pConversations() { showConversations(ImType.P2P, "P2P Conversations"); }
    private void showGroupConversations() { showConversations(ImType.SQUARE, "Group Conversations"); }
    private void showTeamConversations() { showConversations(ImType.TEAM, "Team Conversations"); }
    private void showRoomConversations() { showConversations(ImType.ROOM, "Room Conversations"); }

    private void showConversations(ImType type, String title) {
        List<Conversation> convs = imManager.getConversations(type);
        if (convs.isEmpty()) {
            System.out.println("\n  No " + type.name().toLowerCase() + " conversations yet.");
            System.out.println("  Use 'New P2P Chat' or connect to a group/team/room.");
            return;
        }

        System.out.println("\n  " + title);
        System.out.println("  ─────────────────────────────────────────");
        for (int i = 0; i < convs.size(); i++) {
            Conversation c = convs.get(i);
            String name = c.getDisplayName() != null ? c.getDisplayName() : c.getTargetId();
            int unread = c.getUnreadCount() != null ? c.getUnreadCount() : 0;
            String unreadTag = unread > 0 ? " [" + unread + " new]" : "";
            String lastMsg = c.getLastMessageContent() != null ? c.getLastMessageContent() : "";
            if (lastMsg.length() > 40) lastMsg = lastMsg.substring(0, 40) + "...";
            System.out.printf("  %2d. %s%s%n", i + 1, name, unreadTag);
            if (!lastMsg.isEmpty()) {
                System.out.printf("      %s%n", lastMsg);
            }
        }
        System.out.println("  ─────────────────────────────────────────");
        System.out.println("   0. Back");

        System.out.print("\n  Select conversation: ");
        try {
            String input = br.readLine().trim();
            int choice = Integer.parseInt(input);
            if (choice > 0 && choice <= convs.size()) {
                Conversation selected = convs.get(choice - 1);
                openChat(type, selected.getTargetId(),
                         selected.getDisplayName() != null ? selected.getDisplayName() : selected.getTargetId());
            }
        } catch (Exception e) {
            // Back to main menu
        }
    }

    // ========== New Chat ==========

    private void newP2pChat() {
        System.out.print("\n  Enter target FID: ");
        try {
            String targetFid = br.readLine().trim();
            if (targetFid.isEmpty()) return;

            System.out.print("  Display name (Enter to skip): ");
            String displayName = br.readLine().trim();
            if (displayName.isEmpty()) displayName = targetFid;

            openChat(ImType.P2P, targetFid, displayName);
        } catch (IOException e) {
            System.err.println("  Input error: " + e.getMessage());
        }
    }

    // ========== Connect to Peer ==========

    private void connectToPeer() {
        System.out.print("\n  Enter peer FID: ");
        try {
            String peerFid = br.readLine().trim();
            if (peerFid.isEmpty()) return;

            System.out.print("  Peer address (host:port): ");
            String addr = br.readLine().trim();
            if (addr.isEmpty()) return;

            String[] parts = addr.split(":");
            if (parts.length != 2) {
                System.out.println("  Invalid address. Use host:port format.");
                return;
            }

            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            fudpNode.addPeer(peerFid, null, host, port);
            System.out.printf("  Added peer %s at %s:%d%n", shortFid(peerFid), host, port);

            // Ping to verify
            try {
                Thread.sleep(1000);
                fudpNode.ping(peerFid, false);
                System.out.println("  Ping sent. Connection should establish shortly.");
            } catch (Exception e) {
                System.out.println("  Ping failed, but connection may still establish.");
            }
        } catch (Exception e) {
            System.err.println("  Error: " + e.getMessage());
        }
    }

    // ========== Fetch from DOCK ==========

    private void fetchDock() {
        if (fapiClient == null) {
            System.out.println("\n  FAPI client not configured. Set up FAPI first.");
            return;
        }

        System.out.print("\n  Enter target ID (your FID, group/team/room ID): ");
        try {
            String targetId = br.readLine().trim();
            if (targetId.isEmpty()) targetId = liveFid;

            System.out.print("  Message type (P2P/GROUP/TEAM/ROOM): ");
            String typeStr = br.readLine().trim().toUpperCase();
            ImType type = ImType.fromString(typeStr);
            if (type == null) type = ImType.P2P;

            System.out.printf("  Fetching messages from DOCK for %s...%n", targetId);
            int count = imManager.fetchFromDock(type, targetId);
            System.out.printf("  Fetched %d messages.%n", count);
        } catch (IOException e) {
            System.err.println("  Error: " + e.getMessage());
        }
    }

    // ========== Open Chat ==========

    private void openChat(ImType type, String targetId, String displayName) {
        ChatView chatView = new ChatView(imManager, type, targetId, displayName, br);
        chatView.run();
    }

    // ========== Shutdown ==========

    private void shutdown() {
        System.out.println("\nShutting down...");
        if (fudpNode != null) {
            try {
                fudpNode.stop();
            } catch (Exception e) {
                log.warn("Error stopping FUDP node", e);
            }
        }
        System.out.println("Goodbye.");
    }

    // ========== Helpers ==========

    private String shortFid(String fid) {
        if (fid == null) return "?";
        if (fid.length() > 12) return fid.substring(0, 6) + ".." + fid.substring(fid.length() - 4);
        return fid;
    }

    private static String getArg(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}
