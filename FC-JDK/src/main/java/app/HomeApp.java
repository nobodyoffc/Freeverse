package app;

import constants.*;
import core.crypto.*;
import core.crypto.old.StartTools;
import core.fch.RawTxInfo;
import core.fch.RawTxParser;
import core.fch.TxCreator;
import core.fch.Wallet;
import data.fchData.Cid;
import data.fchData.Multisign;
import ui.Inputer;
import ui.Menu;
import config.Settings;
import ui.Shower;
import config.Starter;
import clients.*;
import core.crypto.Algorithm.Bitcore;
import data.fcData.AlgorithmId;
import data.fcData.FidTxMask;
import data.fcData.Signature;
import data.fchData.Cash;
import data.fchData.SendTo;
import data.feipData.Service;
import handlers.*;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.fch.FchMainNetwork;
import org.bitcoinj.params.MainNetParams;
import org.jetbrains.annotations.Nullable;

import utils.*;
import utils.http.AuthType;
import utils.http.RequestMethod;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import static ui.Inputer.askIfYes;
import static constants.Strings.newCashMapKey;
import static constants.Strings.spendCashMapKey;
import static constants.Values.SHA256X2;
import static core.crypto.Hash.getSign;
import static core.crypto.KeyTools.prikeyToFid;
import static core.crypto.KeyTools.prikeyToPubkey;
import static core.fch.Inputer.inputFidArray;
import static core.fch.Inputer.inputGoodFid;
import static core.fch.TxCreator.DEFAULT_FEE_RATE;
import static utils.FileUtils.getAvailableFile;

public class HomeApp extends FcApp {
    private final String myFid;
    private final String myPubkey;
    private final String myPrikeyCipher;
    private final Settings settings;
    private final ApipClient apipClient;
    private CashManager cashHandler;
    private SecretManager secretHandler;
    private ContactManager contactHandler;
    private MailManager mailHandler;
    private final BufferedReader br;

    // These could remain static if they're truly application-wide constants
    private static final String CLIENT_NAME = "HOME";
    private static final Map<String, Object> SETTING_MAP = new HashMap<>();

    public HomeApp(Settings settings, BufferedReader br) {
        this.settings = settings;
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.myFid = settings.getMainFid();
        this.myPubkey = settings.getMyPubkey();
        this.myPrikeyCipher = settings.getMyPrikeyCipher();
        this.br = br;
    }


