package fudp.crypto;

import core.crypto.CryptoDataByte;
import core.crypto.EncryptType;
import core.crypto.KeyTools;
import fudp.packet.Packet;

/**
 * Packet encryption and decryption handler.
 * 
 * Uses only AsyTwoWay (ECDH) encryption for all packets.
 * This simplifies the protocol by eliminating symmetric key negotiation.
 */
public class PacketCrypto {

    private final CryptoManager cryptoManager;

    public PacketCrypto(CryptoManager cryptoManager) {
        this.cryptoManager = cryptoManager;
    }

    /**
     * Encrypt a packet using AsyTwoWay (ECDH) mode.
     * 
     * @param packet The packet with frames to encrypt
     * @param peerId The peer's FID (for logging)
     * @param peerPubkey The peer's public key
     * @return The encrypted packet
     */
    public Packet encryptPacket(Packet packet, String peerId, byte[] peerPubkey) {
        // Include our session epoch for peer restart detection
        byte[] plaintext = packet.serializeFrames(cryptoManager.getSessionEpoch());

        // Always use AsyTwoWay encryption
        CryptoDataByte cryptoData = cryptoManager.encryptAsyTwoWay(plaintext, peerPubkey);

        // Convert CryptoDataByte to bundle format
        byte[] bundle = cryptoData.toBundle();
        packet.setEncryptedPayload(bundle);

        return packet;
    }

    /**
     * Decrypt a packet.
     * 
     * @param packet The packet with encrypted payload
     * @return The sender's FID
     * @throws RuntimeException if decryption fails
     */
    public String decryptPacket(Packet packet) {
        byte[] bundle = packet.getEncryptedPayload();

        // Parse the bundle
        CryptoDataByte cryptoData = CryptoDataByte.fromBundle(bundle);
        if (cryptoData == null) {
            throw new RuntimeException("Failed to parse crypto bundle");
        }

        // Only AsyTwoWay is supported
        if (cryptoData.getType() != EncryptType.AsyTwoWay) {
            throw new RuntimeException("Unsupported encrypt type: " + cryptoData.getType() + 
                    ". Only AsyTwoWay is supported.");
        }

        // Extract sender's public key and decrypt
        byte[] senderPubkey = cryptoData.getPubkeyA();
        String senderId = KeyTools.pubkeyToFchAddr(senderPubkey);
        byte[] plaintext = cryptoManager.decryptAsyTwoWay(cryptoData);

        // Store sender's public key in packet for connection management
        packet.setPeerPublicKey(senderPubkey);

        // Parse the decrypted frames
        packet.parseFrames(plaintext);

        return senderId;
    }

    /**
     * Get the crypto manager.
     */
    public CryptoManager getCryptoManager() {
        return cryptoManager;
    }
}
