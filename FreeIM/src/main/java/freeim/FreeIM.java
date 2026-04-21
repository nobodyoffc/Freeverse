package freeim;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import core.crypto.KeyTools;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import org.slf4j.LoggerFactory;
import ui.Menu;
import utils.Hex;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FreeIM — Simple P2P Chat & File Transfer on FUDP.
 *
 * Usage:
 *   java -jar FreeIM.jar
 *   java -jar FreeIM.jar --prikey <hex> --port <port> --logs
 */
public class FreeIM implements NodeEventListener {

    private static final String VERSION = "1.0.0";

    private final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    private final Path baseDir = Paths.get(System.getProperty("user.home"), ".freeim");
    private FudpNode node;
    private ChatManager chat;
    private FileTransfer fileTransfer;
    private PeerManager peers;
    private AccountManager accountManager;
    private String localFid;

    public static void main(String[] args) {
        configureLogging(hasFlag(args, "--logs"));
        new FreeIM().run(args);
    }

    private static void configureLogging(boolean enableLogs) {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(enableLogs ? Level.DEBUG : Level.OFF);
    }

    public void run(String[] args) {
        System.out.println();
        System.out.println("  FreeIM v" + VERSION + " — P2P Chat & File Transfer");
        System.out.println("  =========================================");
        System.out.println();

        try {
            // Parse arguments
            String prikeyHex = getArg(args, "--prikey");
            String portStr = getArg(args, "--port");

            byte[] prikey;
            int port;

            if (prikeyHex != null && !prikeyHex.isEmpty()) {
                // Direct prikey mode (backward compatible, no password)
                prikey = Hex.fromHex(prikeyHex);
                port = portStr != null ? Integer.parseInt(portStr) : 9000;
                localFid = KeyTools.prikeyToFid(prikey);
            } else {
                // Password-based account management
                accountManager = new AccountManager(baseDir, br);
                AccountManager.SelectedAccount selected = accountManager.login();
                if (selected == null) {
                    System.out.println("  Login cancelled.");
                    return;
                }
                prikey = selected.prikey();
                port = portStr != null ? Integer.parseInt(portStr) : selected.port();
                localFid = selected.fid();
            }

            System.out.println("  Your FID: " + localFid);

            // Data directories
            Path fidDir = baseDir.resolve(localFid);
            Path msgDir = fidDir.resolve("messages");
            Path dlDir = fidDir.resolve("downloads");

            // Start FUDP node
            startNode(prikey, port, fidDir);

            // Initialize components
            MessageStore store = new MessageStore(msgDir);
            chat = new ChatManager(node, store, localFid);
            fileTransfer = new FileTransfer(node, dlDir);
            peers = new PeerManager(node, br);

            // Main menu
            mainMenu();

        } catch (Exception e) {
            System.err.println("Fatal: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (node != null) node.stop();
            System.out.println("  Goodbye.");
        }
    }

    private void startNode(byte[] prikey, int port, Path dataDir) throws Exception {
        if (node != null && node.isRunning()) {
            node.stop();
        }
        System.out.printf("  Starting FUDP on port %d...%n", port);
        NodeConfig config = new NodeConfig()
                .setPort(port)
                .setDataDir(dataDir.toString())
                .setMaxPacketSize(8000)                     // 8KB MTU for faster file transfer (vs 1350 default)
                .setSocketBufferSize(4 * 1024 * 1024)       // 4MB socket buffers to reduce packet loss
                .setRequestTimeoutMs(120_000);               // 2 min (file offers need user interaction)
        node = new FudpNode(prikey, config);
        node.setEventListener(this);
        node.start();
        System.out.println("  Node started. Public key: " + Hex.toHex(node.getLocalPublicKey()));
        System.out.println();
    }

    private void mainMenu() {
        int port = node.getConfig().getPort();
        Menu menu = new Menu("FreeIM [" + localFid + ":" + port + "]", () -> {});
        menu.add("Peers", this::peerMenu);
        menu.add("Chat", this::startChat);
        menu.add("Send File", this::sendFileMenu);
        menu.add("Pending File Offers", this::fileOffersMenu);
        menu.add("Ping", () -> peers.pingPeer());
        menu.add("Status", this::showStatus);
        if (accountManager != null) {
            menu.add("Settings", this::settingsMenu);
        }
        menu.showAndSelect(br);
    }

    // ========== Menus ==========

    private void peerMenu() {
        Menu menu = new Menu("Peers");
        menu.add("Add Peer", () -> peers.addPeer());
        menu.add("List Peers", () -> peers.listPeers());
        menu.add("Set Alias", () -> peers.setAlias());
        menu.add("Remove Peer", () -> peers.removePeer());
        menu.showAndSelect(br);
    }

    private void settingsMenu() {
        Menu menu = new Menu("Settings");
        menu.add("Show Prikey", this::showPrikey);
        menu.add("Change Password", this::changePassword);
        menu.add("Reset Port", this::resetPort);
        menu.add("Add Account", this::addAccount);
        menu.add("Import Account", this::importAccountMenu);
        menu.add("Remove Account", this::removeAccount);
        menu.showAndSelect(br);
    }

    private void showPrikey() {
        try {
            byte[] prikey = accountManager.decryptPrikey(accountManager.getCurrentAccount());
            String hex = Hex.toHex(prikey);
            System.out.println("  Hex:         " + hex);
            System.out.println("  Base58Check: " + KeyTools.prikey32To38WifCompressed(hex));
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private void changePassword() {
        try {
            accountManager.changePassword();
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private void resetPort() {
        try {
            int newPort = accountManager.resetPort();
            if (newPort > 0) {
                System.out.println("  Restarting node on port " + newPort + "...");
                byte[] prikey = accountManager.decryptPrikey(accountManager.getCurrentAccount());
                Path fidDir = baseDir.resolve(localFid);
                startNode(prikey, newPort, fidDir);
                // Re-initialize components with new node
                peers = new PeerManager(node, br);
                System.out.println("  Node restarted.");
            }
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private void addAccount() {
        try {
            AccountManager.SelectedAccount newAcct = accountManager.addAccount();
            if (newAcct != null) {
                System.out.println("  Account created: " + newAcct.fid());
                System.out.println("  Restart FreeIM to switch to the new account.");
            }
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private void importAccountMenu() {
        try {
            System.out.print("  Port (default 9000): ");
            String portStr = br.readLine().trim();
            int port = portStr.isEmpty() ? 9000 : Integer.parseInt(portStr);
            AccountManager.SelectedAccount imported = accountManager.importAccount(port);
            if (imported != null) {
                System.out.println("  Restart FreeIM to switch to the imported account.");
            }
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private void removeAccount() {
        try {
            if (accountManager.removeCurrentAccount(baseDir)) {
                System.out.println("  Please restart FreeIM.");
                System.exit(0);
            }
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private void startChat() {
        try {
            String peerId = peers.promptSelectPeer("Chat with");
            if (peerId == null) return;
            chat.chatSession(peerId, br);
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private void sendFileMenu() {
        try {
            String peerId = peers.promptSelectPeer("Send file to");
            if (peerId == null) return;

            System.out.print("  File path: ");
            String path = br.readLine().trim();
            if (path.isEmpty()) return;

            if (path.startsWith("~")) {
                path = System.getProperty("user.home") + path.substring(1);
            }

            fileTransfer.sendFile(peerId, new File(path));
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private void fileOffersMenu() {
        fileTransfer.listPendingOffers();
        try {
            System.out.print("  Accept (a) / Reject (r) / Back (Enter): ");
            String input = br.readLine().trim().toLowerCase();
            if (input.isEmpty()) return;

            System.out.print("  Request ID: ");
            long requestId = Long.parseLong(br.readLine().trim());

            if ("a".equals(input)) {
                fileTransfer.acceptOffer(requestId);
            } else if ("r".equals(input)) {
                fileTransfer.rejectOffer(requestId);
            }
        } catch (Exception e) {
            // Back to menu
        }
    }

    private void showStatus() {
        System.out.println("\n  === FreeIM Status ===");
        System.out.println("  FID:     " + localFid);
        System.out.println("  Port:    " + node.getConfig().getPort());
        System.out.println("  Running: " + node.isRunning());
        System.out.println("  Peers:   " + node.listPeers().size());
        System.out.println("  PubKey:  " + Hex.toHex(node.getLocalPublicKey()));
    }

    // ========== NodeEventListener ==========

    @Override
    public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
        chat.onNotifyReceived(peerId, messageId, dataType, data);
    }

    @Override
    public void onNotifyAck(String peerId, long messageId, long rttMs) {
        chat.onNotifyAck(peerId, messageId, rttMs);
    }

    @Override
    public void onRequestReceived(String peerId, long connectionId, long requestId, String serviceName, byte[] data) {
        if (fileTransfer.onRequestReceived(peerId, connectionId, requestId, serviceName, data)) {
            return;
        }
        System.out.printf("%n  [REQUEST] From %s, service=%s (requestId=%d)%n", shortFid(peerId), serviceName, requestId);
    }

    @Override
    public void onStreamAssemblyProgress(String peerId, long streamId, long bytesAssembled) {
        fileTransfer.onAssemblyProgress(peerId, streamId, bytesAssembled);
    }

    @Override
    public void onPeerConnected(String peerId, long connectionId) {
        System.out.println("\n  [+] " + shortFid(peerId) + " connected");
    }

    @Override
    public void onPeerDisconnected(String peerId, long connectionId) {
        System.out.println("\n  [-] " + shortFid(peerId) + " disconnected");
    }

    @Override
    public void onPingComplete(String peerId, long rttMs) {
        System.out.println("\n  [PONG] " + shortFid(peerId) + " — " + rttMs + "ms");
    }

    @Override
    public void onError(String peerId, int errorCode, String message) {
        System.out.println("\n  [ERR] " + (peerId != null ? shortFid(peerId) + " " : "") + errorCode + ": " + message);
    }

    // ========== Helpers ==========

    private String shortFid(String fid) {
        if (fid == null) return "?";
        return fid.length() > 10 ? fid.substring(0, 6) + ".." + fid.substring(fid.length() - 4) : fid;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) return true;
        }
        return false;
    }

    private static String getArg(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}
