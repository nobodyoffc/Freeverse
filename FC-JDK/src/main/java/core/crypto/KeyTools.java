package core.crypto;

import clients.ApipClient;
import constants.Constants;
import core.crypto.old.EccAes256K1P7;
import data.fcData.AlgorithmId;
import handlers.ContactManager;
import ui.Shower;


import data.feipData.Contact;
import data.fcData.FcSession;
import data.fcData.TalkIdInfo;
import handlers.SessionManager;
import handlers.TalkIdManager;
import org.bitcoinj.core.*;
import org.bitcoinj.core.Base58;
import utils.BytesUtils;
import utils.Hex;
import core.fch.FchMainNetwork;
import core.fch.Inputer;
import org.bitcoinj.params.MainNetParams;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import utils.QRCodeUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

public class KeyTools {

    public static void main(String[] args) {
        String secret = "春花秋月何时了";
        ECKey ecKey = secretWordsToPrikey(secret);
        System.out.println("Prikey:"+ecKey.getPrivateKeyAsWiF(new MainNetParams()));
        System.out.println("Prikey hex:"+ecKey.getPublicKeyAsHex());
    }
    public static ECKey secretWordsToPrikey(String secretWords){
        byte[] secretBytes = secretWords.getBytes();
        byte[] hash = Hash.sha256(secretBytes);
        return ECKey.fromPrivate(hash);
    }

    public static String getPubkey(String fid, SessionManager sessionHandler, TalkIdManager talkIdHandler, ContactManager contactHandler, ApipClient apipClient) {
        String pubkey = null;
        if(sessionHandler!=null){
            FcSession session = sessionHandler.getSessionByUserId(fid);
            if(session!=null)pubkey = session.getPubkey();
            if(pubkey!=null)return pubkey;
        }
        if(talkIdHandler!=null){
            TalkIdInfo talkIdInfo = talkIdHandler.get(fid);
            if(talkIdInfo!=null)pubkey = talkIdInfo.getPubkey();
            if(pubkey!=null)return pubkey;
        }
        if(contactHandler!=null){
            Contact contact = contactHandler.getContact(fid);
            if(contact!=null)pubkey = contact.getPubkey();
            if(pubkey!=null)return pubkey;
        }
        if(apipClient!=null){
            pubkey = apipClient.getPubkey(fid, RequestMethod.POST, AuthType.SYMKEY_ENCRYPT);
        }
        return pubkey;
    }

    public static String inputPubkey(BufferedReader br) {

        String pubkeyB;
        pubkeyB = ui.Inputer.inputString(br,"Input the recipient public key in hex or Base58:");
        if(pubkeyB==null)return null;
        try{
            return getPubkey33(pubkeyB);
        }catch (Exception e){
            System.out.println("Failed to get pubkey: "+e.getMessage());
            return null;
        }
    }

    @Test
    public void test(){
        String pubkeyStr = "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a";
        byte[] pubkey = Hex.fromHex(pubkeyStr);
        String addr = BchCashAddr.createCashAddr(pubkey);
        System.out.println(addr);
    }

    public static String scriptToMultiAddr(String script) {
        byte[] scriptBytes = Hex.fromHex(script);
        byte[] b = Hash.sha256(scriptBytes);
        byte[] h = Hash.Ripemd160(b);
        return hash160ToMultiAddr(h);
    }

    public static byte[] prikeyToPubkey(byte[] prikey32Bytes) {
        ECKey eckey = ECKey.fromPrivate(prikey32Bytes);
        return eckey.getPubKey();
    }


    public static String prikeyToPubkey(String prikey) {
        //私钥如果长度为38字节，则为压缩格式。构成为：前缀80+32位私钥+压缩标志01+4位校验位。
        byte[] prikey32Bytes;
        byte[] prikeyBytes;
        byte[] suffix;
        byte[] prikeyForHash;
        byte[] hash;
        byte[] hash4;

        int len = prikey.length();

        switch (len) {
            case 64 -> prikey32Bytes = HexFormat.of().parseHex(prikey);
            case 52 -> {
                if (!(prikey.charAt(0) == 'L' || prikey.charAt(0) == 'K')) {
                    System.out.println("It's not a private key.");
                    return null;
                }
                prikeyBytes = Base58.decode(prikey);
                suffix = new byte[4];
                prikeyForHash = new byte[34];
                System.arraycopy(prikeyBytes, 0, prikeyForHash, 0, 34);
                System.arraycopy(prikeyBytes, 34, suffix, 0, 4);
                hash = Hash.sha256x2(prikeyForHash);
                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);
                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (prikeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                prikey32Bytes = new byte[32];
                System.arraycopy(prikeyForHash, 1, prikey32Bytes, 0, 32);
            }
            case 51 -> {
                if (prikey.charAt(0) != '5') {
                    System.out.println("It's not a private key.");
                    return null;
                }
                prikeyBytes = Base58.decode(prikey);
                suffix = new byte[4];
                prikeyForHash = new byte[33];
                System.arraycopy(prikeyBytes, 0, prikeyForHash, 0, 33);
                System.arraycopy(prikeyBytes, 33, suffix, 0, 4);
                hash = Hash.sha256x2(prikeyForHash);
                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);
                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (prikeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                prikey32Bytes = new byte[32];
                System.arraycopy(prikeyForHash, 1, prikey32Bytes, 0, 32);
            }
            default -> {
                System.out.println("It's not a private key.");
                return null;
            }
        }

        ECKey eckey = ECKey.fromPrivate(prikey32Bytes);

        return HexFormat.of().formatHex(eckey.getPubKey());
    }

    public static String prikeyToFid(byte[] prikey) {
        byte[] prikey32 = getPrikey32(prikey);
        byte[] pubkey = prikeyToPubkey(prikey32);
        return pubkeyToFchAddr(pubkey);
    }


    @Nullable
    public static byte[] importOrCreatePrikey(BufferedReader br) {
        System.out.println("""
                Input the private key...
                'b' for Base58 code.
                'c' for the cipher json.
                'h' for hex.
                'g' to generate a new one.
                other to exit:""");

        String input = Inputer.inputString(br);

        byte[] prikey32 = new byte[0];
        switch (input) {
            case "b" -> {
                do {
                    System.out.println("Input the private key in Base58:");
                    char[] prikeyBase58 = Inputer.inputPrikeyWif(br);
                    prikey32 = getPrikey32(BytesUtils.utf8CharArrayToByteArray(prikeyBase58));
                    String buyer = prikeyToFid(prikey32);
                    System.out.println(buyer);
                    System.out.println("Is this your FID? y/n:");
                    input = Inputer.inputString(br);
                } while (!"y".equals(input));
            }
            case "c" -> {
                do {
                    System.out.println("Input the private key cipher json:");
                    String cipher = Inputer.inputString(br);
                    if (cipher == null || "".equals(cipher)) break;

                    String ask = "Input the password to decrypt this prikey:";
                    char[] userPassword = Inputer.inputPassword(br, ask);

                    Decryptor decryptor = new Decryptor();

                    CryptoDataByte cryptoDataByte = decryptor.decryptJsonByPassword(cipher,userPassword);

                    BytesUtils.clearCharArray(userPassword);

                    if (cryptoDataByte.getCode()!=null && cryptoDataByte.getCode() != 0) {
                        System.out.println("Decrypt prikey cipher from input wrong." + cryptoDataByte.getMessage());
                        System.out.println("Try again.");
                        continue;
                    }
                    prikey32 = getPrikey32(cryptoDataByte.getData());
                    System.out.println("Your FID is: \n" + prikeyToFid(prikey32));
                    System.out.println("Is it right? y/n");
                    input = Inputer.inputString(br);
                } while (!"y".equals(input));
            }
            case "h" -> {
                do {
                    char[] prikeyHex = Inputer.input32BytesKey(br, "Input the private key in Hex:");
                    if (prikeyHex == null) break;
                    prikey32 = BytesUtils.hexCharArrayToByteArray(prikeyHex);
                    String buyer = prikeyToFid(prikey32);
                    System.out.println(buyer);
                    System.out.println("Is this your FID? y/n:");
                    input = Inputer.inputString(br);
                } while (!"y".equals(input));
            }
            case "g" -> {
                ECKey ecKey = generateNewPrikey(br);
                if (ecKey == null) {
                    System.out.println("Failed to generate new prikey.");
                    return null;
                }
                prikey32 = ecKey.getPrivKeyBytes();
            }
            default -> {
                return null;
            }
        }
        return prikey32;
    }

