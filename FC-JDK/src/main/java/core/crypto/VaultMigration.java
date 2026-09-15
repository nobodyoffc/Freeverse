package core.crypto;

import java.util.Arrays;

/**
 * Moves a legacy vault — records encrypted under SHA256(password) and stored under a name
 * derived from the password — to a random data key and a random vault id, in steps that
 * survive a crash at any point. The app supplies its storage through {@link Store}; this class
 * owns the order of the steps, the keys, and the rule that a record that cannot be decrypted
 * aborts the move and leaves the legacy vault as it was.
 */
public final class VaultMigration {

    /** Where a vault stands. The {@link Store} persists it after every step. */
    public enum State {
        /** Nothing done yet. */
        LEGACY,
        /** A DEK and vault id exist; the records are still only in the legacy vault. */
        PREPARED,
        /** Every record is in the new vault and decrypts with the DEK; the app still opens the legacy vault. */
        COPIED,
        /** The app opens the new vault; the legacy vault still exists. */
        FLIPPED,
        /** The legacy vault is gone. */
        DONE
    }

    public interface Store {
        State state();

        /** The vault id saved by {@link #savePrepared}; null before that. */
        String pendingVaultId();

        /** The wrapped DEK saved by {@link #savePrepared}; null before that. */
        String pendingDekCipher();

        /** Durably records PREPARED with the new vault id and wrapped DEK. */
        void savePrepared(String vaultId, String dekCipher) throws Exception;

        /**
         * Copies the whole legacy vault into {@code vaultId}, replacing anything already there,
         * and passes every vault-key cipher through {@code reencrypt} on the way.
         */
        void copyInto(String vaultId, Reencrypt reencrypt) throws Exception;

        /** Calls {@code check} with every vault-key cipher stored under {@code vaultId}. */
        void forEachCipherIn(String vaultId, Check check) throws Exception;

        /** Removes whatever {@link #copyInto} wrote under {@code vaultId}. */
        void discard(String vaultId) throws Exception;

        /** Durably records COPIED. */
        void saveCopied() throws Exception;

        /** In one durable write, makes {@code vaultId} the vault the app opens and records FLIPPED. */
        void flip(String vaultId) throws Exception;

        /** Deletes the legacy vault, including anything on disk named after the password. Safe to repeat. */
        void deleteLegacy() throws Exception;

        /** Durably records DONE. */
        void saveDone() throws Exception;
    }

    public interface Reencrypt {
        String apply(String recordId, String cipherJson) throws UnreadableRecordException;
    }

    public interface Check {
        void accept(String recordId, String cipherJson) throws UnreadableRecordException;
    }

    /** A record that decrypts under neither the legacy key nor the DEK. */
    public static final class UnreadableRecordException extends Exception {
        private final String recordId;

        public UnreadableRecordException(String recordId) {
            super("Record " + recordId + " cannot be decrypted");
            this.recordId = recordId;
        }

        public String getRecordId() {
            return recordId;
        }
    }

    public static final class Result {
        /** The vault's data key; null if the migration was aborted. */
        public final byte[] dek;
        /** The new vault id; null if the migration was aborted. */
        public final String vaultId;
        /** The record that could not be decrypted; null if the migration completed. */
        public final String unreadableRecordId;

        private Result(byte[] dek, String vaultId, String unreadableRecordId) {
            this.dek = dek;
            this.vaultId = vaultId;
            this.unreadableRecordId = unreadableRecordId;
        }

        public boolean isMigrated() {
            return unreadableRecordId == null;
        }
    }

    private VaultMigration() {
    }

    /**
     * Runs or resumes the migration. The caller must already know {@code password} is the vault's —
     * {@code legacySymkey} opens at least one of its records, or the vault has none. If a record
     * cannot be decrypted, nothing is moved and the result names that record.
     */
    public static Result run(Store store, char[] password, byte[] legacySymkey) throws Exception {
        State state = store.state();
        byte[] dek;
        String vaultId;
        if (state == State.LEGACY) {
            dek = VaultKey.newDek();
            vaultId = VaultKey.newVaultId();
            store.savePrepared(vaultId, VaultKey.wrap(dek, password));
            state = State.PREPARED;
        } else {
            vaultId = store.pendingVaultId();
            dek = VaultKey.unwrap(store.pendingDekCipher(), password);
            if (vaultId == null || dek == null)
                throw new IllegalStateException("Vault migration in state " + state + " has no data key this password opens");
        }

        if (state == State.PREPARED) {
            final byte[] key = dek;
            try {
                store.copyInto(vaultId, (recordId, cipher) -> reencrypt(recordId, cipher, legacySymkey, key));
                store.forEachCipherIn(vaultId, (recordId, cipher) -> {
                    byte[] plain = Decryptor.decryptPrikey(cipher, key);
                    if (plain == null) throw new UnreadableRecordException(recordId);
                    Arrays.fill(plain, (byte) 0);
                });
            } catch (UnreadableRecordException e) {
                store.discard(vaultId);
                return new Result(null, null, e.getRecordId());
            }
            store.saveCopied();
            state = State.COPIED;
        }
        if (state == State.COPIED) {
            store.flip(vaultId);
            state = State.FLIPPED;
        }
        if (state == State.FLIPPED) {
            store.deleteLegacy();
            store.saveDone();
        }
        return new Result(dek, vaultId, null);
    }

    /** Decrypts with the legacy key — or the DEK, if an earlier run already converted it — and re-encrypts with the DEK. */
    static String reencrypt(String recordId, String cipher, byte[] legacySymkey, byte[] dek) throws UnreadableRecordException {
        byte[] plain = Decryptor.decryptPrikey(cipher, legacySymkey);
        if (plain == null) plain = Decryptor.decryptPrikey(cipher, dek);
        if (plain == null) throw new UnreadableRecordException(recordId);
        try {
            String out = Encryptor.encryptBySymkeyToJson(plain, dek);
            if (out == null) throw new UnreadableRecordException(recordId);
            return out;
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }
}
