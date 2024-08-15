package crypto.old;


import appTools.Inputer;
import appTools.Menu;
import constants.Constants;
import crypto.*;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.FileTools;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.MainNetParams;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;


public class StartTools {

    public static void main(String[] args) throws Exception {

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Menu menu = new Menu();
        ArrayList<String> itemList = new ArrayList<>();
        itemList.add("Generate a random");
        itemList.add("Get addresses of a pubKey");
        itemList.add("Find a nice FID ending with");
        itemList.add("Swap priKey Hex32 and Base58");

        itemList.add("Sha256-string");
        itemList.add("Sha256-file");
        itemList.add("Sha256x2-string");
        itemList.add("Sha256x2-file");

        itemList.add("Sign string with symKey sha256x2");

        itemList.add("Encrypt with symKey");
        itemList.add("Encrypt with symKey to bundle");
        itemList.add("Encrypt with password");
        itemList.add("Encrypt with password to bundle");
        itemList.add("Encrypt with public key EccAes256K1P7");
        itemList.add("Encrypt with public key EccAes256K1P7 to bundle one way");
        itemList.add("Encrypt with public key EccAes256K1P7 to bundle two way");
        itemList.add("Encrypt file with symKey EccAes256K1P7");
        itemList.add("Encrypt file with public key EccAes256K1P7");

        itemList.add("Decrypt with symKey");
        itemList.add("Decrypt with symKey from bundle");
        itemList.add("Decrypt with password");
        itemList.add("Decrypt with password from bundle");
        itemList.add("Decrypt with private key EccAes256K1P7");
        itemList.add("Encrypt with private key EccAes256K1P7 from bundle one way");
        itemList.add("Encrypt with private key EccAes256K1P7 from bundle two way");
        itemList.add("Decrypt file with symKey EccAes256K1P7");
        itemList.add("Decrypt file with private key EccAes256K1P7");
        itemList.add("Timestamp now");
        menu.add(itemList);

        while (true) {
            System.out.println("<<FreeConsensus Tools>> v1.0.0 by No1_NrC7");
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> getRandom(br);
                case 2 -> pubKeyToAddrs(br);
                case 3 -> findNiceFid(br);
                case 4 -> hex32Base58(br);
                case 5 -> sha256String(br);
                case 6 -> sha256File(br);
                case 7 -> sha256x2String(br);
                case 8 -> sha256x2File(br);
                case 9 -> symKeySign(br);
                case 10 -> encryptWithSymKey(br);
                case 11 -> encryptWithSymKeyBundle(br);
                case 12 -> encryptWithPassword(br);
                case 13 -> encryptWithPasswordBundle(br);
                case 14 -> encryptAsy(br);
                case 15 -> encryptAsyOneWayBundle(br);
                case 16 -> encryptAsyTwoWayBundle(br);
                case 17 -> encryptFileWithSymKey(br);
                case 18 -> encryptFileAsy(br);
                case 19 -> decryptWithSymKey(br);
                case 20 -> decryptWithSymKeyBundle(br);
                case 21 -> decryptWithPassword(br);
                case 22 -> decryptWithPasswordBundle(br);
                case 23 -> decryptAsy(br);
                case 24 -> decryptAsyOneWayBundle(br);
                case 25 -> decryptAsyTwoWayBundle(br);
                case 26 -> decryptFileSymKey(br);
                case 27 -> decryptFileAsy(br);
                case 28 -> {
                    gainTimestamp();
                    Menu.anyKeyToContinue(br);
                }
                case 0 -> {
                    br.close();
                    System.out.println("Bye.");
                    return;
                }
            }
        }
    }

    private static void decryptWithPasswordBundle(BufferedReader br) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        try {
            System.out.println("Input the bundle in Base64:");
            String bundle = br.readLine();
            if (bundle == null) {
                System.out.println("Bundle is null.");
                return;
            }
            String ask = "Input the password:";
            char[] password = Inputer.inputPassword(br, ask);
            byte[] bundleBytes = Base64.getDecoder().decode(bundle);
            byte[] passwordBytes = BytesTools.utf8CharArrayToByteArray(password);
            byte[] msgBytes = ecc.decryptPasswordBundle(bundleBytes, passwordBytes);
            System.out.println(new String(msgBytes));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void encryptWithPasswordBundle(BufferedReader br) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        String msg = Inputer.inputMsg(br);
        String ask = "Input the password:";
        char[] password = Inputer.inputPassword(br, ask);
        assert msg != null;
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] passwordBytes = BytesTools.utf8CharArrayToByteArray(password);
        byte[] bundle = ecc.encryptPasswordBundle(msgBytes, passwordBytes);
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    private static void decryptAsyTwoWayBundle(BufferedReader br) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        try {
            System.out.println("Input the bundle in Base64:");
            String bundle = br.readLine();
            if (bundle == null) {
                System.out.println("Bundle is null.");
                return;
            }

            System.out.println("Input the pubKey in hex:");

            String pubKey = br.readLine();

            String ask = "Input the priKey in hex:";
            char[] priKey = Inputer.input32BytesKey(br, ask);

            System.out.println(ecc.decryptAsyTwoWayBundle(bundle, pubKey, priKey));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Menu.anyKeyToContinue(br);
    }

    private static void decryptAsyOneWayBundle(BufferedReader br) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        try {
            System.out.println("Input the bundle in Base64:");
            String bundle = br.readLine();
            if (bundle == null) {
                System.out.println("Bundle is null.");
                return;
            }
            String ask = "Input the priKey in hex:";
            char[] priKey = Inputer.input32BytesKey(br, ask);

            System.out.println(ecc.decryptAsyOneWayBundle(bundle, priKey));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Menu.anyKeyToContinue(br);
    }

    private static void encryptAsyTwoWayBundle(BufferedReader br) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataStr cryptoDataStr = getEncryptedEccAesDataTwoWay(br);
        if (cryptoDataStr.getData() == null) {
            System.out.println("Error: no message.");
            return;
        }
        String bundle = ecc.encryptAsyTwoWayBundle(cryptoDataStr.getData(), cryptoDataStr.getPubKeyB(), cryptoDataStr.getPriKeyA());
        System.out.println(bundle);
        Menu.anyKeyToContinue(br);
    }

    private static CryptoDataStr getEncryptedEccAesDataTwoWay(BufferedReader br) {

        String pubKeyB;
        String msg;
        char[] priKeyA;
        try {
            System.out.println("Input the recipient public key in hex:");
            pubKeyB = br.readLine();
            if (pubKeyB.length() != 66) {
                System.out.println("The public key should be 66 characters of hex.");
                return null;
            }
            String ask = "Input the sender's private Key:";
            priKeyA = Inputer.input32BytesKey(br, ask);
            if (priKeyA == null) return null;

            System.out.println("Input the msg:");
            msg = br.readLine();
        } catch (Exception e) {
            System.out.println("BufferedReader wrong.");
            return null;
        }
        return new CryptoDataStr(EncryptType.AsyTwoWay, msg, pubKeyB, priKeyA);
    }

    private static void encryptAsyOneWayBundle(BufferedReader br) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataStr cryptoDataStr = getEncryptedEccAesDataOneWay(br);
        if (cryptoDataStr.getData() == null) {
            System.out.println("Error: no message.");
            return;
        }
        String bundle = ecc.encryptAsyOneWayBundle(cryptoDataStr.getData(), cryptoDataStr.getPubKeyB());
        System.out.println(bundle);
        Menu.anyKeyToContinue(br);
    }

    private static void decryptFileSymKey(BufferedReader br) {
        File encryptedFile = FileTools.getAvailableFile(br);
        EccAes256K1P7 ecc = new EccAes256K1P7();
        if (encryptedFile == null || encryptedFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        String result = ecc.decrypt(encryptedFile, symKey);
        System.out.println(result);
        Menu.anyKeyToContinue(br);
    }

    private static void encryptFileWithSymKey(BufferedReader br) {
        File originalFile = FileTools.getAvailableFile(br);
        EccAes256K1P7 ecc = new EccAes256K1P7();
        if (originalFile == null || originalFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;

        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        System.out.println(ecc.encrypt(originalFile, symKey));
        Menu.anyKeyToContinue(br);
    }

    private static void decryptWithSymKeyBundle(BufferedReader br) {
        System.out.println("Input ivCipher in Base64:");
        String ivCipherStr;
        try {
            ivCipherStr = br.readLine();
            if ("".equals(ivCipherStr)) return;
        } catch (IOException e) {
            System.out.println("BufferedReader wrong;");
            return;
        }
        EccAes256K1P7 ecc = new EccAes256K1P7();
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        String eccAesDataJson;
        eccAesDataJson = ecc.decryptSymKeyBundle(ivCipherStr, symKey);
        System.out.println(eccAesDataJson);
        Menu.anyKeyToContinue(br);
    }

    private static void encryptWithSymKeyBundle(BufferedReader br) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        String msg = Inputer.inputMsg(br);
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        String ivCipher = ecc.encryptSymKeyBundle(msg, symKey);
        System.out.println(ivCipher);
        Menu.anyKeyToContinue(br);
    }


    private static void decryptFileAsy(BufferedReader br) {
        File encryptedFile = FileTools.getAvailableFile(br);
        if (encryptedFile == null || encryptedFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;

        System.out.println("Input the recipient private key in hex:");
        char[] priKey = new char[64];
        int num = 0;
        try {
            num = br.read(priKey);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (num != 64 || !Hex.isHexCharArray(priKey)) {
            System.out.println("The symKey should be 64 characters in hex.");
        }
        EccAes256K1P7 ecc = new EccAes256K1P7();
        String result = ecc.decrypt(encryptedFile, BytesTools.hexCharArrayToByteArray(priKey));
        System.out.println(result);
        Menu.anyKeyToContinue(br);
    }

    private static void encryptFileAsy(BufferedReader br) {

        File originalFile = FileTools.getAvailableFile(br);
        if (originalFile == null || originalFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;
        String pubKeyB;
        pubKeyB = getPubKey(br);
        if (pubKeyB == null) return;
        EccAes256K1P7 ecc = new EccAes256K1P7();
        System.out.println(ecc.encrypt(originalFile, pubKeyB));
        Menu.anyKeyToContinue(br);
    }

    private static String getPubKey(BufferedReader br) {
        System.out.println("Input the recipient public key in hex:");
        String pubKeyB;
        try {
            pubKeyB = br.readLine();
        } catch (IOException e) {
            System.out.println("BufferedReader wrong:" + e.getMessage());
            return null;
        }
        if (pubKeyB.length() != 66) {
            System.out.println("The public key should be 66 characters of hex.");
        }
        return pubKeyB;
    }


    private static void gainTimestamp() {
        long timestamp = System.currentTimeMillis();
        System.out.println(timestamp);
    }

    private static void encryptWithSymKey(BufferedReader br) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        String msg = Inputer.inputMsg(br);
        if (msg == null) return;
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.SymKey, msg, symKey);

        ecc.encrypt(cryptoDataStr);

        System.out.println(cryptoDataStr.toJson());
        Menu.anyKeyToContinue(br);
    }

    private static void decryptWithSymKey(BufferedReader br) throws Exception {

        System.out.println("Input the json string of EccAesData:");
        String eccAesDataJson = br.readLine();
        EccAes256K1P7 ecc = new EccAes256K1P7();
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        if (symKey == null) return;

        eccAesDataJson = ecc.decrypt(eccAesDataJson, symKey);
        System.out.println(eccAesDataJson);
        Menu.anyKeyToContinue(br);
    }

    private static void encryptWithPassword(BufferedReader br) throws IOException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, NoSuchProviderException {
        // TODO Auto-generated method stub

        String ask = "Input the password no longer than 64:";
        char[] password = Inputer.inputPassword(br, ask);
        System.out.println("Password:" + Arrays.toString(password));

        System.out.println("Input the plaintext:");
        String msg = br.readLine();
        EccAes256K1P7 ecc = new EccAes256K1P7();
//        EccAesData eccAesData = new EccAesData(EccAesType.Password,msg,password);
//        ecc.encrypt(eccAesData);
        System.out.println(ecc.encrypt(msg, password));

        Menu.anyKeyToContinue(br);
    }

    private static void decryptWithPassword(BufferedReader br) throws Exception {
        // TODO Auto-generated method stub
        String ask = "Input the password no longer than 64:";

        char[] password = Inputer.inputPassword(br, ask);
        System.out.println("Input the json string of EccAesData:");
        String eccAesDataJson = br.readLine();

        EccAes256K1P7 ecc = new EccAes256K1P7();

        String decrypt = ecc.decrypt(eccAesDataJson, password);
        System.out.println(decrypt);
        byte[] bytes = ecc.decryptForBytes(eccAesDataJson, password);
        System.out.println(Hex.toHex(bytes));
        if (bytes.length == 32) System.out.println(Base58.encode(KeyTools.priKey32To38Compressed(bytes)));

        Menu.anyKeyToContinue(br);
    }


    private static void decryptAsy(BufferedReader br) throws Exception {
        // TODO Auto-generated method stub

        System.out.println("Input the json string of EccAesData:");
        String eccAesDataJson = br.readLine();

        decryptAsyJson(br, eccAesDataJson);

        Menu.anyKeyToContinue(br);

    }

    private static void decryptAsyJson(BufferedReader br, String eccAesDataJson) {
        System.out.println("Input the recipient private key in hex:");
        char[] priKey = new char[64];
        int num;
        try {
            num = br.read(priKey);
        } catch (IOException e) {
            System.out.println("BufferedReader wrong.");
            return;
        }
        if (num != 64 || !Hex.isHexCharArray(priKey)) {
            System.out.println("The private key should be 64 characters in hex.");
        }

        EccAes256K1P7 ecc = new EccAes256K1P7();

        String eccAesData = ecc.decrypt(eccAesDataJson, priKey);

        System.out.println(eccAesData);
    }

    private static void decryptWithBtcEcc() {
        // TODO Auto-generated method stub

        System.out.println("Input the ciphertext encrypted with BtcAlgorithm:");
    }

    private static void encryptAsy(BufferedReader br) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataStr cryptoDataStr = getEncryptedEccAesDataOneWay(br);
        ecc.encrypt(cryptoDataStr);
        if (cryptoDataStr == null) return;
        if (cryptoDataStr.getMessage() != null) {
            System.out.println(cryptoDataStr.getMessage());
        } else System.out.println(cryptoDataStr.toJson());
        Menu.anyKeyToContinue(br);
    }

    private static CryptoDataStr getEncryptedEccAesDataOneWay(BufferedReader br) {
        System.out.println("Input the recipient public key in hex:");
        String pubKeyB;
        String msg;
        try {
            pubKeyB = br.readLine();
            if (pubKeyB.length() != 66) {
                System.out.println("The public key should be 66 characters of hex.");
                return null;
            }
            System.out.println("Input the msg:");
            msg = br.readLine();
        } catch (Exception e) {
            System.out.println("BufferedReader wrong.");
            return null;
        }
        return new CryptoDataStr(EncryptType.AsyOneWay, msg, pubKeyB);
    }

    private static void encryptWithBtcEcc() {
        // TODO Auto-generated method stub
        System.out.println("encryptWithBtcAlgo is under developing:");
    }

    public static void getRandom(BufferedReader br) throws IOException {

        int len = 0;
        while (true) {
            System.out.println("Input the bytes length of the random you want. Enter to exit:");
            String input = br.readLine();
            if ("".equals(input)) {
                return;
            }

            try {
                len = Integer.parseInt(input);
                break;
            } catch (Exception e) {
                continue;
            }
        }

        byte[] bytes = BytesTools.getRandomBytes(len);

        if (bytes.length <= 8) {
            // Create a ByteBuffer with the byte array
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.put(bytes);
            buffer.flip();

            // Convert the byte array to a long
            long value = 0;

            // Read the bytes from the ByteBuffer
            for (int i = 0; i < bytes.length; i++) {
                value = (value << 8) | (buffer.get() & 0xFF);
            }

            System.out.println("No longer than 8 bytes, in number:\n----\n" + Math.abs(value) + "\n----");
        } else {
            System.out.println("Longer than 8 bytes, in hex:\n----\n" + BytesTools.bytesToHexStringBE(bytes) + "\n----");
        }
        Menu.anyKeyToContinue(br);
    }

    private static void hex32Base58(BufferedReader br) throws IOException {
        String input = null;
        while (true) {
            System.out.println("Input 32 bytes hex or base58 string, enter to exit:");
            input = br.readLine();
            if ("".equals(input)) {
                return;
            }
            if (input.length() == 64) {
                System.out.println("Hex to Base58:" + "\n----");
                System.out.println("New: " + KeyTools.priKey32To38(input));
                System.out.println("Old: " + KeyTools.priKey32To37(input) + "\n----");

            } else if (input.length() == 52) {
                System.out.println("Base58 WIF compressed to Hex:" + "\n----");
                System.out.println(KeyTools.getPriKey32(input) + "\n----");
            } else if (input.length() == 51) {
                System.out.println("Base58 WIF to Hex:" + "\n----");
                System.out.println(KeyTools.getPriKey32(input) + "\n----");
            } else {
                System.out.println("Only 64 chars hex or 52 chars base58 string can be accepted.");
            }
            ;
        }
    }

    private static void findNiceFid(BufferedReader br) throws IOException {
        String input = null;
        SimpleDateFormat sdf = new SimpleDateFormat();
        Date begin = new Date();
        System.out.println(sdf.format(begin));
        while (true) {
            System.out.println("Input 4 characters you want them be in the end of your fid, enter to exit:");
            input = br.readLine();
            if ("".equals(input)) {
                return;
            }
            if (input.length() != 4) {
                System.out.println("Input 4 characters you want them be in the end of your fid:");
            } else break;
        }
        long i = 0;
        long j = 0;
        System.out.println("Finding...");
        while (true) {
            ECKey ecKey = new ECKey();
            String fid = KeyTools.pubKeyToFchAddr(ecKey.getPubKey());
            if (fid.substring(30).equals(input)) {
                System.out.println("----");
                System.out.println("FID:" + fid);
                System.out.println("PubKey: " + ecKey.getPublicKeyAsHex());
                System.out.println("PriKeyHex: " + ecKey.getPrivateKeyAsHex());
                System.out.println("PriKeyBase58: " + ecKey.getPrivateKeyEncoded(MainNetParams.get()));
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

    private static void pubKeyToAddrs(BufferedReader br) throws Exception {
        System.out.println("Input the public key, enter to exit:");
        String pubKey = null;
        try {
            pubKey = br.readLine();
        } catch (IOException e) {
            System.out.println("BufferedReader wrong.");
            return;
        }
        if ("".equals(pubKey)) {
            return;
        }


        pubKey = KeyTools.getPubKey33(pubKey);

        KeyTools.showPubKeys(pubKey);

        Map<String, String> addrMap = KeyTools.pubKeyToAddresses(pubKey);

        System.out.println("----");

        System.out.println("fch" + ": " + addrMap.get("fchAddr"));
        System.out.println("BTC" + ": " + addrMap.get("btcAddr"));
        System.out.println("ETH" + ": " + addrMap.get("ethAddr"));
        System.out.println("BCH" + ": " + addrMap.get("bchAddr"));
        System.out.println("DOGE" + ": " + addrMap.get("dogeAddr"));
        System.out.println("TRX" + ": " + addrMap.get("trxAddr"));
        System.out.println("LTC" + ": " + addrMap.get("ltcAddr"));


        System.out.println("----");
        Menu.anyKeyToContinue(br);
    }

    private static void sha256File(BufferedReader br) throws IOException {
        while (true) {
            System.out.println("Input the full path of the file to be hashed, enter to exit:");
            String filePath = br.readLine();
            if ("".equals(filePath)) {
                return;
            }
            // Create a File object with the specified path
            File file = new File(filePath);
            // Check if the file exists
            if (file.isDirectory()) {
                System.out.println("It's a directory.");
                break;
            }
            if (file.exists()) {
                System.out.println("File name: " + file.getName());
            } else {
                System.out.println("File does not exist.");
                break;
            }
            String hash = Hash.sha256(file);
            System.out.println("----");
            System.out.println("file:" + filePath);
            System.out.println("sha256:" + hash);
            System.out.println("----");
            Menu.anyKeyToContinue(br);
        }
    }

    private static void sha256x2File(BufferedReader br) throws IOException {
        while (true) {
            System.out.println("Input the full path of the file to be hashed, enter to exit:");
            String filePath = br.readLine();
            if ("".equals(filePath)) {
                return;
            }
            // Create a File object with the specified path
            File file = new File(filePath);
            // Check if the file exists
            if (file.isDirectory()) {
                System.out.println("It's a directory.");
                break;
            }
            if (file.exists()) {
                System.out.println("File name: " + file.getName());
            } else {
                System.out.println("File does not exist.");
                break;
            }
            String hash = Hash.sha256x2(file);
            System.out.println("----");
            System.out.println("file:" + filePath);
            System.out.println("sha256:" + hash);
            System.out.println("----");
            Menu.anyKeyToContinue(br);
        }
    }

    private static void sha256String(BufferedReader br) {
        System.out.println("Input the string to be hashed:");
        String text = Inputer.inputStringMultiLine(br);
        String hash = Hash.sha256(text);
        System.out.println("----");
        System.out.println("raw string:");
        System.out.println("----");
        System.out.println(text);
        System.out.println("----");
        System.out.println("sha256:" + hash);
        System.out.println("----");
        Menu.anyKeyToContinue(br);
    }

    private static void sha256x2String(BufferedReader br) {
        System.out.println("Input the string to be hashed:");
        String text = Inputer.inputStringMultiLine(br);
        String hash = Hash.sha256x2(text);
        System.out.println("----");
        System.out.println("raw string:");
        System.out.println("----");
        System.out.println(text);
        System.out.println("----");
        System.out.println("sha256:" + hash);
        System.out.println("----");
        Menu.anyKeyToContinue(br);
    }

    private static void symKeySign(BufferedReader br) {
        System.out.println("Input the symKey in hex, enter to exit:");
        String symKey;
        try {
            symKey = br.readLine();
            if ("".equals(symKey)) {
                return;
            }

            while (true) {
                System.out.println("Input text to be signed, enter to input, 'q' to exit:");
                String text = Inputer.inputStringMultiLine(br);
                if ("q".equals(text)) return;
                String sign = Hash.getSign(symKey, text);
                System.out.println("----");
                System.out.println("Signature:");
                System.out.println("----");
                System.out.println(sign);
                System.out.println("----");
            }
        } catch (IOException e) {
            System.out.println("BufferedReader wrong.");
        }
    }

}