    public static void main(String[] args) throws Exception {
        Menu.welcome(CLIENT_NAME);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<data.fcData.Module> modules = new ArrayList<>();
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.APIP.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.CASH.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.SECRET.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.CONTACT.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.MAIL.name()));

        while (true) {
            Settings settings = Starter.startClient(CLIENT_NAME, SETTING_MAP, br, modules, null);
            if (settings == null) return;

            byte[] symKey = settings.getSymkey();

            HomeApp home = new HomeApp(settings, br);
            home.menu(symKey);

            if (!Inputer.askIfYes(br, "Switch to another FID?")) System.exit(0);
        }
    }

    public static void hash(BufferedReader br) {
        Menu menu = new Menu("Hash");
        menu.add("SHA256 String")
                .add("SHA256 File")
                .add("SHA256x2 String")
                .add("SHA256x2 File")
                .add("SHA512 String")
                .add("SHA512x2 String")
                .add("RIPEMD160 String")
                .add("SHA3 String");

        while (true) {
            System.out.println(" << Hash Functions >>");
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null) break;
                    String hash = Hash.sha256(text);
                    System.out.println("raw string: " + text);
                    System.out.println("sha256:");
                    System.out.println("----");
                    System.out.println(hash);
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 2 -> {
                    while (true) {
                        File file = inputFilePath(br);
                        if (file == null) break;
                        byte[] hash = Hash.sha256Stream(file);
                        if (hash == null) {
                            System.out.println("Error calculating SHA-256 hash.");
                            break;
                        }
                        System.out.println("file:" + file.getAbsolutePath());
                        System.out.println("sha256:");
                        System.out.println("----");
                        System.out.println(Hex.toHex(hash));
                        System.out.println("----");
                        Menu.anyKeyToContinue(br);
                    }
                }
                case 3 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null) break;
                    String hash = Hash.sha256x2(text);
                    System.out.println("----");
                    System.out.println("raw string: " + text);
                    System.out.println("sha256x2:");
                    System.out.println("----");
                    System.out.println(hash);
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 4 -> {
                    while (true) {
                        File file = inputFilePath(br);
                        if (file == null) break;
                        try {
                            String hash = Hash.sha256x2(file);
                            if (hash == null) {
                                System.out.println("Error calculating SHA-256x2 hash.");
                                break;
                            }
                            System.out.println("file:" + file.getAbsolutePath());
                            System.out.println("sha256x2:");
                            System.out.println("----");
                            System.out.println(hash);
                            System.out.println("----");
                            Menu.anyKeyToContinue(br);
                        } catch (IOException e) {
                            System.out.println("Error reading file: " + e.getMessage());
                            break;
                        }
                    }
                }
                case 5 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null || text.isEmpty()) break;
                    byte[] hashBytes = Hash.sha512(text.getBytes());
                    System.out.println("----");
                    System.out.println("raw string: " + text);
                    System.out.println("sha512:");
                    System.out.println("----");
                    System.out.println(Hex.toHex(hashBytes));
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 6 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null || text.isEmpty()) break;
                    String hash = Hash.sha512x2(text);
                    System.out.println("----");
                    System.out.println("raw string: " + text);
                    System.out.println("sha512x2:" + hash);
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 7 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null) break;
                    byte[] hashBytes = Hash.Ripemd160(text.getBytes());
                    System.out.println("----");
                    System.out.println("raw string: " + text);
                    System.out.println("ripemd160:");
                    System.out.println("----");
                    System.out.println(Hex.toHex(hashBytes));
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 8 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null || text.isEmpty()) break;
                    String hash = Hash.sha3String(text);
                    System.out.println("raw string: " + text);
                    System.out.println("sha3:");
                    System.out.println("----");
                    System.out.println(hash);
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 0 -> {
                    return;
                }
            }
        }
    }


    private static File inputFilePath(BufferedReader br) {
        System.out.println("Input the full path of the file to be hashed, enter to exit:");
        String filePath = Inputer.inputString(br);
        if ("".equals(filePath)) return null;

        File file = new File(filePath);
        if (file.isDirectory()) {
            System.out.println("It's a directory.");
            return null;
        }
        if (!file.exists()) {
            System.out.println("File does not exist.");
            return null;
        }
        return file;
    }

    public static void showPrikeyInfo(BufferedReader br, byte[] prikey) {
        if (prikey == null) return;
        String fid = prikeyToFid(prikey);

        Shower.printUnderline(10);
        System.out.println("* FID:" + fid);
        System.out.println("* Pubkey:" + HexFormat.of().formatHex(KeyTools.prikeyToPubkey(prikey)));

        String prikey32 = HexFormat.of().formatHex(prikey);
        System.out.println("* Prikey in hex:" + prikey32);
        System.out.println("* Prikey WIF compressed:" + KeyTools.prikey32To38WifCompressed(prikey32));
        System.out.println("* Prikey WIF:" + KeyTools.prikey32To37(prikey32));
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
    }

    public void menu(byte[] symKey) throws Exception {
        Menu menu = new Menu("Home", this::close);
        ArrayList<String> menuItemList = new ArrayList<>();
        menuItemList.add("Send");
        menuItemList.add("Cash");
        menuItemList.add("TX");
        menuItemList.add("Sign & Verify");
        menuItemList.add("Encrypt & Decrypt");
        menuItemList.add("Key & Address");
        menuItemList.add("Hash");
        menuItemList.add("MultiSign");
        menuItemList.add("Secrets");
        menuItemList.add("Contacts");
        menuItemList.add("Mails");
        menuItemList.add("Watching FID");
        menuItemList.add("Settings");

        menu.add(menuItemList);

        while (true) {
            System.out.println(" << APIP Client>>");
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> cashHandler.sendTx();
                case 2 -> cashHandler.menu(br, false);
                case 3 -> handleTxMenu(symKey);
                case 4 -> signAndVerify(myPrikeyCipher, symKey, br);
                case 5 -> encryptAndDecrypt(myPrikeyCipher, symKey, br);
                case 6 -> keyAndAddress(myPubkey, myPrikeyCipher, symKey, br);
                case 7 -> hash(br);
                case 8 -> multiSign(settings);
                case 9 -> secretHandler.menu(br, false);
                case 10 -> contactHandler.menu(br, false);
                case 11 -> mailHandler.menu(br,false);
                case 12 -> {
                    settings.setting(br, null);
                    symKey = settings.getSymkey();
                }
                case 0 -> {
                    return;
                }
            }
        }
    }

    public void close() {
        settings.close();
    }

    private void handleTxMenu(byte[] symKey) {
        Menu txMenu = new Menu("TX Operations");
        ArrayList<String> txMenuItems = new ArrayList<>();
        txMenuItems.add("List Tx");
        txMenuItems.add("Decode Tx");
        txMenuItems.add("Sign Raw Tx");
        txMenuItems.add("Broadcast Tx");
        txMenu.add(txMenuItems);

        while (true) {
            txMenu.show();
            int choice = txMenu.choose(br);
            switch (choice) {
                case 1 -> listTx();
                case 2 -> decodeTxFch(br);
                case 3 -> signRawTx(myPrikeyCipher, symKey, br);
                case 4 -> broadcastTx(apipClient, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private void listTx() {
        System.out.println("Requesting txSearch...");
        List<String> last = null;
        while (true) {
            List<FidTxMask> result = apipClient.txByFid(myFid, 20, last == null ? null : last.toArray(new String[0]), RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if (result == null) return;
            if (result.size() < 20) break;
            last = apipClient.getFcClientEvent().getResponseBody().getLast();
            FidTxMask.showFidTxMaskList(result, "Your TXs", 0);
            if (!askIfYes(br, "Continue?")) break;
        }
    }

    public static void decryptWithPasswordFromBundle(BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        String bundle = Inputer.inputString(br, "Input the bundle in Base64:");
        if (bundle == null) {
            System.out.println("Bundle is null.");
            return;
        }
        String ask = "Input the password:";
        char[] password = Inputer.inputPassword(br, ask);
        if (password == null) return;
        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        CryptoDataByte cryptoDataByte = decryptor.decryptBundleByPassword(bundleBytes, password);
        System.out.println(new String(cryptoDataByte.getData()));
    }

    public static void encryptWithPasswordBundle(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        String msg = Inputer.inputMsg(br);
        String ask = "Input the password:";
        char[] password = Inputer.inputPassword(br, ask);
        if (password == null) return;
        assert msg != null;
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] bundle = encryptor.encryptToBundleByPassword(msgBytes, password);
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptAsyTwoWayFromBundle(byte[] prikey, BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        String bundle = Inputer.inputString(br, "Input the bundle in Base64:");
        if (bundle == null) {
            System.out.println("Bundle is null.");
            return;
        }
        String pubkey = Inputer.inputString(br, "Input the pubkey in hex:");
        if (pubkey == null) {
            System.out.println("Pubkey is null.");
            return;
        }

        pubkey = Inputer.inputString(br, "Input the pubkey in hex:");

        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;
        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        System.out.println(decryptor.decryptBundleByAsyTwoWay(bundleBytes, prikey, Hex.fromHex(pubkey)));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptAsyOneWayFromBundle(byte[] prikey, BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        String bundle = Inputer.inputString(br, "Input the bundle in Base64:");
        if (bundle == null) {
            System.out.println("Bundle is null.");
            return;
        }
        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;

        System.out.println(decryptor.decryptBundleByAsyOneWay(Base64.getDecoder().decode(bundle), prikey));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptAsyTwoWayBundle(BufferedReader br) {
        Encryptor encryptor = new Encryptor();
        String pubkeyB;
        String msg;
        String prikeyA;
        byte[] prikeyABytes = new byte[0];
        while (true) {
            try {
                pubkeyB = Inputer.inputString(br, "Input the recipient public key in hex:");
                if (pubkeyB.length() != 66) {
                    System.out.println("The public key should be 66 characters of hex.");
                    continue;
                }
                prikeyA = Inputer.inputString(br, "Input the sender's private key:");
                if (prikeyA == null) {
                    System.out.println("A private key is required.");
                    continue;
                }

                if (Base58.isBase58Encoded(prikeyA)) prikeyABytes = Base58.decode(prikeyA);
                else if (Hex.isHexString(prikeyA)) prikeyABytes = Hex.fromHex(prikeyA);
                msg = Inputer.inputString(br, "Input the msg:");
                if ("".equals(msg)) return;
                break;
            } catch (Exception e) {
                System.out.println("BufferedReader wrong.");
                return;
            }
        }
        byte[] bundle = encryptor.encryptByAsyTwoWayToBundle(msg.getBytes(), prikeyABytes, Hex.fromHex(pubkeyB));//ecc.encryptAsyTwoWayBundle(eccAesData.getMsg(),eccAesData.getPubkeyB(),eccAesData.getPrikeyA());
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }


    public static CryptoDataByte getEncryptedEccAesDataTwoWay(BufferedReader br) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        String pubkeyB;
        String msg;
        String prikeyA;
        pubkeyB = Inputer.inputString(br, "Input the recipient public key in hex:");
        if (pubkeyB.length() != 66) {
            System.out.println("The public key should be 66 characters of hex.");
            return null;
        }
        cryptoDataByte.setPubkeyB(Hex.fromHex(pubkeyB));
        String ask = "Input the sender's private Key:";
        prikeyA = Inputer.inputString(br, ask);
        if (prikeyA == null) return null;
        byte[] prikeyABytes = new byte[0];
        if (Base58.isBase58Encoded(prikeyA)) prikeyABytes = Base58.decode(prikeyA);
        else if (Hex.isHexString(prikeyA)) prikeyABytes = Hex.fromHex(prikeyA);
        cryptoDataByte.setPrikeyA(prikeyABytes);
        msg = Inputer.inputString(br, "Input the msg:");
        cryptoDataByte.setData(msg.getBytes());
        return cryptoDataByte;
    }

    public static void encryptAsyOneWayBundle(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = getEncryptedEccAesDataOneWay(br);
        if (cryptoDataByte == null) return;
        if (cryptoDataByte.getData() == null) {
            System.out.println("Error: no message.");
            return;
        }
        byte[] bundle = encryptor.encryptByAsyOneWayToBundle(cryptoDataByte.getData(), cryptoDataByte.getPubkeyB());
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptFileSymkey(BufferedReader br) {
        File encryptedFile = getAvailableFile(br);
        Decryptor decryptor = new Decryptor();
        if (encryptedFile == null || encryptedFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        if (symKey == null) return;
        CryptoDataByte result = decryptor.decryptFileBySymkey(encryptedFile.getName(), "decrypted" + encryptedFile.getName(), BytesUtils.hexCharArrayToByteArray(symKey));
        System.out.println(new String(result.getData()));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptFileWithSymkey(BufferedReader br) {
        File originalFile = getAvailableFile(br);
        if (originalFile == null || originalFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;

        String ask = "Input the symKey in hex:";
        char[] symKeyHex = Inputer.input32BytesKey(br, ask);
        if (symKeyHex == null) return;
        byte[] symKey = BytesUtils.hexCharArrayToByteArray(symKeyHex);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        String destFileName = Encryptor.makeEncryptedFileName(originalFile.getName());
        System.out.println(encryptor.encryptFileBySymkey(originalFile.getName(), destFileName, symKey));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptWithSymkeyFromBundle(BufferedReader br) {
        String bundle = Inputer.inputString(br, "Input the bundle in Base64:");
        if (bundle == null) {
            System.out.println("Bundle is null.");
            return;
        }
        byte[] bundleBytes;
        bundleBytes = Base64.getDecoder().decode(bundle);
        if (bundleBytes == null) return;

        Decryptor decryptor = new Decryptor();
        String ask = "Input the symKey in hex:";
        char[] symKeyHex = Inputer.input32BytesKey(br, ask);
        if (symKeyHex == null) return;
        byte[] symKey = BytesUtils.hexCharArrayToByteArray(symKeyHex);
        CryptoDataByte cryptoDataByte = decryptor.decryptBundleBySymkey(bundleBytes, symKey);

        System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptWithSymkeyToBundle(BufferedReader br) {
        String msg = Inputer.inputMsg(br);
        if (msg == null) return;
        String ask = "Input the symKey in hex:";
        char[] symKeyHex = Inputer.input32BytesKey(br, ask);
        if (symKeyHex == null) return;
        byte[] symKey = BytesUtils.hexCharArrayToByteArray(symKeyHex);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        byte[] bundle = encryptor.encryptToBundleBySymkey(msg.getBytes(), symKey);
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptFileAsy(byte[] prikey, BufferedReader br) {
        File encryptedFile = getAvailableFile(br);
        if (encryptedFile == null || encryptedFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;

        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;
        System.out.println("Input the recipient private key in hex:");
        Decryptor decryptor = new Decryptor();
        String plainFileName = Decryptor.recoverEncryptedFileName(encryptedFile.getName());
        CryptoDataByte cryptoDataByte = decryptor.decryptFileByAsyOneWay(encryptedFile.getName(), plainFileName, prikey);
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    public static void encryptFileAsy(BufferedReader br) {

        File originalFile = getAvailableFile(br);
        if (originalFile == null || originalFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;
        String pubkeyB;
        pubkeyB = KeyTools.inputPubkey(br);
        if (pubkeyB == null) return;
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        String dataFileName = originalFile.getName();
        CryptoDataByte cryptoDataByte = encryptor.encryptFileByAsyOneWay(dataFileName, Encryptor.makeEncryptedFileName(dataFileName), Hex.fromHex(pubkeyB));
        System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }

    public static void gainTimestamp() {
        long timestamp = System.currentTimeMillis();
        System.out.println(timestamp);
    }

    public static void encryptWithSymkeyToJson(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        String msg = Inputer.inputMsg(br);
        if (msg == null) return;
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        if (symKey == null) return;
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymkey(msg.getBytes(), BytesUtils.hexCharArrayToByteArray(symKey));
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    public static void decryptWithSymkeyFromJson(BufferedReader br) {

        String cipherJson = Inputer.inputString(br, "Input the cipher json string:");
        Decryptor decryptor = new Decryptor();
        String ask = "Input the symKey in hex:";
        byte[] symKey = Inputer.inputSymkey32(br, ask);
        if (symKey == null) return;
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymkey(cipherJson, symKey);
        System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptWithPasswordToJson(BufferedReader br) {

        String ask = "Input the password no longer than 64:";
        char[] password = Inputer.inputPassword(br, ask);
        if (password == null) return;
        System.out.println("Password:" + Arrays.toString(password));

        String msg = Inputer.inputString(br, "Input the plaintext:");
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptByPassword(msg.getBytes(), password);
        System.out.println(cryptoDataByte.toNiceJson());

        Menu.anyKeyToContinue(br);
    }

    public static void decryptWithPasswordFromJson(BufferedReader br) {
        String eccAesDataJson = Inputer.inputString(br, "Input the cipher json:");
        CryptoDataByte cryptoDataByte;
        while (true) {
            String ask = "Input the password no longer than 64:";
            char[] password = Inputer.inputPassword(br, ask);
            if (password == null) return;
            Decryptor decryptor = new Decryptor();

            cryptoDataByte = decryptor.decryptJsonByPassword(eccAesDataJson, password);
            if (cryptoDataByte.getCode() == 0) break;
            System.out.println("Wrong password. Try again.");
        }
        System.out.println(new String(cryptoDataByte.getData()));

        Menu.anyKeyToContinue(br);
    }

    public static void decryptAsyFromJson(byte[] prikey, BufferedReader br) {
        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;
        String eccAesDataJson = Inputer.inputString(br, "Input the json string of EccAesData:");

        decryptAsyJson(prikey, br, eccAesDataJson);

        Menu.anyKeyToContinue(br);

    }

    public static void decryptAsyJson(byte[] prikey, BufferedReader br, String eccAesDataJson) {
        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonByAsyOneWay(eccAesDataJson, prikey);
        System.out.println(new String(cryptoDataByte.getData()));
    }

    @Nullable
    public static byte[] checkPrikey(byte[] prikey, BufferedReader br) {
        if (prikey == null || Inputer.askIfYes(br, "Input a new private key?")) {
            System.out.println("Input the recipient private key in hex:");
            prikey = core.fch.Inputer.inputPrikeyHexOrBase58(br);
        }
        return prikey;
    }

    public static void decryptWithBtcEcc(byte[] prikey, BufferedReader br) {

        String cipher = Inputer.inputString(br, "Input the cipher:");
        Decryptor decryptor = new Decryptor();
        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;
        CryptoDataByte cryptoDataByte = decryptor.decrypt(cipher, prikey);
        if (cryptoDataByte.getCode() != 0) {
            System.out.println(cryptoDataByte.getMessage());
        } else System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptAsyToJson(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = getEncryptedEccAesDataOneWay(br);
        assert cryptoDataByte != null;
        cryptoDataByte = encryptor.encryptByAsyOneWay(cryptoDataByte.getData(), cryptoDataByte.getPubkeyB());
        if (cryptoDataByte == null) return;
        if (cryptoDataByte.getCode() != 0) {
            System.out.println(cryptoDataByte.getMessage());
        } else System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    public static CryptoDataByte getEncryptedEccAesDataOneWay(BufferedReader br) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        String pubkeyB;
        String msg;
        try {
            pubkeyB = Inputer.inputString(br, "Input the recipient public key in hex:");
            if (pubkeyB == null || pubkeyB.length() != 66) {
                System.out.println("The public key should be 66 characters of hex.");
                return null;
            }
            cryptoDataByte.setPubkeyB(Hex.fromHex(pubkeyB));
            msg = Inputer.inputString(br, "Input the msg:");
            if ("".equals(msg)) return null;
            cryptoDataByte.setData(msg.getBytes());
        } catch (Exception e) {
            System.out.println("BufferedReader wrong.");
            return null;
        }
        return cryptoDataByte;
    }

    public static void encryptWithBtcEcc(BufferedReader br) {
        String msg = Inputer.inputString(br, "Input the plaintext:");
        if (msg == null) return;
        String pubkey = KeyTools.inputPubkey(br);
        if (pubkey == null) return;

        Encryptor encryptor = new Encryptor(AlgorithmId.BitCore_EccAes256);
        CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(msg.getBytes(), Hex.fromHex(pubkey));
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    public static void printRandomInMultipleFormats(byte[] bytes) {
        // Convert to BigInteger (works for any length)
        BigInteger bigInt = new BigInteger(1, bytes); // Use 1 for positive number

        System.out.println("Base58:\n" + Base58.encode(bytes) + "\n");

        System.out.println("Integer:\n" + String.format("%,d", bigInt) + "\n");

        System.out.println("Hex:\n" + Hex.toHex(bytes) + "\n");

        System.out.println("Base64:\n" + Base64.getEncoder().encodeToString(bytes) + "\n");

        // Convert to Chinese characters (0x4E00-0x9FFF)
        StringBuilder chineseStr = new StringBuilder();
        int cjkRange = 0x9FFF - 0x4E00 + 1;

        // Process 12 bits at a time (4096 possibilities)
        for (int i = 0; i < bytes.length; i += 3) {
            if (i + 2 < bytes.length) {
                // Take 3 bytes (24 bits) and convert to two Chinese characters (12 bits each)
                int value1 = ((bytes[i] & 0xFF) << 4) | ((bytes[i + 1] & 0xF0) >> 4);
                int value2 = ((bytes[i + 1] & 0x0F) << 8) | (bytes[i + 2] & 0xFF);

                chineseStr.append(Character.toString(0x4E00 + (value1 % cjkRange)));
                chineseStr.append(Character.toString(0x4E00 + (value2 % cjkRange)));
            } else if (i + 1 < bytes.length) {
                // Handle last 2 bytes
                int value = ((bytes[i] & 0xFF) << 4) | ((bytes[i + 1] & 0xF0) >> 4);
                chineseStr.append(Character.toString(0x4E00 + (value % cjkRange)));
            } else {
                // Handle last byte
                int value = bytes[i] & 0xFF;
                chineseStr.append(Character.toString(0x4E00 + value));
            }
        }
        System.out.println("Chinese:\n" + chineseStr + "\n");
    }

    public static byte[] parseMultipleFormats(String input, String format) throws IllegalArgumentException {
        // Remove any whitespace and newlines
        input = input.trim();

        switch (format.toLowerCase()) {
            case "integer" -> {
                try {
                    BigInteger bigInt = new BigInteger(input);
                    // Convert back to positive bytes
                    byte[] bytes = bigInt.toByteArray();
                    // Remove leading 0 byte if present (from BigInteger's sign bit)
                    if (bytes[0] == 0) {
                        byte[] tmp = new byte[bytes.length - 1];
                        System.arraycopy(bytes, 1, tmp, 0, tmp.length);
                        return tmp;
                    }
                    return bytes;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid integer format");
                }
            }
            case "hex" -> {
                if (!Hex.isHexString(input)) {
                    throw new IllegalArgumentException("Invalid hex format");
                }
                return Hex.fromHex(input);
            }
            case "base58" -> {
                try {
                    return Base58.decode(input);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid Base58 format");
                }
            }
            case "base64" -> {
                try {
                    return Base64.getDecoder().decode(input);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid Base64 format");
                }
            }
            case "chinese" -> {
                try {
                    List<Byte> byteList = new ArrayList<>();

                    for (int i = 0; i < input.length(); i += 2) {
                        if (i + 1 < input.length()) {
                            // Convert two Chinese characters back to three bytes
                            int value1 = input.charAt(i) - 0x4E00;
                            int value2 = input.charAt(i + 1) - 0x4E00;

                            byteList.add((byte) ((value1 >> 4) & 0xFF));
                            byteList.add((byte) (((value1 & 0x0F) << 4) | ((value2 >> 8) & 0x0F)));
                            byteList.add((byte) (value2 & 0xFF));
                        } else {
                            // Handle last character
                            int value = input.charAt(i) - 0x4E00;
                            byteList.add((byte) (value & 0xFF));
                        }
                    }

                    byte[] result = new byte[byteList.size()];
                    for (int i = 0; i < byteList.size(); i++) {
                        result[i] = byteList.get(i);
                    }
                    return result;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid Chinese character format");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    public static void getRandom(BufferedReader br) {
        int len;
        while (true) {
            String input = Inputer.inputString(br, "Input the bytes length of the random you want. Enter to exit:");
            if ("".equals(input)) {
                return;
            }

            try {
                len = Integer.parseInt(input);
                break;
            } catch (Exception ignored) {
            }
        }

        byte[] bytes = BytesUtils.getRandomBytes(len);
        printRandomInMultipleFormats(bytes);
        Menu.anyKeyToContinue(br);
    }

    public static void hex32Base58(BufferedReader br) {
        String input;
        while (true) {
            input = Inputer.inputString(br, "Input 32 bytes hex or base58 string, enter to exit:");
            if ("".equals(input)) {
                return;
            }
            try {
                byte[] prikey = KeyTools.getPrikey32(input);
                if (prikey == null) {
                    System.out.println("Only 64 chars hex or 52 chars base58 string can be accepted.");
                    continue;
                }
                String hex = Hex.toHex(prikey);
                System.out.println("----");
                System.out.println("- Private Key in Hex:\n" + hex);
                System.out.println("- Private Key in Base58:\n" + KeyTools.prikey32To38WifCompressed(hex));
                System.out.println("- Private Key in Base58 old style:\n" + KeyTools.prikey32To37(hex));
                String pubkey = Hex.toHex(KeyTools.prikeyToPubkey(prikey));
                printPubkeyAndAddresses(pubkey);
            } catch (Exception e) {
                System.out.println("Only 64 chars hex or 52 chars base58 string can be accepted.");
            }
        }
    }

    public static void findNiceFid(BufferedReader br) {
        String input;
        SimpleDateFormat sdf = new SimpleDateFormat();
        Date begin = new Date();
        System.out.println(sdf.format(begin));
        while (true) {
            input = Inputer.inputString(br, "Input 4 characters you want them to be in the end of your FID, enter to exit:");
            if ("".equals(input)) {
                return;
            }
            if (input.length() != 4) {
                System.out.println("Input 4 characters you want them be in the end of your fid:");
                continue;
            }
            if (!Base58.isBase58Encoded(input)) {
                System.out.println("It's not a Base58 encoded. The string can't contain '0', 'O', 'l', 'L', '+', '/'.");
                continue;
            }
            break;
        }
        long i = 0;
        long j = 0;
        System.out.println("Finding...");
        while (true) {
            ECKey ecKey = new ECKey();
            String fid = KeyTools.pubkeyToFchAddr(ecKey.getPubKey());
            if (fid.substring(30).equals(input)) {
                System.out.println("----");
                System.out.println("FID:" + fid);
                System.out.println("Pubkey: " + ecKey.getPublicKeyAsHex());
                System.out.println("PrikeyHex: " + ecKey.getPrivateKeyAsHex());
                System.out.println("PrikeyBase58: " + ecKey.getPrivateKeyEncoded(MainNetParams.get()));
                System.out.println("----");
                System.out.println("Begin at: " + sdf.format(begin));
                Date end = new Date();
                System.out.println("End at: " + sdf.format(end));
                System.out.println("----");
                break;
            }
            i++;
            if (i % 1000000 == 0) {
                j++;
                System.out.println(sdf.format(new Date()) + ": " + j + " million tryings.");
            }
        }
    }

    public static void pubkeyToAddrs(BufferedReader br) throws Exception {
        String pubkey = Inputer.inputString(br, "Input the public key, enter to exit:");

        if ("".equals(pubkey)) {
            return;
        }

        printPubkeyAndAddresses(pubkey);

        Menu.anyKeyToContinue(br);
    }

    public static void printPubkeyAndAddresses(String pubkey) {
        pubkey = KeyTools.getPubkey33(pubkey);
        KeyTools.showPubkeys(pubkey);
        Map<String, String> addrMap = KeyTools.pubkeyToAddresses(pubkey);
        if (addrMap == null) {
            System.out.println("Failed to make addresses from the public key.");
            return;
        }

        StartTools.showAddressed(addrMap);
    }

    public static void sha256File(BufferedReader br) {
        while (true) {

            File file = inputFilePath(br);
            if (file == null) return;
            byte[] hash = Hash.sha256Stream(file);
            if (hash == null) {
                System.out.println("Error calculating SHA-256 hash.");
                break;
            }
            System.out.println("----");
            System.out.println("file:" + file.getAbsolutePath());
            System.out.println("sha256:" + Hex.toHex(hash));
            System.out.println("----");
            Menu.anyKeyToContinue(br);
        }
    }

    public static void sha256x2File(BufferedReader br) throws IOException {
        while (true) {
            File file = inputFilePath(br);
            if (file == null) return;
            String hash = Hash.sha256x2(file);
            if (hash == null) {
                System.out.println("Error calculating SHA-256 hash.");
                break;
            }
            showHash(br, SHA256X2, file.getAbsolutePath(), hash);
        }
    }

    public static void sha256String(BufferedReader br) {
        String text = Inputer.inputStringMultiLine(br);
        if (text == null) return;
        String hash = Hash.sha256(text);
        showHash(br, Values.SHA256, text, hash);
    }

    public static void showHash(BufferedReader br, String alg, String text, String hash) {
        System.out.println("----");
        System.out.println("Source:");
        System.out.println(text);
        System.out.println(alg + ":" + hash);
        System.out.println("----");
        Menu.anyKeyToContinue(br);
    }

    public static void sha256x2String(BufferedReader br) {
        String text = Inputer.inputStringMultiLine(br);
        if (text == null) return;
        String hash = Hash.sha256x2(text);
        showHash(br, SHA256X2, text, hash);
    }

    public static void symKeySign(BufferedReader br) {
        String symKey = Inputer.inputString(br, "Input the symKey in hex, enter to exit:");
        if ("".equals(symKey)) {
            return;
        }

        while (true) {
            String text = Inputer.inputStringMultiLine(br);
            if (text == null) return;
            if ("q".equals(text)) return;
            String sign = getSign(symKey, text);
            System.out.println("----");
            System.out.println("Signature:");
            System.out.println("----");
            System.out.println(sign);
            System.out.println("----");
        }
    }

    public static void encryptBitcoreToBundle(BufferedReader br2) {
        String msg = Inputer.inputString(br2, "Input the message to be encrypted:");
        String pubkey = Inputer.inputString(br2, "Input the public key in hex:");
        byte[] encrypted = Bitcore.encrypt(msg.getBytes(), Hex.fromHex(pubkey));
        if (encrypted == null) {
            System.out.println("Encrypt failed.");
            return;
        }
        System.out.println("Cipher: \n" + Base64.getEncoder().encodeToString(encrypted));

    }

    public static void decryptBitcoreFromBundle(byte[] prikey, BufferedReader br) {
        String cipher = Inputer.inputString(br, "Input the cipher:");
        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;
        byte[] decrypted;
        try {
            decrypted = Bitcore.decrypt(Base64.getDecoder().decode(cipher), prikey);
            if (decrypted == null) {
                System.out.println("Decrypt failed.");
                return;
            }
            System.out.println("Decrypted: \n" + new String(decrypted));
        } catch (Exception e) {
            System.out.println("Decrypt failed.");
            e.printStackTrace();
        }
    }

    public static void broadcastTx(ApipClient apipClient, BufferedReader br) {
        String rawTx = Inputer.inputString(br, "Input the raw tx hex or Base64:");
        String result = TxCreator.decodeTxFch(rawTx, FchMainNetwork.MAINNETWORK);
        System.out.println("Check your Tx:\n" + result);
        Menu.anyKeyToContinue(br);
        if (askIfYes(br, "Something wrong? y/other to broadcast")) return;
        result = apipClient.broadcastTx(rawTx, RequestMethod.GET, AuthType.FREE);
        System.out.println(result);
        Menu.anyKeyToContinue(br);
    }

    public static void decodeTxFch(BufferedReader br2) {
        String rawTx = Inputer.inputString(br2, "Input the raw tx hex or Base64:");
        String result = TxCreator.decodeTxFch(rawTx, FchMainNetwork.MAINNETWORK);
        System.out.println(result);
        Menu.anyKeyToContinue(br2);
    }

    public static void signRawTx(String userPrikeyCipher, byte[] symKey, BufferedReader br2) {
        String rawTx = Inputer.inputString(br2, "Input the raw tx hex or Base64:");
        String decodeJson = Wallet.decodeTxFch(rawTx);
        if (decodeJson == null) {
            System.out.println("Failed to decoded the TX.");
            return;
        }
        System.out.println("Decoded:");
        System.out.println(decodeJson);
        Menu.anyKeyToContinue(br2);
        byte[] prikey = Decryptor.decryptPrikey(userPrikeyCipher, symKey);
        String result = Wallet.signRawTx(rawTx, prikey);
        if (result == null) return;
        System.out.println("Signed by " + KeyTools.prikeyToFid(prikey) + ".");
        System.out.println("Base64:\n\t" + result);
        System.out.println("Hex:\n\t" + StringUtils.base64ToHex(result));
        Menu.anyKeyToContinue(br2);
    }

    public static void multiSign(Settings settings) {
        BufferedReader br = settings.getBr();
        ApipClient apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);

        Menu menu = new Menu("Multi Signature");
        menu.add("List my multisig FIDs", () -> myMultiFids(settings.getMainFid(), apipClient, br));
        menu.add("Create multisig FID", () -> createFid(apipClient, br));
        menu.add("Show multisig FID", () -> showFid(apipClient, br));
        menu.add("Creat a multisig raw TX", () -> createTx(apipClient, br));
        menu.add("Sign a multisig raw TX", () -> multiSignTx(settings));
        menu.add("Build the final multisig TX", () -> buildSignedTx(br));
        menu.showAndSelect(br);
    }

    public static void myMultiFids(String fid, ApipClient apipClient, BufferedReader br) {
        List<Multisign> multisignList = apipClient.myMultisigns(fid);
        if (multisignList == null || multisignList.isEmpty()) {
            System.out.println("No multiSign address yet.");
            Menu.anyKeyToContinue(br);
            return;
        }
        System.out.println("\nMultisig addresses associated with your FID:");
        System.out.println("----------------------------------------------");
        
        for (int i = 0; i < multisignList.size(); i++) {
            Multisign multisign = multisignList.get(i);
            System.out.printf("%d. ID: %s\n", (i + 1), multisign.getId());
            
            // Display additional information if available
            if (multisign.getM() != null && multisign.getN() != null) {
                System.out.printf("   M of N: %d of %d\n", multisign.getM(), multisign.getN());
            }
            
            if (multisign.getFids() != null && !multisign.getFids().isEmpty()) {
                System.out.println("   Members: " + String.join(", ", multisign.getFids()));
            }
            
            System.out.println();
        }
        Menu.anyKeyToContinue(br);
    }

    public static void buildSignedTx(BufferedReader br) {
        String[] signedData = Inputer.inputStringArray(br, "Input the signed data. Enter to end:", 0);

        String signedTx = TxCreator.buildSignedTx(signedData, FchMainNetwork.MAINNETWORK);
        if (signedTx == null) return;
        System.out.println(signedTx);
        Menu.anyKeyToContinue(br);
    }

    public static void multiSignTx(Settings settings) {
        BufferedReader br = settings.getBr();

        byte[] prikey;

        while (true) {
            if (!Inputer.askIfYes(br,"Sign by other FID?")) {
                Decryptor decryptor = new Decryptor();
                CryptoDataByte cryptoDataByte = decryptor.decrypt(settings.getMyPrikeyCipher(), settings.getSymkey());
                if (cryptoDataByte == null || cryptoDataByte.getCode() != 0) {
                    System.out.println("Failed to decrypt prikey.");
                    return;
                }
                prikey = cryptoDataByte.getData();
            } else {
                try {
                    prikey = KeyTools.importOrCreatePrikey(br);
                } catch (Exception e) {
                    System.out.println("Wrong input. Try again.");
                    continue;
                }
            }

            if (prikey == null) {
                System.out.println("Failed to get prikey.");
                return;
            }

            System.out.println("Set the unsigned data json string. ");
            String multiSignDataJson = Inputer.inputStringMultiLine(br);

            showRawMultiSignTxInfo(multiSignDataJson, br);

            System.out.println("Multisig data signed by " + prikeyToFid(prikey) + ":");
            Shower.printUnderline(60);
            String signedSchnorrMultiSignTx = TxCreator.signSchnorrMultiSignTx(multiSignDataJson, prikey, FchMainNetwork.MAINNETWORK);
            if(signedSchnorrMultiSignTx==null)return;
            System.out.println(signedSchnorrMultiSignTx);
            if(Inputer.askIfYes(br,"Show the QR codes?"))
                QRCodeUtils.generateQRCode(signedSchnorrMultiSignTx);
            BytesUtils.clearByteArray(prikey);
            Shower.printUnderline(60);
            if(!Inputer.askIfYes(br,"Sign another?"))return;
        }
    }

    public static void showRawMultiSignTxInfo(String multiSignDataJson, BufferedReader br) {
        RawTxInfo multiSigData = RawTxInfo.fromJson(multiSignDataJson,RawTxInfo.class);

        byte[] rawTx = multiSigData.getRawTx();
        Map<String, Object> result;
        try {
            result = RawTxParser.parseRawTxBytes(rawTx);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        List<Cash> spendCashList = (List<Cash>) result.get(spendCashMapKey);
        List<Cash> issuredCashList = (List<Cash>) result.get(newCashMapKey);
        String msg = (String) result.get(Strings.OPRETURN);

        Map<String, Cash> spendCashMap = new HashMap<>();
        for (Cash cash : multiSigData.getInputs()) {
            if (cash.getId() == null && cash.getBirthTxId() != null && cash.getBirthIndex() != null)
                cash.makeId(cash.getBirthTxId(), cash.getBirthIndex());
            spendCashMap.put(cash.getId(), cash);
        }

        System.out.println("You are spending:");
        Shower.printUnderline(140);
        System.out.print(Shower.formatString("cashId", 68));
        System.out.print(Shower.formatString("owner", 38));
        System.out.println(Shower.formatString("core/fch", 20));
        Shower.printUnderline(140);
        for (Cash cash : spendCashList) {
            Cash niceCash = spendCashMap.get(cash.getId());
            if (niceCash == null) {
                System.out.println("Warning： The cash " + cash.getId() + " in the rawTx is unfounded.");
                return;
            }
            System.out.print(Shower.formatString(niceCash.getId(), 68));
            System.out.print(Shower.formatString(niceCash.getOwner(), 38));
            System.out.println(Shower.formatString(String.valueOf(FchUtils.satoshiToCoin(niceCash.getValue())), 20));
        }
        Shower.printUnderline(140);
        System.out.println("You are paying:");
        Shower.printUnderline(60);
        System.out.print(Shower.formatString("FID", 38));
        System.out.println(Shower.formatString("core/fch", 20));
        Shower.printUnderline(60);
        for (Cash cash : issuredCashList) {
            if (cash.getOwner().equals(UpStrings.OP_RETURN)) continue;
            System.out.print(Shower.formatString(cash.getOwner(), 38));
            System.out.println(Shower.formatString(String.valueOf(FchUtils.satoshiToCoin(cash.getValue())), 20));
        }
        Shower.printUnderline(60);

        if (msg != null) {
            System.out.println("The message in OP_RETURN is: ");
            Shower.printUnderline(60);
            System.out.println(msg);
            Shower.printUnderline(60);
        }
        Menu.anyKeyToContinue(br);
    }

    public static void createTx(ApipClient apipClient, BufferedReader br) {

        String fid = inputGoodFid(br, "Input the multisig fid:");
        Multisign multisign;
        Map<String, Multisign> p2shMap = apipClient.multisignByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, fid);
        if (p2shMap == null || p2shMap.get(fid)==null) {
            System.out.println(fid + " is not found.");
            if(Inputer.askIfYes(br,"Input the P2SH or the redeem script?")){
                String redeem = Inputer.inputStringMultiLine(br);
                try {
                    if (Hex.isHexString(redeem)) {
                        multisign = Multisign.parseMultisignRedeemScript(redeem);
                    } else multisign = Multisign.fromJson(redeem, Multisign.class);
                }catch (Exception e){
                    System.out.println("Failed to import multisig FID information:"+e.getMessage());
                    Menu.anyKeyToContinue(br);
                    return;
                }
            }else return;
        }else multisign = p2shMap.get(fid);

        System.out.println(JsonUtils.toNiceJson(multisign));

        List<SendTo> sendToList = SendTo.inputSendToList(br);
        String msg = Inputer.inputString(br, "Input the message for OpReturn. Enter to ignore:");
        int msgLen;
        byte[] msgBytes;
        if ("".equals(msg)) {
            msgLen = 0;
        } else {
            msgBytes = msg.getBytes();
            msgLen = msgBytes.length;
        }
        double sum = 0;
        for (SendTo sendTo : sendToList) {
            sum += sendTo.getAmount();
        }

        List<Cash> cashList = apipClient.cashValid(fid, sum, null, sendToList.size(), msgLen, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(cashList==null || cashList.isEmpty()){
            System.out.println("Failed to get valid cashes of "+fid);
            Menu.anyKeyToContinue(br);
            return;
        }
        Transaction transaction = TxCreator.createUnsignedTx(cashList, sendToList, msg, multisign, DEFAULT_FEE_RATE, null, FchMainNetwork.MAINNETWORK);
        if(transaction==null){
            System.out.println("Failed to create unsigned TX.");
            Menu.anyKeyToContinue(br);
            return;
        }
        RawTxInfo multiSignData = new RawTxInfo(transaction.bitcoinSerialize(), multisign, cashList);

        CryptoSign.showMultiUnsignedResult(br, multisign, multiSignData);
    }

    public static void createFid(ApipClient apipClient, BufferedReader br) {
        List<String> fidList = new ArrayList<>();

        do {
            List<Cid> cidList = apipClient.searchCidList(br, true);
            fidList.addAll(cidList.stream().map(Cid::getId).toList());
        } while (Inputer.askIfYes(br, "Add more?"));
        if (fidList.isEmpty()) return;
        if (fidList.size() > Constants.MAXIMUM_MULTI_SIGNER) {
            System.out.println("The FIDs of a MultiSig can not be more than 16.");
            return;
        }

        String[] fids = fidList.toArray(new String[0]);
        int m = Inputer.inputInt(br, "How many signatures is required? ", Constants.MAXIMUM_MULTI_SIGNER);

        Map<String, Cid> fidMap = apipClient.fidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, fids);
        if (fidMap == null) return;

        List<byte[]> pubkeyList = new ArrayList<>();
        for (String fid : fids) {
            String pubkey = fidMap.get(fid).getPubkey();
            pubkeyList.add(HexFormat.of().parseHex(pubkey));
        }

        Multisign multisign = TxCreator.createMultisign(pubkeyList, m);

        Shower.printUnderline(10);
        System.out.println("The multisig information is: \n" + JsonUtils.toNiceJson(multisign));

        Shower.printUnderline(10);
        if (multisign == null) return;
        String fid = multisign.getId();
        System.out.println("Your multisig FID: \n" + fid);
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
    }

    public static void showFid(ApipClient apipClient, BufferedReader br) {
        String fid = inputGoodFid(br, "Input the multisig FID:");
        if (fid == null) return;
        if (!fid.startsWith("3")) {
            System.out.println("A multisig FID should start with '3'.");
            return;
        }
        System.out.println("Requesting APIP from " + apipClient.getUrlHead());
        Map<String, Multisign> p2shMap = apipClient.multisignByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, fid);
        if(p2shMap==null || p2shMap.isEmpty()){
            System.out.println("The redeem script of this multisig FID hasn't been shown on chain.");
            return;
        }
        Multisign multisign = p2shMap.get(fid);

        if (multisign == null) {
            System.out.println(fid + " is not found.");
            return;
        }
        Shower.printUnderline(10);
        System.out.println("Multisig:");
        System.out.println(JsonUtils.toNiceJson(multisign));
        Shower.printUnderline(10);
        System.out.println("The members:");

        Map<String, Cid> cidInfoMap = apipClient.cidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, multisign.getFids().toArray(new String[0]));
        if (cidInfoMap == null) {
            return;
        }
        System.out.println(JsonUtils.toNiceJson(cidInfoMap));
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
    }

    public static void pubkeyConvert(String pubkey, BufferedReader br) {
        CryptoSign.pubkeyInMultiFormats(br, pubkey);
    }

    public static void addressConvert(String myPrikeyCipher, byte[] symkey, BufferedReader br) {
        Map<String, String> addrMap;
        String pubkey;

        while (true) {
            System.out.println("Enter to convert the local FID, or input the address or pubkey:");
            String input = Inputer.inputString(br);
            if ("".equals(input)) {
                byte[] prikey = Decryptor.decryptPrikey(myPrikeyCipher, symkey);
                pubkey = Hex.toHex(KeyTools.prikeyToPubkey(prikey));
                addrMap = KeyTools.pubkeyToAddresses(pubkey);
                break;
            }

            addrMap = convert(input);
            if(addrMap!=null) break;
            System.out.println("Wrong FID or pubkey. Try again.");
        }
        if (addrMap == null) return;
        Shower.printUnderline(10);
        System.out.println(JsonUtils.toNiceJson(addrMap));
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
    }

    public static Map<String, String> convert(String fidOrPubkey) {
        Map<String, String> addrMap = null;
        if (fidOrPubkey.length() < 66) {
            addrMap = convertAddresses(fidOrPubkey);

        } else if (KeyTools.isValidPubkey(fidOrPubkey)) {
            String pubkey = KeyTools.getPubkey33(fidOrPubkey);
            addrMap = KeyTools.pubkeyToAddresses(pubkey);
        }
        return addrMap;
    }


    public static Map<String,String> convertAddresses(String id){
        Map<String,String>addrMap;
       if(id.startsWith("F")
               || id.startsWith("1")
               || id.startsWith("D")
               || id.startsWith("L")) {
           byte[] hash160 = KeyTools.addrToHash160(id);
           addrMap = KeyTools.hash160ToAddresses(hash160);
       } else if(id.startsWith("bc")) {
           byte[] hash160 = KeyTools.bech32BtcToHash160(id);
           addrMap = KeyTools.hash160ToAddresses(hash160);
       } else if(id.startsWith("bitcoincash") || id.startsWith("bch")||id.startsWith("q")||id.startsWith("p")) {
           byte[] hash160 = KeyTools.bech32BchToHash160(id);
           addrMap = KeyTools.hash160ToAddresses(hash160);
       } else{
           System.out.println("Wrong Address. Try again.");
           return null;
       }
       return addrMap;
    }

    public static void prikeyConvert(String userPrikeyCipher, byte[] symkey, BufferedReader br) {
        byte[] prikey = decryptPrikey(userPrikeyCipher, symkey, br);
        if(prikey== null)return;
        showPrikeyInfo(br, prikey);
    }

//    public static void signMsgEcdsa(String userPrikeyCipher, byte[] symKey, BufferedReader br) {
//        AlgorithmId alg = ;
//
//        byte[] prikey = decryptPrikey(userPrikeyCipher, symKey, br);
//        if(prikey== null)return;
//        String signer = prikeyToFid(prikey);
//           System.out.println("The signer is :" + signer);
//
//           System.out.println("Input the message to be sign. Enter to exit:");
//           String msg = Inputer.inputString(br);
//           if ("".equals(msg)) return;
//
//           Signature signature = new Signature();
//        signature.sign(msg, prikey, alg);
//           System.out.println("Signature:");
//           Shower.printUnderline(10);
//           System.out.println(signature.toNiceJson());
//           Shower.printUnderline(10);
//           Menu.anyKeyToContinue(br);
//       }

    @Nullable
    private static byte[] decryptPrikey(String userPrikeyCipher, byte[] symKey, BufferedReader br) {
        byte[] prikey;
        System.out.println("By a new FID? 'y' to confirm, others to use the local prikey:");
        String input = Inputer.inputString(br);
        if ("y".equals(input)) {
            prikey = KeyTools.importOrCreatePrikey(br);
        } else prikey = Decryptor.decryptPrikey(userPrikeyCipher, symKey);
        if(prikey==null){
            System.out.println("Failed to convert prikey.");
        }
        return prikey;
    }

    public static void signMsg(String userPrikeyCipher, AlgorithmId alg, byte[] symKey, BufferedReader br) {

        byte[] prikey = decryptPrikey(userPrikeyCipher, symKey, br);
        if(prikey== null)return;
        String signer = prikeyToFid(prikey);
           System.out.println("The signer is :" + signer);

           System.out.println("Input the message to be sign. Enter to exit:");
           String msg = Inputer.inputString(br);
           if ("".equals(msg)) return;

           Signature signature = new Signature();
           signature.sign(msg, prikey, alg);
           System.out.println("Signature:");
           Shower.printUnderline(10);
           System.out.println(signature.toNiceJson());
           Shower.printUnderline(10);
           Menu.anyKeyToContinue(br);
       }

    public static void verify(BufferedReader br) {
           System.out.println("Set signature.");
           String input = Inputer.inputStringMultiLine(br);
           if (input == null || "".equals(input)) return;

           Signature signature = null;

           try {
               signature = Signature.parseSignature(input);

               if(signature!=null && signature.getKey()==null && signature.getAlg().equals(AlgorithmId.FC_Sha256SymSignMsg_No1_NrC7)){
                   byte[] key = Inputer.inputSymkey32(br,"Input the symKey to verify the signature:");
                   signature.setKey(key);
               }
           }catch (Exception e){
               System.out.println("Failed to read signature:"+e.getMessage());
           }

           if (signature == null) {
               System.out.println("Parse signature wrong.");
               return;
           }

           System.out.println("Resulet:"+signature.verify());
           Menu.anyKeyToContinue(br);
       }

    public static void keyAndAddress(String pubkey, String myPrikeyCipher, byte[] symKey, BufferedReader br) {
           Menu menu = new Menu("Key & Address");
           menu
           .add("Generate Random")
           .add("Find Nice FID")
           .add("Prikey Convert")
           .add("Pubkey Convert")
           .add("Address Convert");

           while (true) {
               System.out.println(" << Key & Address >>");
               menu.show();
               int choice = menu.choose(br);
               switch (choice) {
                   case 1 -> getRandom(br);
                   case 2 -> findNiceFid(br);
                   case 3 -> prikeyConvert(myPrikeyCipher,symKey, br);
                   case 4 -> pubkeyConvert(pubkey, br);
                   case 5 -> addressConvert(myPrikeyCipher,symKey, br);
                   case 0 -> {
                       return;
                   }
               }
           }
       }

    public static void signAndVerify(String userPrikeyCipher, byte[] symKey, BufferedReader br) {
           Menu menu = new Menu();
           menu.add("Sign Message Ecdsa")
               .add("Sign Message Schnorr")
               .add("Sign with Symkey")
               .add("Verify Signature");

           while (true) {
               System.out.println(" << Sign & Verify >>");
               menu.show();
               int choice = menu.choose(br);
               switch (choice) {
                   case 1 -> signMsg(userPrikeyCipher, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, symKey, br);
                   case 2 -> signMsg(userPrikeyCipher, AlgorithmId.FC_SchnorrSignMsg_No1_NrC7, symKey, br);
                   case 3 -> signWithSymkey(br);
                   case 4 -> verify(br);
                   case 0 -> {
                       return;
                   }
               }
           }
       }

    public static void signWithSymkey(BufferedReader br) {
           System.out.println("Input the symKey in hex, enter to exit:");
           String symKeyHex = Inputer.inputString(br);
           if("".equals(symKeyHex)) return;

           while(true) {
               String text = Inputer.inputStringMultiLine(br);
               if(text==null)break;
               if("".equals(text)) break;
               String sign = getSign(symKeyHex, text);
               System.out.println("----");
               System.out.println("Signature:");
               System.out.println("----");
               System.out.println(sign);
               System.out.println("----");
           }
       }

    public static void encryptAndDecrypt(String userPrikeyCipher, byte[] symKey, BufferedReader br) {
           Menu menu = new Menu("Encrypt & Decrypt");
           menu.add("Encrypt with symKey to json")
                .add("Encrypt with symKey to bundle")
                .add("Encrypt with password to json")
                .add("Encrypt with password to bundle")
                .add("Encrypt with public key EccAes256K1P7 to json")
                .add("Encrypt with public key EccAes256K1P7 to bundle one way")
                .add("Encrypt with public key EccAes256K1P7 to bundle two way")
                .add("Encrypt file with symKey EccAes256K1P7 to json")
                .add("Encrypt file with public key EccAes256K1P7 to json")
                .add("Encrypt with public key Bitcore-Eceis to bundle one way")
                .add("Decrypt with symKey from json")
                .add("Decrypt with symKey from bundle")
                .add("Decrypt with password from json")
                .add("Decrypt with password from bundle")
                .add("Decrypt with private key EccAes256K1P7 from json")
                .add("Decrypt with private key EccAes256K1P7 from bundle one way")
                .add("Decrypt with private key EccAes256K1P7 from bundle two way")
                .add("Decrypt file with symKey EccAes256K1P7 from json")
                .add("Decrypt file with private key EccAes256K1P7 from json")
                .add("Decrypt Bitcore-Eceis from bundle");
            byte[] prikey = Decryptor.decryptPrikey(userPrikeyCipher, symKey);
           while (true) {
               System.out.println(" << Encrypt & Decrypt >>");
               menu.show();
               int choice = menu.choose(br);
               try {
                   switch (choice) {
                       case 1 -> encryptWithSymkeyToJson(br);
                       case 2 -> encryptWithSymkeyToBundle(br);
                       case 3 -> encryptWithPasswordToJson(br);
                       case 4 -> encryptWithPasswordBundle(br);
                       case 5 -> encryptAsyToJson(br);
                       case 6 -> encryptAsyOneWayBundle(br);
                       case 7 -> encryptAsyTwoWayBundle(br);
                       case 8 -> encryptFileWithSymkey(br);
                       case 9 -> encryptFileAsy(br);
                       case 10 -> encryptBitcoreToBundle(br);
                       case 11 -> decryptWithSymkeyFromJson(br);
                       case 12 -> decryptWithSymkeyFromBundle(br);
                       case 13 -> decryptWithPasswordFromJson(br);
                       case 14 -> decryptWithPasswordFromBundle(br);
                       case 15 -> decryptAsyFromJson(prikey, br);
                       case Constants.MAXIMUM_MULTI_SIGNER -> decryptAsyOneWayFromBundle(prikey, br);
                       case 17 -> decryptAsyTwoWayFromBundle(prikey, br);
                       case 18 -> decryptFileSymkey(br);
                       case 19 -> decryptFileAsy(prikey, br);
                       case 20 -> decryptBitcoreFromBundle(prikey,br);
                       case 0 -> {
                           return;
                       }
                       default -> {
                           System.out.println("Unexpected value: " + choice);
                           return;
                       }
                    }
                    Menu.anyKeyToContinue(br);
               } catch (Exception e) {
                   System.out.println("Error:"+e.getMessage());
               }
           }
       }


}
