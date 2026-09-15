package core.crypto;

import data.fcData.AlgorithmId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/** VaultKey: wrapping a vault's data key under the password. */
public class VaultKeyTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    public void wrapsAndUnwraps() {
        byte[] dek = VaultKey.newDek();
        String cipher = VaultKey.wrap(dek, "correct horse".toCharArray());
        check(Arrays.equals(dek, VaultKey.unwrap(cipher, "correct horse".toCharArray())), "the right password must unwrap the DEK");
        check(VaultKey.unwrap(cipher, "wrong horse".toCharArray()) == null, "a wrong password must not unwrap the DEK");
    }

    @Test
    public void wrappedKeyHoldsNoKeyMaterial() {
        byte[] dek = VaultKey.newDek();
        String cipher = VaultKey.wrap(dek, "pw".toCharArray());
        check(!cipher.contains(hex(dek)), "the DEK must not appear in the stored cipher");
        check(!cipher.contains("\"data\"") && !cipher.contains("\"symkey\"") && !cipher.contains("\"password\""),
                "no secret field may be serialized: " + cipher);
        check(cipher.contains("Argon2id@No1_NrC7"), "the cipher must record its KDF: " + cipher);
    }

    @Test
    public void rewrapChangesThePasswordNotTheKey() {
        byte[] dek = VaultKey.newDek();
        String oldCipher = VaultKey.wrap(dek, "old".toCharArray());
        check(VaultKey.rewrap(oldCipher, "nope".toCharArray(), "new".toCharArray()) == null, "rewrap must refuse a wrong old password");
        String newCipher = VaultKey.rewrap(oldCipher, "old".toCharArray(), "new".toCharArray());
        check(newCipher != null, "rewrap with the right password must succeed");
        check(Arrays.equals(dek, VaultKey.unwrap(newCipher, "new".toCharArray())), "the new password must unwrap the same DEK");
        check(VaultKey.unwrap(newCipher, "old".toCharArray()) == null, "the old password must no longer unwrap it");
    }

    @Test
    public void rejectsAKeyWrappedWithACheaperKdf() {
        Encryptor weak = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        weak.setKdf(Kdf.Sha256Iv_No1_NrC7);
        CryptoDataByte c = weak.encryptByPassword(VaultKey.newDek(), "pw".toCharArray());
        c.setData(null);
        check(VaultKey.unwrap(c.toJson(), "pw".toCharArray()) == null, "a Sha256Iv-wrapped DEK must be refused");
    }

    @Test
    public void vaultIdsAreRandomAndDistinctFromLegacyNames() {
        String a = VaultKey.newVaultId();
        String b = VaultKey.newVaultId();
        check(a.length() == VaultKey.VAULT_ID_LENGTH && a.matches("[0-9a-f]+"), "a vault id is " + VaultKey.VAULT_ID_LENGTH + " hex chars: " + a);
        check(!a.equals(b), "vault ids must differ");
        check(!VaultKey.isLegacyName(a), "a vault id is not a legacy name");
        check(VaultKey.isLegacyName("a1b2c3"), "a 6-char password-derived name is legacy");
    }
}