    @Nullable
    public static ECKey generateNewPrikey(BufferedReader br) {
        ECKey ecKey = new ECKey(new SecureRandom());
        String publicKeyAsHex = ecKey.getPublicKeyAsHex();
        Address address = Address.fromKey(FchMainNetwork.MAINNETWORK, ecKey);
        System.out.println("New FID:" + address.toString());
        System.out.println();
        while(true) {
            char[] password = Inputer.inputPassword(br, "Input a password to encrypt it:");
            byte[] prikey32 = ecKey.getPrivKeyBytes();
            CryptoDataByte cryptoDataByte = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7).encryptByPassword(prikey32, password);//EccAes256K1P7.encryptKeyWithPassword(prikey32, password);
            String cipher;
            if (cryptoDataByte != null && cryptoDataByte.getCode() == 0) {
                cipher = cryptoDataByte.toNiceJson();
            } else {
                System.out.println("Failed to encrypt prikey. Try again.");
                continue;
            }
            password = Inputer.inputPassword(br, "Check the password:");
            try {
                CryptoDataByte cryptoDataByteD = new Decryptor().decryptJsonByPassword(cipher,password);//.decryptJsonBytes(cipher, BytesUtils.utf8CharArrayToByteArray(password));
                if (cryptoDataByteD==null || cryptoDataByteD.getData()==null) {
                    System.out.println("Failed to decrypt prikey. Try again.");
                    continue;
                }
                ECKey ecKey1 = ECKey.fromPrivate(prikey32);
                Address checkAddress = Address.fromKey(FchMainNetwork.MAINNETWORK, ecKey1);

                if (!address.toString().equals(checkAddress.toString())) {
                    System.out.println("Failed to generate new prikey. Try again");
                    continue;
                }
                System.out.println("New prikey is ready:");
                Shower.printUnderline(10);
                System.out.println("Prikey:" + ecKey.getPrivateKeyAsWiF(FchMainNetwork.MAINNETWORK));
                Shower.printUnderline(10);
                System.out.println("FID:" + address);
                System.out.println("Pubkey:" + publicKeyAsHex);
                System.out.println("PrikeyCipher:\n" + cipher);
                Shower.printUnderline(10);
                System.out.println("* Keep the prikey cipher and the password carefully." +
                        "\n* They are both required to recover the prikey.");
                break;
            } catch (Exception e) {
                System.out.println("Failed to generate new prikey. Try again.");
            }
        }
        return ecKey;
    }

    public static ECKey genNewFid(BufferedReader br) {
        MainNetParams netParams = FchMainNetwork.MAINNETWORK;

        SecureRandom sr;
        try {
            sr = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        ECKey ecKey = new ECKey(sr);

        byte[] prikey32 = ecKey.getPrivKeyBytes();
        System.out.println("New FID generated:");
        Shower.printUnderline(60);
        Address fid = Address.fromKey(netParams, ecKey);
        System.out.println(fid);
        String privateKeyAsWiF = ecKey.getPrivateKeyAsWiF(netParams);
        System.out.println("Prikey WIF:" + privateKeyAsWiF);
        QRCodeUtils.generateQRCode(privateKeyAsWiF);
        System.out.println("Prikey hex:" + HexFormat.of().formatHex(prikey32));
        
        Shower.printUnderline(60);
        System.out.println("* Warning: To copy and paste prikey is dangerous with an online device!\n");
        char[] password;
        while(true){
            password= Inputer.inputPassword(br, "Input a password to encrypt your new private key:");
            char[] confirmingPassword = Inputer.inputPassword(br, "Input the password again:");
            if(password !=null && password.length>0 && Arrays.equals(confirmingPassword,password))break;
            System.out.println("Failed. Try again.");
        }
        String userCipher = EccAes256K1P7.encryptKeyWithPassword(prikey32, password);
        System.out.println("Here is the cipher of your new private key:");
        System.out.println(fid);
        Shower.printUnderline(60);
        System.out.println(userCipher);
        Shower.printUnderline(60);
        System.out.println("* Warning: Keep the cipher text and the password. Without any of them, you will lose the control of the FID.");
        return ecKey;
    }

    public static Map<String, String> inputGoodFidValueStrMap(BufferedReader br, String mapName, boolean checkFullShare) {
        Map<String, String> map = new HashMap<>();

        while (true) {

            while (true) {
                System.out.println("Set " + mapName + ". 'y' to input. 'q' to quit. 'i' to quit ignore all changes.");
                String input;
                try {
                    input = br.readLine();
                } catch (IOException e) {
                    System.out.println("br.readLine() wrong.");
                    return null;
                }
                if ("y".equals(input)) break;
                if ("q".equals(input)) {
                    System.out.println(mapName + " is set.");
                    return map;
                }
                if ("i".equals(input)) return null;
                System.out.println("Invalid input. Try again.");
            }

            String key;
            while (true) {
                System.out.println("Input FID. 'q' to quit:");
                key = Inputer.inputString(br);
                if (key == null) return null;
                if ("q".equals(key)) break;

                if (!isGoodFid(key)) {
                    System.out.println("It's not a valid FID. Try again.");
                    continue;
                }
                break;
            }
            Double value = null;

            if (!"q".equals(key)) {
                if (checkFullShare) {
                    value = Inputer.inputGoodShare(br);
                } else {
                    String ask = "Input the number. Enter to quit.";
                    value = Inputer.inputDouble(br, ask);
                }
            }

            if (value != null) {
                map.put(key, String.valueOf(value));
            }
        }
    }

    public static boolean isGoodFid(String addr) {
        try {
            byte[] addrBytes = Base58.decode(addr);

            byte[] suffix = new byte[4];
            byte[] addrNaked = new byte[21];

            System.arraycopy(addrBytes, 0, addrNaked, 0, 21);
            System.arraycopy(addrBytes, 21, suffix, 0, 4);

            byte[] hash = Hash.sha256x2(addrNaked);

            byte[] hash4 = new byte[4];
            System.arraycopy(hash, 0, hash4, 0, 4);

            return (addrNaked[0] == (byte) 0x23 || addrNaked[0] == (byte) 0x05) && Arrays.equals(suffix, hash4);
        } catch (Exception ignore) {
            return false;
        }
    }
    public static String getPubkeyHexUncompressed(String pubkey33) {
        String pubkey65 = KeyTools.recoverPK33ToPK65(pubkey33);
        if(pubkey65==null)return null;
        byte[] pubkeyBytes = HexFormat.of().parseHex(pubkey65);
        return HexFormat.of().formatHex(pubkeyBytes);
    }
    public static String getPubkeyWifUncompressed(String pubkey33) {
        String pubkey65 = KeyTools.recoverPK33ToPK65(pubkey33);
        byte[] pubkeyBytes = HexFormat.of().parseHex(pubkey65);
        return Base58.encodeChecked(0, pubkeyBytes);
    }

    public static String getPubkeyWifCompressedWithVer0(String pubkey33) {
        byte[] pubkeyBytes = HexFormat.of().parseHex(pubkey33);
        return Base58.encodeChecked(0, pubkeyBytes);
    }

    public static String getPubkeyWifCompressedWithoutVer(String pubkey33) {
        byte[] pubkeyBytes = HexFormat.of().parseHex(pubkey33);
        return core.crypto.Base58.encodeChecked(pubkeyBytes);
    }

    public static Map<String, String> pubkeyToAddresses(String pubkey) {

        String pubkey33;

        if (pubkey.length() == 130) {
            try {
                pubkey33 = compressPk65To33(pubkey);
            } catch (Exception e) {
                return null;
            }
        } else {
            pubkey33 = pubkey;
        }

        String fchAddr = pubkeyToFchAddr(pubkey33);
        String btcAddr = pubkeyToBtcAddr(pubkey33);
        String ethAddr = pubkeyToEthAddr(pubkey);
        String ltcAddr = pubkeyToLtcAddr(pubkey33);
        String dogeAddr = pubkeyToDogeAddr(pubkey33);
        String trxAddr = pubkeyToTrxAddr(pubkey33);
        String bchAddr = pubkeyToBchBesh32Addr(pubkey33);

        Map<String, String> map = new HashMap<>();
        map.put(Constants.FCH_ADDR, fchAddr);
        map.put(Constants.BTC_ADDR, btcAddr);
        map.put(Constants.ETH_ADDR, ethAddr);
        map.put(Constants.BCH_ADDR, bchAddr);
        map.put(Constants.LTC_ADDR, ltcAddr);
        map.put(Constants.DOGE_ADDR, dogeAddr);
        map.put(Constants.TRX_ADDR, trxAddr);

        return map;
    }

//   public static String pubkeyToBtcBech32Addr(String pubkeyHex) {
//       byte[] hash160 = pubkeyToHash160(pubkeyHex);
//       return BtcAddrConverter.hash160ToBech32(hash160);
//   }

    public static String pubkeyToBchBesh32Addr(String pubkey33) {
        byte[] pubkeyBytes = HexFormat.of().parseHex(pubkey33);
        return BchCashAddr.createCashAddr(pubkeyBytes);
    }


    public static Map<String, String> hash160ToAddresses(byte[] hash160) {
        String fchAddr = hash160ToFchAddr(hash160);
        String btcAddr = hash160ToBtcBech32Addr(hash160);
        String bchAddr = hash160ToBchBech32Addr(hash160);
        String ltcAddr = hash160ToLtcAddr(hash160);
        String dogeAddr = hash160ToDogeAddr(hash160);

        Map<String, String> map = new HashMap<>();
        map.put(Constants.FCH_ADDR, fchAddr);
        map.put(Constants.BTC_ADDR, btcAddr);
        map.put(Constants.BCH_ADDR, bchAddr);
        map.put(Constants.LTC_ADDR, ltcAddr);
        map.put(Constants.ETH_ADDR, null);
        map.put(Constants.TRX_ADDR, null);
        map.put(Constants.DOGE_ADDR, dogeAddr);

        return map;
    }

    public static Map<String, String> pubkeyToAddresses(byte[] pubkey) {
        String pubkeyStr = HexFormat.of().formatHex(pubkey);
        return pubkeyToAddresses(pubkeyStr);
    }

    /**
     * Parse public key from script hex (supports both P2PKH unlockScript and P2PK lockScript)
     * @param scriptHex The script in hex format
     * @return The public key in hex format, or null if parsing fails
     */
    public static String parsePkFromScript(String scriptHex) {
        if (scriptHex == null || scriptHex.isEmpty()) {
            return null;
        }

        try {
            byte[] bScript = HexFormat.of().parseHex(scriptHex);

            // Check for P2PK lockScript: <pubkey_length> <pubkey> OP_CHECKSIG (0xac)
            // P2PK script structure: first byte is pubkey length (33 or 65), last byte is OP_CHECKSIG
            if (bScript.length >= 35 && bScript[bScript.length - 1] == (byte) 0xac) {
                int pubkeyLen = Byte.toUnsignedInt(bScript[0]);
                // Validate pubkey length and script structure
                if ((pubkeyLen == 33 || pubkeyLen == 65) && bScript.length == pubkeyLen + 2) {
                    // Validate pubkey format
                    byte[] pubkeyBytes = new byte[pubkeyLen];
                    System.arraycopy(bScript, 1, pubkeyBytes, 0, pubkeyLen);

                    // Check pubkey prefix
                    if ((pubkeyLen == 33 && (pubkeyBytes[0] == 0x02 || pubkeyBytes[0] == 0x03)) ||
                        (pubkeyLen == 65 && pubkeyBytes[0] == 0x04)) {
                        return HexFormat.of().formatHex(pubkeyBytes);
                    }
                }
            }

            // Otherwise, try to parse as P2PKH unlockScript: <sig_length> <signature> <pubkey_length> <pubkey>
            int sigLen = Byte.toUnsignedInt(bScript[0]); // Length of signature
            if (bScript.length > sigLen + 2) {
                // Skip signature, read pubkey
                byte pubkeyLenB = bScript[sigLen + 1]; // 公钥长度
                int pubkeyLen = Byte.toUnsignedInt(pubkeyLenB);

                if (bScript.length >= sigLen + 2 + pubkeyLen) {
                    byte[] pubkeyBytes = new byte[pubkeyLen];
                    System.arraycopy(bScript, sigLen + 2, pubkeyBytes, 0, pubkeyLen);
                    return HexFormat.of().formatHex(pubkeyBytes);
                }
            }
        } catch (Exception e) {
            // Return null if parsing fails
            return null;
        }

        return null;
    }

    /**
     * @deprecated Use {@link #parsePkFromScript(String)} instead
     */
    @Deprecated
    public static String parsePkFromUnlockScript(String hexScript) {
        return parsePkFromScript(hexScript);
    }

    public static String recoverPK33ToPK65(String PK33) {
        BigInteger p = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
        BigInteger e = new BigInteger("3", 16);
        BigInteger one = new BigInteger("1", 16);
        BigInteger two = new BigInteger("2", 16);
        BigInteger four = new BigInteger("4", 16);
        BigInteger seven = new BigInteger("7", 16);
        String prefix = PK33.substring(0, 2);

        if (prefix.equals("02") || prefix.equals("03")) {
            BigInteger x = new BigInteger(PK33.substring(2), 16);
            BigInteger ySq = (x.modPow(e, p).add(seven)).mod(p);
            BigInteger y = ySq.modPow(p.add(one).divide(four), p);

            if (!(y.mod(two).equals(new BigInteger(prefix, 16).mod(two)))) {
                y = p.subtract(y);
            }

            byte[] yByteArray = y.toByteArray();

            // Ensuring y is always exactly 32 bytes
            byte[] yBytes = new byte[32];
            if (yByteArray.length == 33) {
                System.arraycopy(yByteArray, 1, yBytes, 0, 32);
            } else {
                System.arraycopy(yByteArray, 0, yBytes, 32 - yByteArray.length, yByteArray.length);
            }

            return "04" + PK33.substring(2) + HexFormat.of().formatHex(yBytes);
        } else {
            return null;
        }
    }

    public static byte[] recoverPK33ToPK65(byte[] PK33) {
        String str = HexFormat.of().formatHex(PK33);
        String pubkey65 = recoverPK33ToPK65(str);
        if (pubkey65 != null)
            return HexFormat.of().parseHex(pubkey65);
        else return null;
    }

    public static String compressPk65To33(String pk64_65) {
        String publicKey;
        if (pk64_65.length() == 130 && pk64_65.startsWith("04")) {
            publicKey = pk64_65.substring(2);
        } else if (pk64_65.length() == 128) {
            publicKey = pk64_65;
        } else {
            return null;
        }
        String keyX = publicKey.substring(0, publicKey.length() / 2);
        String keyY = publicKey.substring(publicKey.length() / 2);
        String y_d = keyY.substring(keyY.length() - 1);
        String header;
        if ((Integer.parseInt(y_d, 16) & 1) == 0) {
            header = "02";
        } else {
            header = "03";
        }
        return header + keyX;
    }

    public static String compressPK65ToPK33(byte[] bytesPK65) {
        byte[] pk33 = new byte[33];
        byte[] y = new byte[32];
        System.arraycopy(bytesPK65, 1, pk33, 1, 32);
        System.arraycopy(bytesPK65, 33, y, 0, 32);
        BigInteger Y = new BigInteger(y);
        BigInteger TWO = new BigInteger("2");
        BigInteger ZERO = new BigInteger("0");
        if (Y.mod(TWO).equals(ZERO)) {
            pk33[0] = 0x02;
        } else {
            pk33[0] = 0x03;
        }
        return BytesUtils.bytesToHexStringLE(BytesUtils.invertArray(pk33));
    }

    public static String hash160ToFchAddr(String hash160Hex) {

        byte[] b = HexFormat.of().parseHex(hash160Hex);

        byte[] d = {0x23};
        byte[] e = new byte[21];
        System.arraycopy(d, 0, e, 0, 1);
        System.arraycopy(b, 0, e, 1, 20);

        byte[] c = Hash.sha256x2(e);
        byte[] f = new byte[4];
        System.arraycopy(c, 0, f, 0, 4);
        byte[] addrRaw = BytesUtils.bytesMerger(e, f);

        return Base58.encode(addrRaw);
    }

    public static String hash160ToFchAddr(byte[] hash160Bytes) {

        byte[] prefixForFch = {0x23};
        byte[] hash160WithPrefix = new byte[21];
        System.arraycopy(prefixForFch, 0, hash160WithPrefix, 0, 1);
        System.arraycopy(hash160Bytes, 0, hash160WithPrefix, 1, 20);


        byte[] hashWithPrefix = Hash.sha256x2(hash160WithPrefix);
        byte[] checkHash = new byte[4];
        System.arraycopy(hashWithPrefix, 0, checkHash, 0, 4);
        byte[] addrRaw = BytesUtils.bytesMerger(hash160WithPrefix, checkHash);

        return Base58.encode(addrRaw);
    }

    public static byte[] addrToHash160(String addr) {

        byte[] addrBytes = Base58.decode(addr);
        byte[] hash160Bytes = new byte[20];
        System.arraycopy(addrBytes, 1, hash160Bytes, 0, 20);
        return hash160Bytes;
    }

    public static String hash160ToBtcBech32Addr(String hash160Hex) {
        byte[] b = HexFormat.of().parseHex(hash160Hex);
        byte[] d = {0x00};
        byte[] addrRaw = hash160ToAddrBytes(b,d);
        return org.bitcoinj.core.Bech32.encode("bc", addrRaw);
    }

   public static String hash160ToBtcBech32Addr(byte[] hash160Bytes) {
       return BtcAddrConverter.hash160ToBech32(hash160Bytes);
   }

    public static String hash160ToBchBech32Addr(String hash160Hex) {
        byte[] b = HexFormat.of().parseHex(hash160Hex);
        byte[] d = {0x00};
        byte[] addrRaw = hash160ToAddrBytes(b,d);
        return Bech32Address.encode("bitcoincash", addrRaw);
    }

    public static String hash160ToBchBech32Addr(byte[] hash160Bytes) {
        return BchCashAddr.hash160ToCashAddr(hash160Bytes);
    }

    private static byte[] hash160ToAddrBytes(byte[] hash160Bytes, byte[] prefix) {
        byte[] e = new byte[21];
        System.arraycopy(prefix, 0, e, 0, 1);
        System.arraycopy(hash160Bytes, 0, e, 1, 20);

        byte[] c = Hash.sha256x2(e);
        byte[] f = new byte[4];
        System.arraycopy(c, 0, f, 0, 4);
        byte[] addrRaw = BytesUtils.bytesMerger(e, f);
        return addrRaw;
    }

    public static String hash160ToDogeAddr(String hash160Hex) {

        byte[] b = HexFormat.of().parseHex(hash160Hex);

        byte[] d = {0x1e};
        byte[] e = new byte[21];
        System.arraycopy(d, 0, e, 0, 1);
        System.arraycopy(b, 0, e, 1, 20);

        byte[] c = Hash.sha256x2(e);
        byte[] f = new byte[4];
        System.arraycopy(c, 0, f, 0, 4);
        byte[] addrRaw = BytesUtils.bytesMerger(e, f);

        return Base58.encode(addrRaw);
    }

    public static String hash160ToDogeAddr(byte[] hash160Bytes) {
        byte[] d = {0x1e};
        byte[] e = new byte[21];
        System.arraycopy(d, 0, e, 0, 1);
        System.arraycopy(hash160Bytes, 0, e, 1, 20);

        byte[] c = Hash.sha256x2(e);
        byte[] f = new byte[4];
        System.arraycopy(c, 0, f, 0, 4);
        byte[] addrRaw = BytesUtils.bytesMerger(e, f);

        return Base58.encode(addrRaw);
    }

    public static String hash160ToLtcAddr(String hash160Hex) {

        byte[] b = HexFormat.of().parseHex(hash160Hex);

        byte[] d = {0x30};
        byte[] e = new byte[21];
        System.arraycopy(d, 0, e, 0, 1);
        System.arraycopy(b, 0, e, 1, 20);

        byte[] c = Hash.sha256x2(e);
        byte[] f = new byte[4];
        System.arraycopy(c, 0, f, 0, 4);
        byte[] addrRaw = BytesUtils.bytesMerger(e, f);

        return Base58.encode(addrRaw);
    }

    public static String hash160ToLtcAddr(byte[] hash160Bytes) {

        byte[] d = {0x30};
        byte[] e = new byte[21];
        System.arraycopy(d, 0, e, 0, 1);
        System.arraycopy(hash160Bytes, 0, e, 1, 20);

        byte[] c = Hash.sha256x2(e);
        byte[] f = new byte[4];
        System.arraycopy(c, 0, f, 0, 4);
        byte[] addrRaw = BytesUtils.bytesMerger(e, f);

        return Base58.encode(addrRaw);
    }

    public static String hash160ToMultiAddr(byte[] hash160Bytes) {
        byte[] d = {0x05};
        byte[] e = new byte[21];
        System.arraycopy(d, 0, e, 0, 1);
        System.arraycopy(hash160Bytes, 0, e, 1, 20);

        byte[] c = Hash.sha256x2(e);
        byte[] f = new byte[4];
        System.arraycopy(c, 0, f, 0, 4);
        byte[] addrRaw = BytesUtils.bytesMerger(e, f);

        return Base58.encode(addrRaw);
    }

    public static String hash160ToBtcAddr(String hash160Hex) {

        byte[] b = HexFormat.of().parseHex(hash160Hex);
        byte[] d = {0x00};

        byte[] addrRaw = hash160ToAddrBytes(b,d);

        return Base58.encode(addrRaw);
    }

    public static String hash160ToBtcAddr(byte[] hash160Bytes) {
        byte[] d = {0x00};
        byte[] addrRaw = hash160ToAddrBytes(hash160Bytes,d);

        return Base58.encode(addrRaw);
    }

    public static String pubkeyToFchAddr(String a) {
        byte[] h = pubkeyToHash160(HexFormat.of().parseHex(a));
        return hash160ToFchAddr(h);
    }
    
    private static byte[] pubkeyToHash160(String a) {
        return pubkeyToHash160(HexFormat.of().parseHex(a));
    }

    private static byte[] pubkeyToHash160(byte[] pubkeyBytes) {
        byte[] b = Hash.sha256(pubkeyBytes);
        byte[] h = Hash.Ripemd160(b);
        return h;
    }

    public static String pubkeyToFchAddr(byte[] a) {
        byte[] b = Hash.sha256(a);
        byte[] h = Hash.Ripemd160(b);
        return hash160ToFchAddr(h);
    }

    public static String pubkeyToMultiSigAddr(String a) {
        byte[] h = pubkeyToHash160(a);
        return hash160ToMultiAddr(h);
    }

    public static String pubkeyToMultiSigAddr(byte[] a) {
        byte[] b = Hash.sha256(a);
        byte[] h = Hash.Ripemd160(b);
        return hash160ToMultiAddr(h);
    }

    public static String pubkeyToBtcAddr(String a) {
        byte[] h = pubkeyToHash160(a);
        return hash160ToBtcAddr(h);
    }

    public static byte[] bech32BtcToHash160(String bech32Address) {
        return BtcAddrConverter.bech32ToHash160(bech32Address);
    }

    public static byte[] bech32BchToHash160(String bech32Address) {
        if(bech32Address.lastIndexOf(":")<1) {
            bech32Address = "bitcoincash:"+bech32Address;
        }
        Bech32Address bech32AddressData = Bech32Address.decode(bech32Address);
        if (bech32AddressData == null) {
            throw new IllegalArgumentException("Invalid Bech32 Bitcoin address");
        }
        
        byte[] data = bech32AddressData.words;
        if (data.length < 2 || data[0] != 0x00) {
            throw new IllegalArgumentException("Invalid witness version or program length");
        }
        
        byte[] hash160 = new byte[20];
        System.arraycopy(data, 1, hash160, 0, 20);
        return hash160;
    }

    public static String pubkeyToBtcAddr(byte[] a) {
        byte[] b = Hash.sha256(a);
        byte[] h = Hash.Ripemd160(b);
        return hash160ToBtcAddr(h);
    }

    public static String pubkeyToTrxAddr(String a) {
        if (a == null) return null;
        String pubkey65;
        if (a.length() == 130) {
            pubkey65 = a;
        } else {
            pubkey65 = recoverPK33ToPK65(a);
        }
        if (pubkey65 == null) return null;
        String pubkey64 = pubkey65.substring(2);

        byte[] pubkey64Bytes = HexFormat.of().parseHex(pubkey64);
        byte[] pukHash64Hash = Hash.sha3(pubkey64Bytes);

        byte[] pukHashWithPrefix = new byte[21];
        pukHashWithPrefix[0] = 0x41;
        System.arraycopy(pukHash64Hash, 12, pukHashWithPrefix, 1, 20);

        byte[] c = Hash.sha256x2(pukHashWithPrefix);
        byte[] f = new byte[4];
        System.arraycopy(c, 0, f, 0, 4);
        byte[] addrRaw = BytesUtils.bytesMerger(pukHashWithPrefix, f);

        return Base58.encode(addrRaw);
    }

    public static String pubkeyToDogeAddr(String a) {
        byte[] h = pubkeyToHash160(a);
        return hash160ToDogeAddr(h);
    }

    public static String pubkeyToDogeAddr(byte[] a) {
        byte[] b = Hash.sha256(a);
        byte[] h = Hash.Ripemd160(b);
        return hash160ToDogeAddr(h);
    }

    public static String pubkeyToLtcAddr(String a) {
        byte[] h = pubkeyToHash160(a);
        return hash160ToLtcAddr(h);
    }

    public static String pubkeyToLtcAddr(byte[] a) {
        byte[] b = Hash.sha256(a);
        byte[] h = Hash.Ripemd160(b);
        String address = hash160ToLtcAddr(h);
        return address;
    }

    public static String pubkeyToEthAddr(String a) {

        String pubkey65;
        if (a.length() == 130) {
            pubkey65 = a;
        } else {
            pubkey65 = recoverPK33ToPK65(a);
        }

        String pubkey64 = pubkey65.substring(2);

        byte[] pubkey64Bytes = HexFormat.of().parseHex(pubkey64);
        byte[] pukHash64Hash = Hash.sha3(pubkey64Bytes);
        String fullHash = HexFormat.of().formatHex(pukHash64Hash);

        return "0x" + fullHash.substring(24);
    }

    public static String pubkeyToEthAddr(byte[] b) {
        String a = HexFormat.of().formatHex(b);

        String pubkey65 = recoverPK33ToPK65(a);

        String pubkey64 = pubkey65.substring(2);

        byte[] pubkey64Bytes = HexFormat.of().parseHex(pubkey64);
        byte[] pukHash64Hash = Hash.sha3(pubkey64Bytes);
        String fullHash = HexFormat.of().formatHex(pukHash64Hash);

        return "0x" + fullHash.substring(24);
    }

    public static byte[] getPrikey32(byte[] prikey) {
        byte[] prikey32Bytes;
        byte[] prikeyBytes;
        byte[] suffix;
        byte[] prikeyForHash;
        byte[] hash;
        byte[] hash4;
        int len = prikey.length;

        if (len == 52) {
            char[] prikeyUtf8Chars = BytesUtils.byteArrayToUtf8CharArray(prikey);
            BytesUtils.clearByteArray(prikey);
            prikey = core.crypto.Base58.base58CharArrayToByteArray(prikeyUtf8Chars);
            len = prikey.length;
        }

        switch (len) {
            case 32 -> prikey32Bytes = prikey;
            case 38 -> {
                prikeyBytes = prikey;
                suffix = new byte[4];
                prikeyForHash = new byte[34];
                System.arraycopy(prikeyBytes, 0, prikeyForHash, 0, 34);
                System.arraycopy(prikeyBytes, 34, suffix, 0, 4);
                hash = Hash.sha256x2(prikeyForHash);
                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);
                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (prikeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                prikey32Bytes = new byte[32];
                System.arraycopy(prikeyForHash, 1, prikey32Bytes, 0, 32);
            }
            case 37 -> {
                prikeyBytes = prikey;
                suffix = new byte[4];
                prikeyForHash = new byte[33];
                System.arraycopy(prikeyBytes, 0, prikeyForHash, 0, 33);
                System.arraycopy(prikeyBytes, 33, suffix, 0, 4);
                hash = Hash.sha256x2(prikeyForHash);
                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);
                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (prikeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                prikey32Bytes = new byte[32];
                System.arraycopy(prikeyForHash, 1, prikey32Bytes, 0, 32);
            }
            default -> {
                System.out.println("It's not a private key.");
                return null;
            }
        }

        return prikey32Bytes;
    }

    public static boolean checkSum(String str) {
        byte[] strBytes;
        byte[] suffix;
        byte[] hash;
        byte[] hash4 = new byte[4];

        strBytes = HexFormat.of().parseHex(str);
        int len = str.length();

        suffix = new byte[4];
        byte[] strNaked = new byte[len - 4];

        System.arraycopy(strBytes, 0, strNaked, 0, len - 4);
        System.arraycopy(strBytes, len - 4, suffix, 0, 4);

        hash = Hash.sha256x2(strNaked);
        System.arraycopy(hash, 0, hash4, 0, 4);

        return Arrays.equals(suffix, hash4);
    }

    public static boolean isValidPubkey(String puk) {
        String prefix = "";
        if (puk.length() > 2) prefix = puk.substring(0, 2);
        if (puk.length() == 66) {
            return prefix.equals("02") || prefix.equals("03");
        } else if (puk.length() == 130) {
            return prefix.equals("04");
        }
        return false;
    }

    public static String prikey32To38WifCompressed(String prikey32) {
        /*
        26字节长度为WIF compressed私钥格式。过程：
        在32位私钥加入版本号前缀0x80
        再加入压缩标志后缀0x01
        sha256x2(80+prikey32+01)取前4字节checksum
        对80+prikey32+01+checksum取base58编码
         */
        if(prikey32.startsWith("0x")||prikey32.startsWith("0X"))prikey32 = prikey32.substring(2);
        String prikey26;
        if (prikey32.length() != 64) {
            System.out.println("Private keys must be 32 bytes");
            return null;
        }
        // Keys that have compressed public components have an extra 1 byte on the end in dumped form.
        byte[] bytes32 = BytesUtils.hexToByteArray(prikey32);
        byte[] bytes38 = prikey32To38Compressed(bytes32);

        prikey26 = Base58.encode(bytes38);
        return prikey26;
    }

    

    public static byte[] prikey32To38Compressed(byte[] bytes32) {
        byte[] bytes34 = new byte[34];
        bytes34[0] = (byte) 0x80;
        System.arraycopy(bytes32, 0, bytes34, 1, 32);
        bytes34[33] = 1;

        byte[] hash = Hash.sha256x2(bytes34);
        byte[] hash4 = new byte[4];
        System.arraycopy(hash, 0, hash4, 0, 4);

        byte[] bytes38 = new byte[38];

        System.arraycopy(bytes34, 0, bytes38, 0, 34);
        System.arraycopy(hash4, 0, bytes38, 34, 4);
        return bytes38;
    }

    public static String prikey32To37(String prikey32) {
        /*
        26字节长度为WIF compressed私钥格式。过程：
        在32位私钥加入版本号前缀0x80
        sha256x2(80+prikey32+01)取前4字节checksum
        对80+prikey32+01+checksum取base58编码
         */

        String prikey37;
        if (prikey32.length() != 64) {
            System.out.println("Private keys must be 32 bytes");
            return null;
        }
        // Keys that have compressed public components have an extra 1 byte on the end in dumped form.
        byte[] bytes33 = new byte[33];
        byte[] bytes32 = BytesUtils.hexToByteArray(prikey32);
        bytes33[0] = (byte) 0x80;
        System.arraycopy(bytes32, 0, bytes33, 1, 32);

        byte[] hash = Hash.sha256x2(bytes33);
        byte[] hash4 = new byte[4];
        System.arraycopy(hash, 0, hash4, 0, 4);

        byte[] bytes37 = new byte[37];

        System.arraycopy(bytes33, 0, bytes37, 0, 33);
        System.arraycopy(hash4, 0, bytes37, 33, 4);

        prikey37 = Base58.encode(bytes37);
        return prikey37;
    }

    public static void showPubkeys(String pubkey) {
        Shower.printUnderline(4);
        System.out.println("- Public key compressed in hex:\n" + pubkey);
        System.out.println("- Public key uncompressed in hex:\n" + getPubkeyHexUncompressed(pubkey));

        System.out.println("- Public key WIF uncompressed:\n" + getPubkeyWifUncompressed(pubkey));
        System.out.println("- Public key WIF compressed with version 0:\n" + getPubkeyWifCompressedWithVer0(pubkey));
        System.out.println("- Public key WIF compressed without version:\n" + getPubkeyWifCompressedWithoutVer(pubkey));
    }

    @NotNull
    public static String getPubkey33(String pubkey) {
        switch (pubkey.length()) {
            case 66 -> {
                if (pubkey.startsWith("02") || pubkey.startsWith("03")) return pubkey;
            }
            case 130 -> {
                if (pubkey.startsWith("04")) return compressPk65To33(pubkey);
            }
            case 50 -> {
                return HexFormat.of().formatHex(Base58.decodeChecked(pubkey));
            }
            case 51 -> {
                return HexFormat.of().formatHex(Base58.decodeChecked(pubkey)).substring(2);
            }
        }
        return null;
    }

    public static byte[] getPrikey32(String prikey) {
        byte[] prikey32Bytes;
        byte[] prikeyBytes;
        byte[] suffix;
        byte[] prikeyForHash;
        byte[] hash;
        byte[] hash4;
        int len = prikey.length();


        switch (len) {
            case 64:
                prikey32Bytes = HexFormat.of().parseHex(prikey);
                break;
            case 52:
                if (!(prikey.substring(0, 1).equals("L") || prikey.substring(0, 1).equals("K"))) {
                    System.out.println("It's not a private key.");
                    return null;
                }
                prikeyBytes = Base58.decode(prikey);

                suffix = new byte[4];
                prikeyForHash = new byte[34];

                System.arraycopy(prikeyBytes, 0, prikeyForHash, 0, 34);
                System.arraycopy(prikeyBytes, 34, suffix, 0, 4);

                hash = Sha256Hash.hashTwice(prikeyForHash);

                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);

                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (prikeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                prikey32Bytes = new byte[32];
                System.arraycopy(prikeyForHash, 1, prikey32Bytes, 0, 32);
                break;
            case 51:
                if (!prikey.substring(0, 1).equals("5")) {
                    System.out.println("It's not a private key.");
                    return null;
                }

                prikeyBytes = Base58.decode(prikey);

                suffix = new byte[4];
                prikeyForHash = new byte[33];

                System.arraycopy(prikeyBytes, 0, prikeyForHash, 0, 33);
                System.arraycopy(prikeyBytes, 33, suffix, 0, 4);

                hash = Sha256Hash.hashTwice(prikeyForHash);

                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);

                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (prikeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                prikey32Bytes = new byte[32];
                System.arraycopy(prikeyForHash, 1, prikey32Bytes, 0, 32);
                break;
            default:
                System.out.println("It's not a private key.");
                return null;
        }

        return prikey32Bytes;
    }

    public static ECPublicKeyParameters pubkeyFromBytes(byte[] publicKeyBytes) {

        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());

        ECCurve curve = domainParameters.getCurve();

        ECPoint point = curve.decodePoint(publicKeyBytes);

        return new ECPublicKeyParameters(point, domainParameters);
    }

    public static ECPublicKeyParameters pubkeyFromHex(String publicKeyHex) {
        return pubkeyFromBytes(HexFormat.of().parseHex(publicKeyHex));
    }

    public static String pubkeyToHex(ECPublicKeyParameters publicKey) {
        return Hex.toHex(pubkeyToBytes(publicKey));
    }

    public static String prikeyToHex(ECPrivateKeyParameters privateKey) {
        BigInteger privateKeyValue = privateKey.getD();
        String hex = privateKeyValue.toString(16);
        while (hex.length() < 64) {  // 64 is for 256-bit key
            hex = "0" + hex;
        }
        return hex;
    }

    public static byte[] prikeyToBytes(ECPrivateKeyParameters privateKey) {
        return HexFormat.of().parseHex(prikeyToHex(privateKey));//Hex.decode(prikeyToHex(privateKey));
    }

    public static byte[] pubkeyToBytes(ECPublicKeyParameters publicKey) {
        return publicKey.getQ().getEncoded(true);
    }

    public static ECPrivateKeyParameters prikeyFromBytes(byte[] privateKey) {
        return prikeyFromHex(HexFormat.of().formatHex(privateKey));
    }

    public static ECPrivateKeyParameters prikeyFromHex(String privateKeyHex) {
        BigInteger privateKeyValue = new BigInteger(privateKeyHex, 16); // Convert hex to BigInteger
        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1"); // Use the same curve name as in key pair generation
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());
        return new ECPrivateKeyParameters(privateKeyValue, domainParameters);
    }

    public static ECPublicKeyParameters pubkeyFromPrikey(ECPrivateKeyParameters privateKey) {
        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());

        ECPoint Q = domainParameters.getG().multiply(privateKey.getD()); // Scalar multiplication of base point (G) and private key

        return new ECPublicKeyParameters(Q, domainParameters);
    }

    public static boolean isPubkey(String owner) {
        return Hex.isHexString(owner) 
        && (
            (owner.length() == 66 && (owner.startsWith("02") || owner.startsWith("03")))
            || (owner.length() == 130 && owner.startsWith("04"))
        ) ;
    }


    // BIP39 Mnemonic support
    private static List<String> bip39WordList = null;

    /**
     * Load BIP39 English wordlist from resources
     */
    private static List<String> loadBip39WordList() throws Exception {
        if (bip39WordList != null) {
            return bip39WordList;
        }

        bip39WordList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(
                        KeyTools.class.getResourceAsStream("/bip39-english.txt"),
                        java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                bip39WordList.add(line.trim());
            }
        }

        if (bip39WordList.size() != 2048) {
            throw new IllegalStateException("BIP39 wordlist must contain exactly 2048 words, found: " + bip39WordList.size());
        }

        return bip39WordList;
    }

    /**
     * Convert entropy bytes to BIP39 mnemonic phrase
     * @param entropy 16 bytes (128 bits) for 12 words, or 32 bytes (256 bits) for 24 words
     * @return BIP39 mnemonic phrase (space-separated words)
     * @throws IllegalArgumentException if entropy is not 16 or 32 bytes
     */
    public static String bytesToMnemonic(byte[] entropy) throws Exception {
        if (entropy == null) {
            throw new IllegalArgumentException("Entropy cannot be null");
        }
        if (entropy.length != 16 && entropy.length != 32) {
            throw new IllegalArgumentException("Entropy must be 16 bytes (128 bits) or 32 bytes (256 bits), got: " + entropy.length + " bytes");
        }

        List<String> wordList = loadBip39WordList();

        // Calculate checksum
        byte[] hash = Hash.sha256(entropy);
        int checksumLengthBits = entropy.length / 4; // 4 bits for 16 bytes, 8 bits for 32 bytes

        // Convert entropy + checksum to binary string
        StringBuilder bits = new StringBuilder();

        // Add entropy bits
        for (byte b : entropy) {
            bits.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
        }

        // Add checksum bits
        String checksumBits = String.format("%8s", Integer.toBinaryString(hash[0] & 0xFF)).replace(' ', '0');
        bits.append(checksumBits.substring(0, checksumLengthBits));

        // Split into 11-bit groups and convert to words
        int numberOfWords = (entropy.length * 8 + checksumLengthBits) / 11;
        String[] words = new String[numberOfWords];

        for (int i = 0; i < numberOfWords; i++) {
            int startIndex = i * 11;
            String wordBits = bits.substring(startIndex, startIndex + 11);
            int wordIndex = Integer.parseInt(wordBits, 2);
            words[i] = wordList.get(wordIndex);
        }

        return String.join(" ", words);
    }

    /**
     * Check if a string array is a valid BIP39 mnemonic
     * Validates:
     * 1. Word count (must be 12 or 24 words)
     * 2. All words are in the BIP39 wordlist
     * 3. Checksum is valid
     *
     * @param allList
     * @param words   Array of mnemonic words
     * @return true if the mnemonic is valid, false otherwise
     */
    public static boolean isValidMnemonic(List<String> allList, String[] words) {
        if (words == null || (words.length != 12 && words.length != 24)) {
            return false;
        }

        try {
            // Try to convert to bytes - if it succeeds, all validation passed
            mnemonicToBytes(allList,words);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a mnemonic phrase string is valid BIP39
     * Validates:
     * 1. Word count (must be 12 or 24 words)
     * 2. All words are in the BIP39 wordlist
     * 3. Checksum is valid
     *
     * @param mnemonic BIP39 mnemonic phrase (space-separated words)
     * @return true if the mnemonic is valid, false otherwise
     */
    public static boolean isValidMnemonic(String mnemonic) {
        if (mnemonic == null || mnemonic.trim().isEmpty()) {
            return false;
        }

        try {
            mnemonicToBytes(mnemonic);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Convert BIP39 mnemonic phrase to entropy bytes
     * @param mnemonic BIP39 mnemonic phrase (space-separated words)
     * @return entropy bytes (16 or 32 bytes)
     * @throws IllegalArgumentException if mnemonic is invalid
     */
    public static byte[] mnemonicToBytes(String mnemonic) throws Exception {
        if (mnemonic == null || mnemonic.trim().isEmpty()) {
            throw new IllegalArgumentException("Mnemonic cannot be null or empty");
        }

        List<String> wordList = loadBip39WordList();
        String[] words = mnemonic.trim().toLowerCase().split("\\s+");

        return mnemonicToBytes(wordList, words);
    }

    @NotNull
    private static byte[] mnemonicToBytes(List<String> wordList, String[] words) {
        if (words.length != 12 && words.length != 24) {
            throw new IllegalArgumentException("Mnemonic must be 12 or 24 words, got: " + words.length + " words");
        }

        // Convert words to indices
        int[] indices = new int[words.length];
        for (int i = 0; i < words.length; i++) {
            int index = wordList.indexOf(words[i]);
            if (index == -1) {
                throw new IllegalArgumentException("Invalid word in mnemonic: " + words[i]);
            }
            indices[i] = index;
        }

        // Convert indices to binary string
        StringBuilder bits = new StringBuilder();
        for (int index : indices) {
            bits.append(String.format("%11s", Integer.toBinaryString(index)).replace(' ', '0'));
        }

        // Calculate expected entropy and checksum lengths
        int entropyLengthBits = words.length == 12 ? 128 : 256;
        int checksumLengthBits = entropyLengthBits / 32;

        // Extract entropy bits
        String entropyBits = bits.substring(0, entropyLengthBits);
        String checksumBits = bits.substring(entropyLengthBits, entropyLengthBits + checksumLengthBits);

        // Convert entropy bits to bytes
        byte[] entropy = new byte[entropyLengthBits / 8];
        for (int i = 0; i < entropy.length; i++) {
            String byteBits = entropyBits.substring(i * 8, (i + 1) * 8);
            entropy[i] = (byte) Integer.parseInt(byteBits, 2);
        }

        // Verify checksum
        byte[] hash = Hash.sha256(entropy);
        String expectedChecksumBits = String.format("%8s", Integer.toBinaryString(hash[0] & 0xFF)).replace(' ', '0')
                .substring(0, checksumLengthBits);

        if (!checksumBits.equals(expectedChecksumBits)) {
            throw new IllegalArgumentException("Invalid mnemonic checksum");
        }

        return entropy;
    }

    /**
     * Convert BIP39 mnemonic to seed using PBKDF2-HMAC-SHA512
     * @param mnemonic BIP39 mnemonic phrase (space-separated words)
     * @param passphrase Optional passphrase (can be empty string "")
     * @return 64-byte seed
     */
    public static byte[] mnemonicToSeed(String mnemonic, String passphrase) throws Exception {
        if (mnemonic == null || mnemonic.trim().isEmpty()) {
            throw new IllegalArgumentException("Mnemonic cannot be null or empty");
        }

        String normalizedMnemonic = mnemonic.trim().toLowerCase();
        String salt = "mnemonic" + (passphrase == null ? "" : passphrase);

        return pbkdf2HmacSha512(normalizedMnemonic.getBytes(StandardCharsets.UTF_8),
                salt.getBytes(StandardCharsets.UTF_8),
                2048,
                64);
    }

    /**
     * Convert BIP39 mnemonic array to seed using PBKDF2-HMAC-SHA512
     * @param mnemonic Array of BIP39 mnemonic words
     * @param passphrase Optional passphrase (can be empty string "")
     * @return 64-byte seed
     */
    public static byte[] mnemonicToSeed(String[] mnemonic, String passphrase) throws Exception {
        if (mnemonic == null || mnemonic.length == 0) {
            throw new IllegalArgumentException("Mnemonic cannot be null or empty");
        }
        return mnemonicToSeed(String.join(" ", mnemonic), passphrase);
    }

    /**
     * PBKDF2-HMAC-SHA512 key derivation function
     */
    private static byte[] pbkdf2HmacSha512(byte[] password, byte[] salt, int iterations, int dkLen) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA512");
        hmac.init(new SecretKeySpec(password, "HmacSHA512"));

        byte[] dk = new byte[dkLen];
        int hLen = hmac.getMacLength();
        int l = (int) Math.ceil((double) dkLen / hLen);

        for (int i = 1; i <= l; i++) {
            byte[] u = new byte[salt.length + 4];
            System.arraycopy(salt, 0, u, 0, salt.length);
            u[salt.length] = (byte) (i >>> 24);
            u[salt.length + 1] = (byte) (i >>> 16);
            u[salt.length + 2] = (byte) (i >>> 8);
            u[salt.length + 3] = (byte) i;

            u = hmac.doFinal(u);
            byte[] t = u.clone();

            for (int j = 1; j < iterations; j++) {
                u = hmac.doFinal(u);
                for (int k = 0; k < hLen; k++) {
                    t[k] ^= u[k];
                }
            }

            int destPos = (i - 1) * hLen;
            int len = Math.min(hLen, dkLen - destPos);
            System.arraycopy(t, 0, dk, destPos, len);
        }

        return dk;
    }

    /**
     * Derive private keys from BIP39 mnemonic using BIP44 path: m/44'/0'/0'/0/i
     * This uses the standard Bitcoin derivation path
     *
     * @param mnemonic Array of BIP39 mnemonic words (12 or 24 words)
     * @param count Number of private keys to generate (generates keys from index 0 to count-1)
     * @return Array of private key byte arrays (32 bytes each)
     * @throws Exception if mnemonic is invalid or derivation fails
     */
    public static byte[][] priKeysFromMnemonic(String[] mnemonic, int count) throws Exception {
        return priKeysFromMnemonic(mnemonic, "", count);
    }

    /**
     * Derive private keys from BIP39 mnemonic using BIP44 path: m/44'/0'/0'/0/i
     * This uses the standard Bitcoin derivation path
     *
     * @param mnemonic Array of BIP39 mnemonic words (12 or 24 words)
     * @param passphrase BIP39 passphrase (can be empty string "")
     * @param count Number of private keys to generate (generates keys from index 0 to count-1)
     * @return Array of private key byte arrays (32 bytes each)
     * @throws Exception if mnemonic is invalid or derivation fails
     */
    public static byte[][] priKeysFromMnemonic(String[] mnemonic, String passphrase, int count) throws Exception {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }

        // Convert mnemonic to seed
        byte[] seed = mnemonicToSeed(mnemonic, passphrase);

        // Derive master key from seed
        byte[] masterKey = deriveMasterKey(seed);

        // Derive keys using BIP44 path: m/44'/0'/0'/0/i
        byte[][] privateKeys = new byte[count][];

        for (int i = 0; i < count; i++) {
            privateKeys[i] = deriveChildKey(masterKey, new int[]{
                    0x8000002C, // 44' (hardened)
                    0x80000000, // 0' (hardened) - Bitcoin coin type
                    0x80000000, // 0' (hardened) - account
                    0,          // 0 - external chain
                    i           // i - address index
            });
        }

        return privateKeys;
    }

    /**
     * Derive master key from seed using HMAC-SHA512
     */
    private static byte[] deriveMasterKey(byte[] seed) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA512");
        hmac.init(new SecretKeySpec("Bitcoin seed".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] i = hmac.doFinal(seed);

        // Left 32 bytes is the master private key, right 32 bytes is the chain code
        // We'll return both as a 64-byte array
        return i;
    }

    /**
     * Derive child key using BIP32 hierarchical deterministic derivation
     * @param extendedKey 64-byte extended key (32 bytes private key + 32 bytes chain code)
     * @param path Derivation path indices (use 0x80000000 + index for hardened derivation)
     * @return 32-byte derived private key
     */
    private static byte[] deriveChildKey(byte[] extendedKey, int[] path) throws Exception {
        byte[] key = new byte[32];
        byte[] chainCode = new byte[32];
        System.arraycopy(extendedKey, 0, key, 0, 32);
        System.arraycopy(extendedKey, 32, chainCode, 0, 32);

        for (int index : path) {
            byte[] data;

            if ((index & 0x80000000) != 0) {
                // Hardened derivation
                data = new byte[37];
                data[0] = 0;
                System.arraycopy(key, 0, data, 1, 32);
            } else {
                // Normal derivation - need public key
                ECKey ecKey = ECKey.fromPrivate(key);
                byte[] pubKey = ecKey.getPubKey();
                data = new byte[33 + 4];
                System.arraycopy(pubKey, 0, data, 0, 33);
            }

            // Add index to data
            int dataLen = data.length;
            data[dataLen - 4] = (byte) (index >>> 24);
            data[dataLen - 3] = (byte) (index >>> 16);
            data[dataLen - 2] = (byte) (index >>> 8);
            data[dataLen - 1] = (byte) index;

            // Calculate I = HMAC-SHA512(chainCode, data)
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(chainCode, "HmacSHA512"));
            byte[] i = hmac.doFinal(data);

            // Left 32 bytes is the tweak to add to parent key
            byte[] iL = new byte[32];
            System.arraycopy(i, 0, iL, 0, 32);

            // Right 32 bytes is the new chain code
            System.arraycopy(i, 32, chainCode, 0, 32);

            // Parse iL as 256 bit number and add to parent key modulo curve order
            BigInteger keyInt = new BigInteger(1, key);
            BigInteger iLInt = new BigInteger(1, iL);
            BigInteger n = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);

            BigInteger childKeyInt = iLInt.add(keyInt).mod(n);

            // Convert back to bytes
            byte[] childKeyBytes = childKeyInt.toByteArray();

            // Ensure it's exactly 32 bytes (pad with zeros if needed, remove sign byte if present)
            key = new byte[32];
            if (childKeyBytes.length == 33 && childKeyBytes[0] == 0) {
                System.arraycopy(childKeyBytes, 1, key, 0, 32);
            } else if (childKeyBytes.length <= 32) {
                System.arraycopy(childKeyBytes, 0, key, 32 - childKeyBytes.length, childKeyBytes.length);
            } else {
                System.arraycopy(childKeyBytes, childKeyBytes.length - 32, key, 0, 32);
            }
        }

        return key;
    }

}
