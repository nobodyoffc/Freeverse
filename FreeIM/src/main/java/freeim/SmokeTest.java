package freeim;

import core.crypto.KeyTools;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import org.bitcoinj.core.ECKey;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end smoke test for FreeIM-on-FUDP after the v1 wire-format hardening.
 *
 * <p>Spawns two FudpNodes locally (Alice and Bob), wires up the FreeIM
 * components (ChatManager, FileTransfer) on both, and runs three checks:
 * <ol>
 *   <li>Chat: Alice sends a NOTIFY to Bob, Bob receives it and ACKs.</li>
 *   <li>Small file: Alice OFFERs a 32 KB file, Bob accepts, file transfers
 *       and SHA-256 matches.</li>
 *   <li>Larger file: 4 MB transfer to exercise streaming / fragmentation.</li>
 * </ol>
 *
 * <p>Exits 0 on full pass, non-zero on any failure. Intended to be run as:
 * <pre>
 *   mvn -pl FreeIM exec:java -Dexec.mainClass=freeim.SmokeTest
 * </pre>
 * or directly:
 * <pre>
 *   java -cp FreeIM/target/classes:FreeIM/target/dependency/* freeim.SmokeTest
 * </pre>
 */
public class SmokeTest {

    private static final long CHAT_TIMEOUT_MS = 10_000L;
    private static final long FILE_OFFER_TIMEOUT_MS = 15_000L;
    private static final long FILE_XFER_TIMEOUT_MS = 60_000L;

    public static void main(String[] args) throws Exception {
        // Quiet logback unless --verbose is passed.
        boolean verbose = args.length > 0 && "--verbose".equals(args[0]);
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(verbose ? ch.qos.logback.classic.Level.INFO : ch.qos.logback.classic.Level.WARN);

        Path tmpRoot = Files.createTempDirectory("freeim-smoke-");
        System.out.println("[smoke] tmp = " + tmpRoot);

        Endpoint alice = new Endpoint("alice", tmpRoot, /*port*/ 0);
        Endpoint bob = new Endpoint("bob", tmpRoot, /*port*/ 0);

        try {
            alice.start();
            bob.start();
            System.out.printf("[smoke] alice fid=%s port=%d%n", alice.fid, alice.actualPort);
            System.out.printf("[smoke] bob   fid=%s port=%d%n", bob.fid, bob.actualPort);

            // Each side adds the other as a peer, by FID + (host,port).
            alice.node.addPeer(bob.fid, bob.node.getLocalPublicKey(), "127.0.0.1", bob.actualPort);
            bob.node.addPeer(alice.fid, alice.node.getLocalPublicKey(), "127.0.0.1", alice.actualPort);

            int passed = 0, failed = 0;

            if (testChat(alice, bob)) passed++; else failed++;
            if (testFileTransfer(alice, bob, "small.bin", 32 * 1024)) passed++; else failed++;
            if (testFileTransfer(alice, bob, "medium.bin", 4 * 1024 * 1024)) passed++; else failed++;

            System.out.printf("%n[smoke] result: %d passed, %d failed%n", passed, failed);
            if (failed != 0) System.exit(1);
        } finally {
            alice.stop();
            bob.stop();
            // Best-effort tmp cleanup; errors are non-fatal.
            try { deleteTree(tmpRoot.toFile()); } catch (Exception ignored) {}
        }
    }

    /* ---------------- Chat ---------------- */

    private static boolean testChat(Endpoint alice, Endpoint bob) {
        System.out.println("[smoke] ---- chat ----");
        String text = "hello-from-alice-" + System.currentTimeMillis();
        CompletableFuture<String> received = new CompletableFuture<>();
        bob.chat.setListener(msg -> {
            if (msg.peerId.equals(alice.fid) && msg.incoming) {
                received.complete(msg.text);
            }
        });
        try {
            alice.chat.send(bob.fid, text);
            String got = received.get(CHAT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!text.equals(got)) {
                System.out.println("  [FAIL] mismatch: sent=\"" + text + "\" recv=\"" + got + "\"");
                return false;
            }
            System.out.println("  [pass] chat round-trip OK");
            return true;
        } catch (Exception e) {
            System.out.println("  [FAIL] chat: " + e.getMessage());
            return false;
        }
    }

    /* ---------------- File transfer ---------------- */

