package fudp;

import core.crypto.CryptoDataByte;
import fudp.crypto.CryptoManager;
import fudp.crypto.PacketCrypto;
import fudp.packet.Packet;
import fudp.packet.PacketHeader;
import fudp.packet.frames.StreamFrame;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Negative-path tests for FUDP header AAD (F1).
 *
 * <p>These tests build a packet end-to-end through {@link PacketCrypto},
 * tamper with the wire bytes, and assert the receiver rejects with an
 * AEAD tag failure.
 *
 * <p>The "replay at new packet number" test is the most important: it
 * proves that a captured ciphertext cannot be re-played at a fresh
 * packet number, which was the worst pre-F1 attack — the receiver's
 * replay window saw a "new" PN, the AEAD ignored the header, and the
 * old payload was re-processed as fresh application data.
 */
class Phase3HeaderAadTest {

    private static byte[] buildEncryptedWire(CryptoManager sender, byte[] receiverPubkey,
                                             long connId, long pn, byte[] streamData) {
        Packet packet = new Packet(connId, pn);
        packet.addFrame(new StreamFrame(/*streamId*/ 4L, /*offset*/ 0L, streamData, /*fin*/ false));

        PacketCrypto pc = new PacketCrypto(sender);
        pc.encryptPacket(packet, /*peerId*/ "test", receiverPubkey, true, true);
        return packet.toBytes();
    }

    private static String tryDecrypt(CryptoManager receiver, byte[] wire) {
        Packet parsed = Packet.fromBytes(wire);
        PacketCrypto pc = new PacketCrypto(receiver);
        return pc.decryptPacket(parsed);
    }

    @Test
    void roundTripWithAadSucceeds() {
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        byte[] bobKey = new ECKey().getPrivKeyBytes();
        CryptoManager alice = new CryptoManager(aliceKey);
        CryptoManager bob = new CryptoManager(bobKey);

        byte[] wire = buildEncryptedWire(alice, bob.getLocalPublicKey(),
                /*connId*/ 0xCAFEBABEL, /*pn*/ 7L, "hello".getBytes());
        // Decrypt should succeed; payload is parsed back into a StreamFrame.
        Packet parsed = Packet.fromBytes(wire);
        new PacketCrypto(bob).decryptPacket(parsed);
        assertEquals(1, parsed.getFrames().size());
        assertTrue(parsed.getFrames().get(0) instanceof StreamFrame);
        StreamFrame sf = (StreamFrame) parsed.getFrames().get(0);
        assertArrayEquals("hello".getBytes(), sf.getData());
    }

