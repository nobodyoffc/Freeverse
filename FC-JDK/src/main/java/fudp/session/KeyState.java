package fudp.session;

/**
 * Key state for symmetric key lifecycle
 *
 * State transitions:
 * - Proposer: PROPOSED → ACTIVE (on receiving ACK)
 * - Acceptor: ACCEPTED → ACTIVE (on receiving first encrypted packet)
 * - Any: ACTIVE → DEPRECATED (on key rotation)
 * - Any: DEPRECATED → deleted (after 60 seconds)
 */
public enum KeyState {
    PROPOSED,    // I proposed this key, waiting for peer's ACK
    ACCEPTED,    // I accepted peer's proposal, waiting for peer to start using it
    ACTIVE,      // Key is in use for encryption/decryption
    DEPRECATED   // Key is deprecated but kept for decryption of late packets (60 seconds)
}
