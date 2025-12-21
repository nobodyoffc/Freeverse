package fudp.crypto;

import fudp.packet.Packet;
import fudp.packet.frames.StreamFrame;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for PacketCrypto to verify packet encryption/decryption
 */
public class PacketCryptoTest {

    private CryptoManager managerA;
    private CryptoManager managerB;
    private byte[] privateKeyA;
    private byte[] privateKeyB;

    @BeforeEach
    public void setUp() {
        // Generate valid secp256k1 keys
        ECKey keyA = new ECKey();
        ECKey keyB = new ECKey();

        privateKeyA = keyA.getPrivKeyBytes();
        privateKeyB = keyB.getPrivKeyBytes();

        managerA = new CryptoManager(privateKeyA);
        managerB = new CryptoManager(privateKeyB);

    }

    @Test
    public void testPacketEncryptDecrypt() {
        // Create a packet with a simple stream frame
        Packet packet = new Packet();
        packet.getHeader().setPacketNumber(1);
        packet.getHeader().setConnectionId(12345);

        // Constructor: (streamId, offset, data, fin)
        StreamFrame frame = new StreamFrame(1, 0, "Hello, World!".getBytes(), false);
        packet.addFrame(frame);

        // Get peer IDs
        String peerIdA = managerA.getLocalFid();
        String peerIdB = managerB.getLocalFid();

        System.out.println("=== Input ===");
        System.out.println("PeerA: " + peerIdA);
        System.out.println("PeerB: " + peerIdB);
        System.out.println("Frame data: Hello, World!");

        // A encrypts packet for B
        PacketCrypto cryptoA = new PacketCrypto(managerA);
        Packet encryptedPacket = cryptoA.encryptPacket(packet, peerIdB, managerB.getLocalPublicKey());

        System.out.println("\n=== After Encryption ===");
        System.out.println("Encrypted payload length: " + encryptedPacket.getEncryptedPayload().length);

        // Serialize to bytes (like sending over network)
        byte[] networkData = encryptedPacket.toBytes();
        System.out.println("Network data length: " + networkData.length);

        // B receives and parses packet (like Protocol.handleIncomingPacket)
        PacketCrypto cryptoB = new PacketCrypto(managerB);
        Packet receivedPacket = Packet.fromBytes(networkData);

        String senderId = cryptoB.decryptPacket(receivedPacket);

        System.out.println("\n=== After Decryption ===");
        System.out.println("Sender ID: " + senderId);
        System.out.println("Frames count: " + receivedPacket.getFrames().size());

        assertEquals(peerIdA, senderId, "Sender should be A");
        assertEquals(1, receivedPacket.getFrames().size(), "Should have 1 frame");

        StreamFrame decodedFrame = (StreamFrame) receivedPacket.getFrames().get(0);
        String decodedData = new String(decodedFrame.getData());
        System.out.println("Decoded data: " + decodedData);

        assertEquals("Hello, World!", decodedData, "Frame data should match");
    }
}