    private static boolean testFileTransfer(Endpoint sender, Endpoint receiver,
                                            String name, int size) {
        System.out.printf("[smoke] ---- file (%s, %d bytes) ----%n", name, size);
        File src = new File(sender.tmp.toFile(), name);
        try {
            byte[] payload = new byte[size];
            new Random(0xCAFEBABEL ^ size).nextBytes(payload);
            try (FileOutputStream fos = new FileOutputStream(src)) {
                fos.write(payload);
            }
            String wantHash = sha256(payload);

            // Receiver auto-accepts any incoming offer for this test.
            receiver.autoAccept = true;

            sender.fileTransfer.sendFile(receiver.fid, src);

            // Wait for the receiver's downloads dir to contain a file with
            // matching size (the FileTransfer code names files based on the
            // offer; filename collisions get _1 suffixes).
            File got = waitForReceivedFile(receiver.downloads.toFile(), name, size,
                    FILE_OFFER_TIMEOUT_MS + FILE_XFER_TIMEOUT_MS);
            if (got == null) {
                System.out.println("  [FAIL] receiver never saw the file");
                return false;
            }
            byte[] gotBytes = Files.readAllBytes(got.toPath());
            String gotHash = sha256(gotBytes);
            if (!wantHash.equals(gotHash)) {
                System.out.println("  [FAIL] hash mismatch: want=" + wantHash + " got=" + gotHash);
                return false;
            }
            System.out.println("  [pass] file transfer OK (" + got.getName() + ", " + gotBytes.length + " B, sha256=" + gotHash.substring(0, 12) + "..)");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("  [FAIL] file transfer: " + e.getMessage());
            return false;
        } finally {
            receiver.autoAccept = false;
        }
    }

    private static File waitForReceivedFile(File dir, String hint, long expectedSize, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            File[] kids = dir.listFiles();
            if (kids != null) {
                for (File f : kids) {
                    if (f.isFile() && f.getName().startsWith(hint.split("\\.")[0]) && f.length() == expectedSize) {
                        // Give a brief moment for the writer to finish.
                        Thread.sleep(50);
                        if (f.length() == expectedSize) return f;
                    }
                }
            }
            Thread.sleep(100);
        }
        return null;
    }

    /* ---------------- Endpoint scaffolding ---------------- */

    private static final class Endpoint implements NodeEventListener {
        final String label;
        final Path tmp;
        final Path msgs;
        final Path downloads;
        final byte[] prikey;
        final String fid;
        FudpNode node;
        ChatManager chat;
        FileTransfer fileTransfer;
        int actualPort;
        volatile boolean autoAccept = false;

        Endpoint(String label, Path tmpRoot, int port) throws Exception {
            this.label = label;
            this.tmp = Files.createDirectory(tmpRoot.resolve(label));
            this.msgs = Files.createDirectory(tmp.resolve("messages"));
            this.downloads = Files.createDirectory(tmp.resolve("downloads"));
            this.prikey = new ECKey().getPrivKeyBytes();
            this.fid = KeyTools.prikeyToFid(prikey);
        }

        void start() throws Exception {
            // port=0 → let the OS pick an ephemeral port.
            // Use a random high port instead since NodeConfig.setPort(int) wants
            // an int and the underlying DatagramChannel binds to 0 via NodeConfig.
            int port = 30_000 + new SecureRandom().nextInt(20_000);
            NodeConfig cfg = new NodeConfig()
                    .setPort(port)
                    .setDataDir(tmp.toString())
                    .setMaxPacketSize(8000)
                    .setSocketBufferSize(4 * 1024 * 1024)
                    .setRequestTimeoutMs(120_000);
            node = new FudpNode(prikey, cfg);
            node.setEventListener(this);
            node.start();
            actualPort = node.getConfig().getPort();
            chat = new ChatManager(node, new MessageStore(msgs), fid);
            fileTransfer = new FileTransfer(node, downloads);
        }

        void stop() {
            if (node != null) {
                try { node.stop(); } catch (Exception ignored) {}
            }
        }

        @Override
        public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
            chat.onNotifyReceived(peerId, messageId, dataType, data);
        }

        @Override
        public void onNotifyAck(String peerId, long messageId, long rttMs) {
            chat.onNotifyAck(peerId, messageId, rttMs);
        }

        @Override
        public void onRequestReceived(String peerId, long connectionId, long requestId,
                                      String serviceName, byte[] data) {
            // FileTransfer.onRequestReceived returns true if it handled the message
            // (file-offer or file-data). For file-offer we additionally auto-accept
            // when this endpoint is the receiver in a test.
            boolean handled = fileTransfer.onRequestReceived(peerId, connectionId, requestId, serviceName, data);
            if (handled && "file-offer".equals(serviceName) && autoAccept) {
                fileTransfer.acceptOffer(requestId);
            }
        }

        @Override
        public void onStreamAssemblyProgress(String peerId, long streamId, long bytesAssembled) {
            fileTransfer.onAssemblyProgress(peerId, streamId, bytesAssembled);
        }

        @Override public void onPeerConnected(String peerId, long connectionId) {}
        @Override public void onPeerDisconnected(String peerId, long connectionId) {}
        @Override public void onPingComplete(String peerId, long rttMs) {}
        @Override public void onError(String peerId, int errorCode, String message) {
            // Surface unexpected errors so failures are diagnosable.
            System.err.println("  [" + label + "] onError peer=" + peerId + " code=" + errorCode + " msg=" + message);
        }
    }

    /* ---------------- helpers ---------------- */

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(data);
        StringBuilder sb = new StringBuilder(h.length * 2);
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteTree(k);
        }
        f.delete();
    }
}
