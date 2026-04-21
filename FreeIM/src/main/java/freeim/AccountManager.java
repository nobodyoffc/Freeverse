package freeim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import core.crypto.Algorithm.AesGcm256;
import core.crypto.CryptoDataByte;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import org.bitcoinj.core.ECKey;
import utils.Hex;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Password-based account management.
 *
 * One password manages a group of accounts. Private keys are encrypted with
 * AES-GCM-256 using SHA256(password) as the key, per FTSP12V1_AesGcm256.
 *
 * Storage: ~/.freeim/accounts/{passwordHash6}.json
 */
public class AccountManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ACCOUNT_LIST_TYPE = new TypeToken<List<Account>>() {}.getType();
    private static final Encryptor ENCRYPTOR = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);

    private final Path accountsDir;
    private final BufferedReader br;

    private byte[] encryptionKey;       // SHA256(password), 32 bytes
    private String groupFileId;         // first 6 hex chars of SHA256(SHA256(password))
    private List<Account> accounts;     // loaded accounts for this password
    private Account currentAccount;     // selected account

    public static class Account {
        public String fid;
        public String pubkey;           // hex
        public String encryptedPrikey;  // base64
        public String iv;              // hex, 12 bytes
        public int port;

        public Account() {}

        public Account(String fid, String pubkey, String encryptedPrikey, String iv, int port) {
            this.fid = fid;
            this.pubkey = pubkey;
            this.encryptedPrikey = encryptedPrikey;
            this.iv = iv;
            this.port = port;
        }
    }

    public record SelectedAccount(byte[] prikey, byte[] pubkey, String fid, int port) {}

    public AccountManager(Path baseDir, BufferedReader br) {
        this.accountsDir = baseDir.resolve("accounts");
        this.br = br;
        try {
            Files.createDirectories(accountsDir);
        } catch (IOException e) {
            System.err.println("  Failed to create accounts directory: " + e.getMessage());
        }
    }

    /**
     * Login flow: prompt password, load or create account group, select account.
     * @return selected account with decrypted prikey, or null if cancelled
     */
    public SelectedAccount login() throws IOException {
        System.out.print("  Password (or 'new' to create): ");
        String input = br.readLine().trim();

        if ("new".equalsIgnoreCase(input)) {
            return createNewGroup();
        }

        // Derive encryption key and group file ID
        encryptionKey = sha256(input.getBytes(StandardCharsets.UTF_8));
        groupFileId = Hex.toHex(sha256(encryptionKey)).substring(0, 12);

        // Try to load accounts
        Path groupFile = accountsDir.resolve(groupFileId + ".json");
        if (Files.exists(groupFile)) {
            accounts = loadAccounts(groupFile);
            if (accounts.isEmpty()) {
                System.out.println("  No accounts for this password. Creating one...");
                return createFirstAccount();
            }

            // Verify password by trying to decrypt the first account
            try {
                decryptPrikey(accounts.get(0));
            } catch (Exception e) {
                System.out.println("  Wrong password or corrupted data.");
                return null;
            }

            return selectAccount();
        } else {
            // No group file exists — this is a new password, create first account
            accounts = new ArrayList<>();
            System.out.println("  New password. Creating first account...");
            return createFirstAccount();
        }
    }

    private SelectedAccount createNewGroup() throws IOException {
        System.out.print("  Create password: ");
        String password = br.readLine().trim();
        if (password.isEmpty()) {
            System.out.println("  Password cannot be empty.");
            return null;
        }
        System.out.print("  Confirm password: ");
        String confirm = br.readLine().trim();
        if (!password.equals(confirm)) {
            System.out.println("  Passwords don't match.");
            return null;
        }

        encryptionKey = sha256(password.getBytes(StandardCharsets.UTF_8));
        groupFileId = Hex.toHex(sha256(encryptionKey)).substring(0, 12);
        accounts = new ArrayList<>();
        return createFirstAccount();
    }

    private SelectedAccount createFirstAccount() throws IOException {
        System.out.println("  1. Generate new account");
        System.out.println("  2. Import existing private key");
        System.out.print("  Choice: ");
        String choice = br.readLine().trim();

        System.out.print("  Port (default 9000): ");
        String portStr = br.readLine().trim();
        int port = portStr.isEmpty() ? 9000 : Integer.parseInt(portStr);

        if ("2".equals(choice)) {
            return importAccount(port);
        }
        return createAccount(port);
    }

    private SelectedAccount selectAccount() throws IOException {
        System.out.println("  Accounts:");
        for (int i = 0; i < accounts.size(); i++) {
            Account a = accounts.get(i);
            System.out.printf("    %d. %s (port %d)%n", i + 1, a.fid, a.port);
        }
        int newIdx = accounts.size() + 1;
        int importIdx = accounts.size() + 2;
        int removeIdx = accounts.size() + 3;
        System.out.printf("    %d. [New Account]%n", newIdx);
        System.out.printf("    %d. [Import Private Key]%n", importIdx);
        System.out.printf("    %d. [Remove Account]%n", removeIdx);
        System.out.print("  Select: ");
        String choice = br.readLine().trim();

        try {
            int idx = Integer.parseInt(choice) - 1;
            if (idx >= 0 && idx < accounts.size()) {
                Account selected = accounts.get(idx);
                currentAccount = selected;
                byte[] prikey = decryptPrikey(selected);
                byte[] pubkey = Hex.fromHex(selected.pubkey);
                return new SelectedAccount(prikey, pubkey, selected.fid, selected.port);
            } else if (idx == accounts.size()) {
                // New account
                System.out.print("  Port (default 9000): ");
                String portStr = br.readLine().trim();
                int port = portStr.isEmpty() ? 9000 : Integer.parseInt(portStr);
                return createAccount(port);
            } else if (idx == accounts.size() + 1) {
                // Import account
                System.out.print("  Port (default 9000): ");
                String portStr = br.readLine().trim();
                int port = portStr.isEmpty() ? 9000 : Integer.parseInt(portStr);
                return importAccount(port);
            } else if (idx == accounts.size() + 2) {
                // Remove account
                removeAccountAtLogin();
                return selectAccount(); // re-show menu after removal
            }
        } catch (NumberFormatException ignored) {}

        System.out.println("  Cancelled.");
        return null;
    }

    /**
     * Remove an account during the login/select flow.
     */
    private void removeAccountAtLogin() throws IOException {
        if (accounts.isEmpty()) {
            System.out.println("  No accounts to remove.");
            return;
        }
        System.out.print("  Remove which account (1-" + accounts.size() + "): ");
        String input = br.readLine().trim();
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= accounts.size()) {
                System.out.println("  Invalid selection.");
                return;
            }
            Account toRemove = accounts.get(idx);
            System.out.printf("  Remove %s (port %d)? Type 'yes' to confirm: ", toRemove.fid, toRemove.port);
            String confirm = br.readLine().trim();
            if (!"yes".equalsIgnoreCase(confirm)) {
                System.out.println("  Cancelled.");
                return;
            }
            accounts.remove(idx);
            save();
            System.out.println("  Account " + toRemove.fid + " removed.");
        } catch (NumberFormatException e) {
            System.out.println("  Invalid input.");
        }
    }

    /**
     * Create a new account, encrypt its prikey, save to group file.
     */
    public SelectedAccount createAccount(int port) {
        ECKey key = new ECKey();
        byte[] prikey = key.getPrivKeyBytes();
        byte[] pubkey = key.getPubKey();
        String fid = KeyTools.pubkeyToFchAddr(pubkey);

        // Encrypt prikey
        CryptoDataByte result = ENCRYPTOR.encryptBySymkey(prikey, encryptionKey);
        if (result.getCode() != null && result.getCode() != 0) {
            System.out.println("  Encryption failed: " + result.getMessage());
            return null;
        }

        Account account = new Account(
            fid,
            Hex.toHex(pubkey),
            Base64.getEncoder().encodeToString(result.getCipher()),
            Hex.toHex(result.getIv()),
            port
        );

        accounts.add(account);
        currentAccount = account;
        save();

        System.out.println("  New account: " + fid);
        return new SelectedAccount(prikey, pubkey, fid, port);
    }

    /**
     * Import an existing private key, encrypt and save to group file.
     */
    public SelectedAccount importAccount(int port) throws IOException {
        System.out.print("  Private key (hex): ");
        String prikeyHex = br.readLine().trim();
        if (prikeyHex.isEmpty()) {
            System.out.println("  Cancelled.");
            return null;
        }

        byte[] prikey = Hex.fromHex(prikeyHex);
        if (prikey.length != 32) {
            System.out.println("  Invalid private key length. Must be 32 bytes (64 hex chars).");
            return null;
        }

        ECKey key = ECKey.fromPrivate(prikey);
        byte[] pubkey = key.getPubKey();
        String fid = KeyTools.pubkeyToFchAddr(pubkey);

        // Check if already exists in this group
        for (Account a : accounts) {
            if (a.fid.equals(fid)) {
                // Update port if a different one was requested
                if (a.port != port) {
                    a.port = port;
                    save();
                }
                System.out.println("  Account " + fid + " already exists in this group (port " + port + ").");
                currentAccount = a;
                return new SelectedAccount(prikey, pubkey, fid, port);
            }
        }

        // Encrypt prikey
        CryptoDataByte result = ENCRYPTOR.encryptBySymkey(prikey, encryptionKey);
        if (result.getCode() != null && result.getCode() != 0) {
            System.out.println("  Encryption failed: " + result.getMessage());
            return null;
        }

        Account account = new Account(
            fid,
            Hex.toHex(pubkey),
            Base64.getEncoder().encodeToString(result.getCipher()),
            Hex.toHex(result.getIv()),
            port
        );

        accounts.add(account);
        currentAccount = account;
        save();

        System.out.println("  Imported account: " + fid);
        return new SelectedAccount(prikey, pubkey, fid, port);
    }

    /**
     * Decrypt a stored account's private key.
     */
    public byte[] decryptPrikey(Account account) {
        byte[] cipher = Base64.getDecoder().decode(account.encryptedPrikey);
        byte[] iv = Hex.fromHex(account.iv);

        CryptoDataByte cd = new CryptoDataByte();
        cd.setCipher(cipher);
        cd.setSymkey(encryptionKey);
        cd.setIv(iv);
        cd.setAlg(AlgorithmId.FC_AesGcm256_No1_NrC7);

        CryptoDataByte result = AesGcm256.decrypt(cd);
        if (result.getCode() != null && result.getCode() != 0) {
            throw new RuntimeException("Decryption failed: " + result.getMessage());
        }
        return result.getData();
    }

    // ========== Settings Operations ==========

    /**
     * Change password: re-encrypt all prikeys with new password.
     */
    public void changePassword() throws IOException {
        // Decrypt all prikeys with current password
        List<byte[]> prikeys = new ArrayList<>();
        for (Account a : accounts) {
            prikeys.add(decryptPrikey(a));
        }

        System.out.print("  New password: ");
        String newPassword = br.readLine().trim();
        if (newPassword.isEmpty()) {
            System.out.println("  Cancelled.");
            return;
        }
        System.out.print("  Confirm new password: ");
        String confirm = br.readLine().trim();
        if (!newPassword.equals(confirm)) {
            System.out.println("  Passwords don't match.");
            return;
        }

        // Delete old group file
        Path oldFile = accountsDir.resolve(groupFileId + ".json");
        Files.deleteIfExists(oldFile);

        // Update encryption key and group file ID
        encryptionKey = sha256(newPassword.getBytes(StandardCharsets.UTF_8));
        groupFileId = Hex.toHex(sha256(encryptionKey)).substring(0, 12);

        // Re-encrypt all prikeys
        for (int i = 0; i < accounts.size(); i++) {
            byte[] prikey = prikeys.get(i);
            CryptoDataByte result = ENCRYPTOR.encryptBySymkey(prikey, encryptionKey);
            accounts.get(i).encryptedPrikey = Base64.getEncoder().encodeToString(result.getCipher());
            accounts.get(i).iv = Hex.toHex(result.getIv());
        }

        save();
        System.out.println("  Password changed. All accounts re-encrypted.");
    }

    /**
     * Change port for the current account. Returns new port for restart.
     */
    public int resetPort() throws IOException {
        if (currentAccount == null) {
            System.out.println("  No account selected.");
            return -1;
        }
        System.out.println("  Current port: " + currentAccount.port);
        System.out.print("  New port: ");
        String portStr = br.readLine().trim();
        if (portStr.isEmpty()) {
            System.out.println("  Cancelled.");
            return -1;
        }
        int newPort = Integer.parseInt(portStr);
        currentAccount.port = newPort;
        save();
        System.out.println("  Port changed to " + newPort + ".");
        return newPort;
    }

    /**
     * Remove the current account from the group and delete its data.
     */
    public boolean removeCurrentAccount(Path baseDir) throws IOException {
        if (currentAccount == null) {
            System.out.println("  No account selected.");
            return false;
        }
        System.out.println("  WARNING: This will delete account " + currentAccount.fid + " and all its data.");
        System.out.print("  Type 'yes' to confirm: ");
        String confirm = br.readLine().trim();
        if (!"yes".equalsIgnoreCase(confirm)) {
            System.out.println("  Cancelled.");
            return false;
        }

        // Delete account data directory
        Path dataDir = baseDir.resolve(currentAccount.fid);
        if (Files.exists(dataDir)) {
            deleteDirectory(dataDir);
        }

        // Remove from accounts list
        accounts.removeIf(a -> a.fid.equals(currentAccount.fid));
        save();

        System.out.println("  Account " + currentAccount.fid + " removed.");
        currentAccount = null;
        return true;
    }

    /**
     * Add a new account to the current group. Returns the new account for switching.
     */
    public SelectedAccount addAccount() throws IOException {
        System.out.print("  Port for new account (default 9000): ");
        String portStr = br.readLine().trim();
        int port = portStr.isEmpty() ? 9000 : Integer.parseInt(portStr);
        return createAccount(port);
    }

    public Account getCurrentAccount() {
        return currentAccount;
    }

    // ========== Persistence ==========

    private void save() {
        Path file = accountsDir.resolve(groupFileId + ".json");
        try {
            Files.writeString(file, GSON.toJson(accounts));
        } catch (IOException e) {
            System.err.println("  Failed to save accounts: " + e.getMessage());
        }
    }

    private List<Account> loadAccounts(Path file) {
        try {
            String json = Files.readString(file);
            List<Account> list = GSON.fromJson(json, ACCOUNT_LIST_TYPE);
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("  Failed to load accounts: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ========== Helpers ==========

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
    }
}
