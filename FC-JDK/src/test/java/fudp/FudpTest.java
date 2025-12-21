package fudp;

import core.crypto.KeyTools;
import fudp.connection.ConnectionManager;
import fudp.connection.PeerConnection;
import fudp.crypto.CryptoManager;
import fudp.packet.*;
import fudp.packet.frames.*;
import fudp.stream.Stream;
import fudp.util.ByteUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;

/**
 * Test class for FUDP protocol
 */
public class FudpTest {

    public static void main(String[] args) {
        try {
            testCryptoManager();
            testPacketSerialization();
            testProtocolBasics();

            System.out.println("\n=== All tests passed! ===");
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test CryptoManager encryption/decryption
     */
    private static void testCryptoManager() {
        System.out.println("\n=== Testing CryptoManager ===");

        // Generate two key pairs
        byte[] privKeyA = ByteUtils.randomBytes(32);
        byte[] pubKeyA = KeyTools.prikeyToPubkey(privKeyA);

        byte[] privKeyB = ByteUtils.randomBytes(32);
        byte[] pubKeyB = KeyTools.prikeyToPubkey(privKeyB);

        // Create crypto managers
        CryptoManager cryptoA = new CryptoManager(privKeyA);
        CryptoManager cryptoB = new CryptoManager(privKeyB);

        // Test AsyTwoWay encryption
        byte[] plaintext = "Hello FUDP!".getBytes();

        // A encrypts for B
        var encrypted = cryptoA.encryptAsyTwoWay(plaintext, pubKeyB);
        System.out.println("Encrypted data type: " + encrypted.getType());

        // B decrypts
        encrypted.setPubkeyA(pubKeyA);
        byte[] decrypted = cryptoB.decryptAsyTwoWay(encrypted);

        String result = new String(decrypted);
        assert result.equals("Hello FUDP!") : "AsyTwoWay decryption failed";
        System.out.println("AsyTwoWay encryption/decryption: PASSED");

        // Get FID
        String fidA = cryptoA.getLocalFid();
        String fidB = cryptoB.getLocalFid();
        System.out.println("FID A: " + fidA);
        System.out.println("FID B: " + fidB);

        // Clean up
        cryptoA.clear();
        cryptoB.clear();
    }

    /**
     * Test packet serialization
     */
    private static void testPacketSerialization() {
        System.out.println("\n=== Testing Packet Serialization ===");

        // Create a packet
        Packet packet = new Packet(12345678L, 1);

        // Add frames
        byte[] data = "Test data".getBytes();
        StreamFrame streamFrame = new StreamFrame(0, 0, data, false);
        packet.addFrame(streamFrame);

        // Serialize frames
        byte[] payload = packet.serializeFrames(1);
        System.out.println("Serialized payload length: " + payload.length);

        // Create header
        PacketHeader header = new PacketHeader();
        header.setVersion(1);
        header.setConnectionId(12345678L);
        header.setPacketNumber(1);
        byte[] headerBytes = header.toBytes();
        System.out.println("Header length: " + headerBytes.length);

        // Parse header back
        PacketHeader parsed = PacketHeader.fromBytes(headerBytes);
        assert parsed.getConnectionId() == 12345678L : "Connection ID mismatch";
        assert parsed.getPacketNumber() == 1 : "Packet number mismatch";
        System.out.println("Header serialization: PASSED");

        // Test ACK frame
        ArrayList<AckFrame.AckRange> ranges = new ArrayList<>();
        ranges.add(new AckFrame.AckRange(0, 5));  // Packets 95-100 (length = 5)
        ranges.add(new AckFrame.AckRange(9, 5)); // Gap of 9, packets 80-85 (length = 5)
        AckFrame ackFrame = new AckFrame(100, 1000, ranges);

        byte[] ackBytes = ackFrame.toBytes();
        System.out.println("ACK frame length: " + ackBytes.length);

        System.out.println("Packet serialization tests: PASSED");
    }

    /**
     * Test basic protocol operations
     */
    private static void testProtocolBasics() throws IOException {
        System.out.println("\n=== Testing Protocol Basics ===");

        // Generate key pair
        byte[] privateKey = ByteUtils.randomBytes(32);

        // Create protocol instance
        Protocol protocol = new Protocol(privateKey, 0,"./"); // Port 0 = auto-assign

        System.out.println("Local FID: " + protocol.getLocalFid());
        System.out.println("Local public key length: " + protocol.getLocalPublicKey().length);

        // Test connection manager
        ConnectionManager connManager = protocol.getConnectionManager();

        // Create a peer connection
        byte[] peerPrivKey = ByteUtils.randomBytes(32);
        byte[] peerPubKey = KeyTools.prikeyToPubkey(peerPrivKey);
        String peerId = KeyTools.pubkeyToFchAddr(peerPubKey);

        InetSocketAddress peerAddress = new InetSocketAddress("127.0.0.1", 12345);
        PeerConnection conn = connManager.getOrCreate(peerId, peerAddress);
        conn.setPeerPublicKey(peerPubKey);

        assert conn != null : "Connection should be created";
        assert conn.getPeerId().equals(peerId) : "Peer ID mismatch";
        System.out.println("Created connection to peer: " + peerId);

        // Open a stream
        Stream stream = conn.openStream();
        assert stream != null : "Stream should be created";
        System.out.println("Opened stream ID: " + stream.getStreamId());

        // Test stream data handling
        byte[] testData = "Stream test data".getBytes();
        stream.onDataReceived(0, testData, false);

        byte[] received = stream.poll();
        assert received != null : "Should receive data";
        assert new String(received).equals("Stream test data") : "Data mismatch";
        System.out.println("Stream data reception: PASSED");

        // Test flow control
        assert stream.canSend(1000) : "Should be able to send 1000 bytes";
        System.out.println("Flow control check: PASSED");

        // Stop protocol
        protocol.stop();
        System.out.println("Protocol basics tests: PASSED");
    }
}
