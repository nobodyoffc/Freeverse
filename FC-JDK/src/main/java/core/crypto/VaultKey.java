package core.crypto;

import data.fcData.AlgorithmId;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * The data key (DEK) that encrypts a wallet vault, and its wrapping under the password.
 *
 * A vault's records are encrypted with a random 32-byte DEK. The DEK is stored only as
 * {@code dekCipher}: a Password cipher (FVEP8) under Argon2id (FTSP29) and AES-256-GCM with
 * the KDF recorded. Unlocking costs one Argon2id run, and changing the password re-wraps the
 * DEK without touching any record. Nothing derived from the password is stored beside it.
 */
public final class VaultKey {

    public static final int DEK_LENGTH = 32;
    /** Vault ids are 12 hex chars; the password-derived names they replace are 6. */
    public static final int VAULT_ID_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private VaultKey() {
    }

    public static byte[] newDek() {
        byte[] dek = new byte[DEK_LENGTH];
        RANDOM.nextBytes(dek);
        return dek;
    }

    public static String newVaultId() {
        byte[] bytes = new byte[VAULT_ID_LENGTH / 2];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(VAULT_ID_LENGTH);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** @return true if {@code name} is a legacy password-derived vault name rather than a random vault id. */
    public static boolean isLegacyName(String name) {
        return name != null && name.length() < VAULT_ID_LENGTH;
    }

    /** Wraps {@code dek} under {@code password}. The returned JSON carries no key material. */
    public static String wrap(byte[] dek, char[] password) {
        if (dek == null || dek.length != DEK_LENGTH) throw new IllegalArgumentException("DEK must be " + DEK_LENGTH + " bytes");
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        encryptor.setKdf(Kdf.Argon2id_No1_NrC7);
        CryptoDataByte wrapped = encryptor.encryptByPassword(dek.clone(), password);
        if (wrapped.getCode() == null || wrapped.getCode() != 0 || wrapped.getCipher() == null)
            throw new IllegalStateException("Failed to wrap the vault key: " + wrapped.getMessage());
        // The JSON form serializes data; clear it and every key field before writing it out.
        wrapped.setData(null);
        wrapped.setSymkey(null);
        wrapped.setPassword(null);
        return wrapped.toJson();
    }

    /**
     * @return the DEK, or null if {@code password} is wrong or {@code dekCipher} is not an
     * Argon2id-wrapped AES-256-GCM key.
     */
    public static byte[] unwrap(String dekCipher, char[] password) {
        if (dekCipher == null || password == null) return null;
        CryptoDataByte parsed;
        try {
            parsed = CryptoDataByte.fromJson(dekCipher);
        } catch (Exception e) {
            return null;
        }
        // A cipher whose KDF was stripped or swapped for a cheaper one must not open the vault,
        // or an attacker who can edit storage could make the next unlock skip Argon2id.
        if (parsed == null || parsed.getType() != EncryptType.Password
                || parsed.getKdf() != Kdf.Argon2id_No1_NrC7
                || parsed.getAlg() != AlgorithmId.FC_AesGcm256_No1_NrC7) return null;
        CryptoDataByte result = Decryptor.decryptByPassword(parsed, password);
        byte[] dek = result.getData();
        if (result.getCode() == null || result.getCode() != 0 || dek == null || dek.length != DEK_LENGTH) return null;
        return dek;
    }

    /** @return {@code dekCipher} re-wrapped under {@code newPassword}, or null if {@code oldPassword} is wrong. */
    public static String rewrap(String dekCipher, char[] oldPassword, char[] newPassword) {
        byte[] dek = unwrap(dekCipher, oldPassword);
        if (dek == null) return null;
        try {
            return wrap(dek, newPassword);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }
}