    @Test
    void flippingAnyHeaderByteFailsTagCheck() {
        // For each of the 21 header bytes, flip one bit and confirm decrypt
        // rejects. Tampering with the version byte is rejected earlier (by
        // the version gate in Protocol), but at the PacketCrypto level all
        // 21 bytes are bound into AAD, so tag failure is the universal floor.
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        byte[] bobKey = new ECKey().getPrivKeyBytes();
        CryptoManager alice = new CryptoManager(aliceKey);
        CryptoManager bob = new CryptoManager(bobKey);

        byte[] base = buildEncryptedWire(alice, bob.getLocalPublicKey(),
                /*connId*/ 1L, /*pn*/ 1L, "payload".getBytes());

        for (int i = 0; i < PacketHeader.HEADER_SIZE; i++) {
            byte[] tampered = base.clone();
            tampered[i] ^= 0x01;
            int idx = i;
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> tryDecrypt(bob, tampered),
                    "header byte " + idx + " flipped — decrypt must fail");
            // Either the version gate rejects pre-decrypt, or the AEAD tag fails.
            assertTrue(ex.getMessage().toLowerCase().contains("tag")
                            || ex.getMessage().toLowerCase().contains("decrypt"),
                    "expected tag/decrypt failure for byte " + idx + ", got: " + ex.getMessage());
        }
    }

    @Test
    void replayingCiphertextAtNewPacketNumberFailsTagCheck() {
        // The killer pre-F1 attack: capture a valid packet, rewrite the
        // packetNumber field in the header to a fresh value (so the
        // receiver's replay window thinks it is new), reuse the same
        // ciphertext+IV+tag. Pre-F1 the AEAD ignored the header so the
        // tag still verified and the old payload was reprocessed.
        // Post-F1, the AAD includes the packetNumber so the tag MUST fail.
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        byte[] bobKey = new ECKey().getPrivKeyBytes();
        CryptoManager alice = new CryptoManager(aliceKey);
        CryptoManager bob = new CryptoManager(bobKey);

        byte[] wire = buildEncryptedWire(alice, bob.getLocalPublicKey(),
                /*connId*/ 42L, /*pn*/ 1L, "secret-payload".getBytes());

        // Sanity: the unmodified packet decrypts fine.
        tryDecrypt(bob, wire);

        // Mutate packetNumber from 1 → 999_999. Header layout:
        // flags(1) + version(4) + connId(8) + packetNumber(8) at offset 13.
        byte[] forged = wire.clone();
        // Overwrite the 8-byte packetNumber at offset 13 with big-endian 999_999.
        long fakePn = 999_999L;
        for (int i = 0; i < 8; i++) {
            forged[13 + i] = (byte) (fakePn >>> (56 - 8 * i));
        }

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tryDecrypt(bob, forged),
                "replay at new packet number must fail AEAD tag check");
        assertTrue(ex.getMessage().toLowerCase().contains("tag"),
                "expected AEAD tag failure, got: " + ex.getMessage());
    }

    @Test
    void mutatingPacketTypeBitsFailsTagCheck() {
        // Pre-F1, an attacker could flip the 2-bit packet type to turn a
        // DATA into an ACK (or back). Post-F1 those bits are inside AAD too.
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        byte[] bobKey = new ECKey().getPrivKeyBytes();
        CryptoManager alice = new CryptoManager(aliceKey);
        CryptoManager bob = new CryptoManager(bobKey);

        byte[] wire = buildEncryptedWire(alice, bob.getLocalPublicKey(),
                /*connId*/ 9L, /*pn*/ 3L, "payload".getBytes());

        // Original is DATA (00). Force ACK bits (01) without changing other
        // flags. The flags byte is at offset 0.
        byte[] forged = wire.clone();
        forged[0] = (byte) ((forged[0] & ~0x03) | PacketHeader.PACKET_TYPE_ACK);

        // The forged packet now claims to be ACK type. PacketCrypto.decryptPacket
        // is called regardless of type at this layer, but AAD won't match.
        // (At the Protocol layer, an ACK-typed packet skips data-frame
        // dispatch — but here we test the crypto layer directly.)
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tryDecrypt(bob, forged));
        assertTrue(ex.getMessage().toLowerCase().contains("tag"),
                "expected tag failure, got: " + ex.getMessage());
    }

    @Test
    void aadDecryptionWithWrongAadFailsTagCheck() {
        // Direct test on CryptoManager: encrypt with one AAD, decrypt with
        // a different AAD → must fail. This locks in the F1 invariant at
        // the lowest layer, independent of PacketCrypto.
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        byte[] bobKey = new ECKey().getPrivKeyBytes();
        CryptoManager alice = new CryptoManager(aliceKey);
        CryptoManager bob = new CryptoManager(bobKey);

        byte[] aadGood = new byte[]{1, 2, 3, 4};
        byte[] aadBad = new byte[]{1, 2, 3, 5};
        byte[] plaintext = "data".getBytes();

        CryptoDataByte ct = alice.encryptAsyTwoWay(plaintext, bob.getLocalPublicKey(), aadGood);

        // Right AAD: succeeds.
        assertArrayEquals(plaintext, bob.decryptAsyTwoWay(ct, aadGood));

        // Wrong AAD: fails with tag error and the counter increments.
        long before = bob.getAeadTagFailCount();
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> bob.decryptAsyTwoWay(ct, aadBad));
        assertTrue(ex.getMessage().toLowerCase().contains("tag"));
        assertEquals(before + 1, bob.getAeadTagFailCount());

        // Null AAD when one was bound: also fails.
        assertThrows(RuntimeException.class,
                () -> bob.decryptAsyTwoWay(ct, null));
    }

    @Test
    void noAadOnEitherSideStillWorks() {
        // Backward-compat: callers that don't use AAD (legacy callers /
        // tests / non-FUDP uses of CryptoManager) still work.
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        byte[] bobKey = new ECKey().getPrivKeyBytes();
        CryptoManager alice = new CryptoManager(aliceKey);
        CryptoManager bob = new CryptoManager(bobKey);

        byte[] plaintext = "no-aad".getBytes();
        CryptoDataByte ct = alice.encryptAsyTwoWay(plaintext, bob.getLocalPublicKey());
        assertArrayEquals(plaintext, bob.decryptAsyTwoWay(ct));
    }

    @Test
    void currentVersionIsOne() {
        // Lock the version constant. Future bumps must update this test.
        assertEquals(1, PacketHeader.CURRENT_VERSION);
        assertEquals(PacketHeader.CURRENT_VERSION, new PacketHeader().getVersion());
        assertEquals(PacketHeader.CURRENT_VERSION, new PacketHeader(1L, 1L).getVersion());
    }
}
