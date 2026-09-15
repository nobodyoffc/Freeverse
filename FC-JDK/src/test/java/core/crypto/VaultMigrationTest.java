package core.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** VaultMigration over an in-memory store, including a crash at every durable step. */
public class VaultMigrationTest {

    private static final char[] PASSWORD = "legacy password".toCharArray();
    private static final String LEGACY_NAME = "a1b2c3";

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Each vault maps record id to cipher JSON; {@code active} is the vault the app opens. */
    static final class MemoryStore implements VaultMigration.Store {
        final Map<String, Map<String, String>> vaults = new HashMap<>();
        String active = LEGACY_NAME;
        VaultMigration.State state = VaultMigration.State.LEGACY;
        String pendingId;
        String pendingDek;
        int crashAt = -1;
        private int step = 0;

        private void maybeCrash() {
            if (step++ == crashAt) throw new IllegalStateException("simulated crash at step " + crashAt);
        }

        public VaultMigration.State state() { return state; }
        public String pendingVaultId() { return pendingId; }
        public String pendingDekCipher() { return pendingDek; }

        public void savePrepared(String vaultId, String dekCipher) {
            maybeCrash();
            pendingId = vaultId;
            pendingDek = dekCipher;
            state = VaultMigration.State.PREPARED;
        }

        public void copyInto(String vaultId, VaultMigration.Reencrypt reencrypt) throws Exception {
            Map<String, String> target = new LinkedHashMap<>();
            vaults.put(vaultId, target);
            boolean first = true;
            for (Map.Entry<String, String> r : vaults.get(LEGACY_NAME).entrySet()) {
                target.put(r.getKey(), reencrypt.apply(r.getKey(), r.getValue()));
                if (first) {
                    first = false;
                    maybeCrash();
                }
            }
        }

        public void forEachCipherIn(String vaultId, VaultMigration.Check check) throws Exception {
            maybeCrash();
            for (Map.Entry<String, String> r : vaults.get(vaultId).entrySet()) check.accept(r.getKey(), r.getValue());
        }

        public void discard(String vaultId) { vaults.remove(vaultId); }

        public void saveCopied() {
            maybeCrash();
            state = VaultMigration.State.COPIED;
        }

        public void flip(String vaultId) {
            maybeCrash();
            active = vaultId;
            state = VaultMigration.State.FLIPPED;
        }

        public void deleteLegacy() {
            vaults.remove(LEGACY_NAME);
            maybeCrash();
        }

        public void saveDone() {
            maybeCrash();
            state = VaultMigration.State.DONE;
        }
    }

    private static byte[] legacyKey() {
        return Hash.sha256(new String(PASSWORD).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> records() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("key-1", "private key one");
        m.put("key-2", "private key two");
        m.put("secret-1", "totp seed");
        return m;
    }

    private static MemoryStore legacyStore() {
        MemoryStore store = new MemoryStore();
        Map<String, String> legacy = new LinkedHashMap<>();
        for (Map.Entry<String, String> r : records().entrySet())
            legacy.put(r.getKey(), Encryptor.encryptBySymkeyToJson(r.getValue().getBytes(StandardCharsets.UTF_8), legacyKey()));
        store.vaults.put(LEGACY_NAME, legacy);
        return store;
    }

    private static void checkMigrated(MemoryStore store, VaultMigration.Result result, String label) {
        check(result.isMigrated(), label + ": migration must complete");
        check(store.state == VaultMigration.State.DONE, label + ": state must be DONE, was " + store.state);
        check(result.vaultId.equals(store.active) && !VaultKey.isLegacyName(result.vaultId), label + ": the app must open the new vault id");
        check(!store.vaults.containsKey(LEGACY_NAME), label + ": the legacy vault must be deleted");
        check(Arrays.equals(result.dek, VaultKey.unwrap(store.pendingDek, PASSWORD)), label + ": the stored wrapped DEK must unwrap to the result's DEK");
        Map<String, String> moved = store.vaults.get(result.vaultId);
        check(moved.size() == records().size(), label + ": every record must move");
        for (Map.Entry<String, String> r : records().entrySet()) {
            byte[] plain = Decryptor.decryptPrikey(moved.get(r.getKey()), result.dek);
            check(plain != null && r.getValue().equals(new String(plain, StandardCharsets.UTF_8)), label + ": " + r.getKey() + " must decrypt with the DEK");
            check(Decryptor.decryptPrikey(moved.get(r.getKey()), legacyKey()) == null, label + ": " + r.getKey() + " must no longer decrypt with the legacy key");
        }
    }

    @Test
    public void migratesALegacyVault() throws Exception {
        MemoryStore store = legacyStore();
        checkMigrated(store, VaultMigration.run(store, PASSWORD, legacyKey()), "clean run");
    }

    @Test
    public void resumesAfterACrashAtEveryStep() throws Exception {
        // Seven durable steps; crashAt = 7 is the run that never crashes.
        for (int crashAt = 0; crashAt <= 7; crashAt++) {
            MemoryStore store = legacyStore();
            store.crashAt = crashAt;
            try {
                VaultMigration.run(store, PASSWORD, legacyKey());
            } catch (IllegalStateException simulatedCrash) {
                // the process died here
            }
            store.crashAt = -1;
            checkMigrated(store, VaultMigration.run(store, PASSWORD, legacyKey()), "crash at step " + crashAt);
        }
    }

    @Test
    public void abortsAndKeepsTheLegacyVaultWhenARecordCannotBeDecrypted() throws Exception {
        MemoryStore store = legacyStore();
        byte[] otherKey = new byte[32];
        Arrays.fill(otherKey, (byte) 7);
        store.vaults.get(LEGACY_NAME).put("broken", Encryptor.encryptBySymkeyToJson("unreadable".getBytes(StandardCharsets.UTF_8), otherKey));
        Map<String, String> before = new LinkedHashMap<>(store.vaults.get(LEGACY_NAME));

        for (int attempt = 1; attempt <= 2; attempt++) {
            VaultMigration.Result result = VaultMigration.run(store, PASSWORD, legacyKey());
            check(!result.isMigrated() && "broken".equals(result.unreadableRecordId), "attempt " + attempt + ": must abort on the unreadable record");
            check(LEGACY_NAME.equals(store.active), "attempt " + attempt + ": the app must keep opening the legacy vault");
            check(store.vaults.get(LEGACY_NAME).equals(before), "attempt " + attempt + ": the legacy vault must be untouched");
            check(store.vaults.size() == 1, "attempt " + attempt + ": the partial new vault must be discarded");
        }
    }
}
