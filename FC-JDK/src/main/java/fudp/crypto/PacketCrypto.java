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
     * Includes both timestamp and session epoch in the payload.
     */
    public Packet encryptPacket(Packet packet, String peerId, byte[] peerPubkey) {
        return encryptPacket(packet, peerId, peerPubkey, true, true);
    }

    /**
     * Encrypt a packet using AsyTwoWay (ECDH) mode.
     *
     * @param packet The packet with frames to encrypt
     * @param peerId The peer's FID (for logging)
     * @param peerPubkey The peer's public key
     * @param includeTimestamp Whether to include the 8-byte timestamp
     * @param includeEpoch Whether to include the 8-byte session epoch
     * @return The encrypted packet
     */
    public Packet encryptPacket(Packet packet, String peerId, byte[] peerPubkey,
                                boolean includeTimestamp, boolean includeEpoch) {
        // Include our session epoch for peer restart detection.
        // serializeFrames also sets the HAS_TIMESTAMP / HAS_EPOCH flag bits
        // on the header — we must take the header bytes AFTER this call so
        // the AAD reflects what the receiver will see on the wire.
        byte[] plaintext = packet.serializeFrames(cryptoManager.getSessionEpoch(),
                includeTimestamp, includeEpoch);

        // F1: bind the 21-byte serialised header to the AEAD tag. Any
        // tampering with header bits between sender and receiver fails
        // the tag check and the packet is dropped.
        byte[] aad = packet.getHeader().toBytes();

        CryptoDataByte cryptoData = cryptoManager.encryptAsyTwoWay(plaintext, peerPubkey, aad);

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

        // F1: re-derive the AAD from the parsed header. The parser is the
        // inverse of header.toBytes() (round-trip is byte-for-byte
        // deterministic for valid packets), so this matches what the sender
        // bound. Any tampering on the wire will diverge here and fail the
        // AEAD tag below.
        byte[] aad = packet.getHeader().toBytes();

        // Extract sender's public key and decrypt
        byte[] senderPubkey = cryptoData.getPubkeyA();
        String senderId = KeyTools.pubkeyToFchAddr(senderPubkey);
        byte[] plaintext = cryptoManager.decryptAsyTwoWay(cryptoData, aad);

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
