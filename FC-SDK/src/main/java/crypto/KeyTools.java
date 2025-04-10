package crypto;

import clients.ApipClient;
import constants.Constants;
import crypto.old.EccAes256K1P7;
import appTools.Shower;


import fcData.ContactDetail;
import fcData.FcSession;
import fcData.TalkIdInfo;
import handlers.ContactHandler;
import handlers.SessionHandler;
import handlers.TalkIdHandler;
import org.bitcoinj.core.*;
import org.bitcoinj.core.Base58;
import utils.BytesUtils;
import utils.Hex;
import fch.FchMainNetwork;
import fch.Inputer;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public class KeyTools {

    public static void main(String[] args) {
        String secret = "春花秋月何时了";
        ECKey ecKey = secretWordsToPriKey(secret);
        System.out.println("PriKey:"+ecKey.getPrivateKeyAsWiF(new MainNetParams()));
        System.out.println("PriKey hex:"+ecKey.getPublicKeyAsHex());
    }
    public static ECKey secretWordsToPriKey(String secretWords){
        byte[] secretBytes = secretWords.getBytes();
        byte[] hash = Hash.sha256(secretBytes);
        return ECKey.fromPrivate(hash);
    }

    public static String getPubKey(String fid, SessionHandler sessionHandler, TalkIdHandler talkIdHandler, ContactHandler contactHandler, ApipClient apipClient) {
        String pubKey = null;
        if(sessionHandler!=null){
            FcSession session = sessionHandler.getSessionByUserId(fid);
            if(session!=null)pubKey = session.getPubKey();
            if(pubKey!=null)return pubKey;
        }
        if(talkIdHandler!=null){
            TalkIdInfo talkIdInfo = talkIdHandler.get(fid);
            if(talkIdInfo!=null)pubKey = talkIdInfo.getPubKey();
            if(pubKey!=null)return pubKey;
        }
        if(contactHandler!=null){
            ContactDetail contact = contactHandler.getContact(fid);
            if(contact!=null)pubKey = contact.getPubKey();
            if(pubKey!=null)return pubKey;
        }
        if(apipClient!=null){
            pubKey = apipClient.getPubKey(fid, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        }
        return pubKey;
    }

    public static String inputPubKey(BufferedReader br) {

        String pubKeyB;
        pubKeyB = appTools.Inputer.inputString(br,"Input the recipient public key in hex or Base58:");
        if(pubKeyB==null)return null;
        try{
            return getPubKey33(pubKeyB);
        }catch (Exception e){
            System.out.println("Failed to get pubKey: "+e.getMessage());
            return null;
        }
    }

    @Test
    public void test(){
        String pubKeyStr = "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a";
        byte[] pubKey = Hex.fromHex(pubKeyStr);
        String addr = BchCashAddr.createCashAddr(pubKey);
        System.out.println(addr);
    }

    public static String scriptToMultiAddr(String script) {
        byte[] scriptBytes = Hex.fromHex(script);
        byte[] b = Hash.sha256(scriptBytes);
        byte[] h = Hash.Ripemd160(b);
        return hash160ToMultiAddr(h);
    }

    public static byte[] priKeyToPubKey(byte[] priKey32Bytes) {
        ECKey eckey = ECKey.fromPrivate(priKey32Bytes);
        return eckey.getPubKey();
    }


    public static String priKeyToPubKey(String priKey) {
        //私钥如果长度为38字节，则为压缩格式。构成为：前缀80+32位私钥+压缩标志01+4位校验位。
        byte[] priKey32Bytes;
        byte[] priKeyBytes;
        byte[] suffix;
        byte[] priKeyForHash;
        byte[] hash;
        byte[] hash4;

        int len = priKey.length();

        switch (len) {
            case 64 -> priKey32Bytes = HexFormat.of().parseHex(priKey);
            case 52 -> {
                if (!(priKey.charAt(0) == 'L' || priKey.charAt(0) == 'K')) {
                    System.out.println("It's not a private key.");
                    return null;
                }
                priKeyBytes = Base58.decode(priKey);
                suffix = new byte[4];
                priKeyForHash = new byte[34];
                System.arraycopy(priKeyBytes, 0, priKeyForHash, 0, 34);
                System.arraycopy(priKeyBytes, 34, suffix, 0, 4);
                hash = Hash.sha256x2(priKeyForHash);
                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);
                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (priKeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                priKey32Bytes = new byte[32];
                System.arraycopy(priKeyForHash, 1, priKey32Bytes, 0, 32);
            }
            case 51 -> {
                if (priKey.charAt(0) != '5') {
                    System.out.println("It's not a private key.");
                    return null;
                }
                priKeyBytes = Base58.decode(priKey);
                suffix = new byte[4];
                priKeyForHash = new byte[33];
                System.arraycopy(priKeyBytes, 0, priKeyForHash, 0, 33);
                System.arraycopy(priKeyBytes, 33, suffix, 0, 4);
                hash = Hash.sha256x2(priKeyForHash);
                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);
                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (priKeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                priKey32Bytes = new byte[32];
                System.arraycopy(priKeyForHash, 1, priKey32Bytes, 0, 32);
            }
            default -> {
                System.out.println("It's not a private key.");
                return null;
            }
        }

        ECKey eckey = ECKey.fromPrivate(priKey32Bytes);

        String pubKey = HexFormat.of().formatHex(eckey.getPubKey());

        return pubKey;
    }

    public static String priKeyToFid(byte[] priKey) {
        byte[] priKey32 = getPriKey32(priKey);
        byte[] pubKey = priKeyToPubKey(priKey32);
        return pubKeyToFchAddr(pubKey);
    }


    @Nullable
    public static byte[] importOrCreatePriKey(BufferedReader br) {
        System.out.println("""
                Input the private key...
                'b' for Base58 code.
                'c' for the cipher json.
                'h' for hex.
                'g' to generate a new one.
                other to exit:""");

        String input = Inputer.inputString(br);

        byte[] priKey32 = new byte[0];
        switch (input) {
            case "b" -> {
                do {
                    System.out.println("Input the private key in Base58:");
                    char[] priKeyBase58 = Inputer.inputPriKeyWif(br);
                    priKey32 = getPriKey32(BytesUtils.utf8CharArrayToByteArray(priKeyBase58));
                    String buyer = priKeyToFid(priKey32);
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

                    String ask = "Input the password to decrypt this priKey:";
                    char[] userPassword = Inputer.inputPassword(br, ask);

                    Decryptor decryptor = new Decryptor();

                    CryptoDataByte cryptoDataByte = decryptor.decryptJsonByPassword(cipher,userPassword);

                    BytesUtils.clearCharArray(userPassword);

                    if (cryptoDataByte.getCode()!=null && cryptoDataByte.getCode() != 0) {
                        System.out.println("Decrypt priKey cipher from input wrong." + cryptoDataByte.getMessage());
                        System.out.println("Try again.");
                        continue;
                    }
                    priKey32 = getPriKey32(cryptoDataByte.getData());
                    System.out.println("Your FID is: \n" + priKeyToFid(priKey32));
                    System.out.println("Is it right? y/n");
                    input = Inputer.inputString(br);
                } while (!"y".equals(input));
            }
            case "h" -> {
                do {
                    char[] priKeyHex = Inputer.input32BytesKey(br, "Input the private key in Hex:");
                    if (priKeyHex == null) break;
                    priKey32 = BytesUtils.hexCharArrayToByteArray(priKeyHex);
                    String buyer = priKeyToFid(priKey32);
                    System.out.println(buyer);
                    System.out.println("Is this your FID? y/n:");
                    input = Inputer.inputString(br);
                } while (!"y".equals(input));
            }
            case "g" -> {
                ECKey ecKey = generateNewPriKey(br);
                if (ecKey == null) {
                    System.out.println("Failed to generate new priKey.");
                    return null;
                }
                priKey32 = ecKey.getPrivKeyBytes();
            }
            default -> {
                return null;
            }
        }
        return priKey32;
    }

    @Nullable
    public static ECKey generateNewPriKey(BufferedReader br) {
        ECKey ecKey = new ECKey(new SecureRandom());
        String publicKeyAsHex = ecKey.getPublicKeyAsHex();
        Address address = Address.fromKey(FchMainNetwork.MAINNETWORK, ecKey);
        System.out.println("New FID:" + address.toString());
        System.out.println();
        char[] password = Inputer.inputPassword(br, "Input a password to encrypt it:");
        byte[] priKey32 = ecKey.getPrivKeyBytes();
        String cipher = EccAes256K1P7.encryptKeyWithPassword(priKey32, password);
        password = Inputer.inputPassword(br, "Check the password:");
        try {
            priKey32 = EccAes256K1P7.decryptJsonBytes(cipher, BytesUtils.utf8CharArrayToByteArray(password));
            if (priKey32 == null) {
                System.out.println("Failed to generate new priKey.");
                return null;
            }
            ECKey ecKey1 = ECKey.fromPrivate(priKey32);
            Address checkAddress = Address.fromKey(FchMainNetwork.MAINNETWORK, ecKey1);

            if (!address.toString().equals(checkAddress.toString())) {
                System.out.println("Failed to generate new priKey.");
                return null;
            }
            System.out.println("New priKey is ready:");
            Shower.printUnderline(10);
            System.out.println("PriKey:" + ecKey.getPrivateKeyAsWiF(FchMainNetwork.MAINNETWORK));
            Shower.printUnderline(10);
            System.out.println("FID:" + address);
            System.out.println("PubKey:" + publicKeyAsHex);
            System.out.println("PriKeyCipher:" + cipher);
            Shower.printUnderline(10);
            System.out.println("* Keep the priKey cipher and the password carefully." +
                    "\n* They are both required to recover the priKey.");
        } catch (Exception e) {
            System.out.println("Failed to generate new priKey.");
        }
        return ecKey;
    }

    public static ECKey genNewFid(BufferedReader br) {
        MainNetParams netParams = FchMainNetwork.MAINNETWORK;
        ECKey ecKey = new ECKey();
        byte[] priKey32 = ecKey.getPrivKeyBytes();
        System.out.println("New FID generated:");
        Shower.printUnderline(60);
        Address fid = Address.fromKey(netParams, ecKey);
        System.out.println(fid);
        String privateKeyAsWiF = ecKey.getPrivateKeyAsWiF(netParams);
        System.out.println("PriKey WIF:" + privateKeyAsWiF);
        QRCodeUtils.generateQRCode(privateKeyAsWiF);
        System.out.println("PriKey hex:" + HexFormat.of().formatHex(priKey32));
        
        Shower.printUnderline(60);
        System.out.println("* Warning: To copy and paste priKey is dangerous with an online device!\n");
        char[] password = Inputer.inputPassword(br, "Input a password to encrypt your new private key:");
        String userCipher = EccAes256K1P7.encryptKeyWithPassword(priKey32, password);
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
    public static String getPubKeyHexUncompressed(String pubKey33) {
        String pubKey65 = KeyTools.recoverPK33ToPK65(pubKey33);
        if(pubKey65==null)return null;
        byte[] pubKeyBytes = HexFormat.of().parseHex(pubKey65);
        return HexFormat.of().formatHex(pubKeyBytes);
    }
    public static String getPubKeyWifUncompressed(String pubKey33) {
        String pubKey65 = KeyTools.recoverPK33ToPK65(pubKey33);
        byte[] pubKeyBytes = HexFormat.of().parseHex(pubKey65);
        return Base58.encodeChecked(0, pubKeyBytes);
    }

    public static String getPubKeyWifCompressedWithVer0(String pubKey33) {
        byte[] pubKeyBytes = HexFormat.of().parseHex(pubKey33);
        return Base58.encodeChecked(0, pubKeyBytes);
    }

    public static String getPubKeyWifCompressedWithoutVer(String pubKey33) {
        byte[] pubKeyBytes = HexFormat.of().parseHex(pubKey33);
        return crypto.Base58.encodeChecked(pubKeyBytes);
    }

    public static Map<String, String> pubKeyToAddresses(String pubKey) {

        String pubKey33;

        if (pubKey.length() == 130) {
            try {
                pubKey33 = compressPk65To33(pubKey);
            } catch (Exception e) {
                return null;
            }
        } else {
            pubKey33 = pubKey;
        }

        String fchAddr = pubKeyToFchAddr(pubKey33);
        String btcAddr = pubKeyToBtcAddr(pubKey33);
        String ethAddr = pubKeyToEthAddr(pubKey);
        String ltcAddr = pubKeyToLtcAddr(pubKey33);
        String dogeAddr = pubKeyToDogeAddr(pubKey33);
        String trxAddr = pubKeyToTrxAddr(pubKey33);
        String bchAddr = pubKeyToBchBesh32Addr(pubKey33);

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

//   public static String pubKeyToBtcBech32Addr(String pubKeyHex) {
//       byte[] hash160 = pubKeyToHash160(pubKeyHex);
//       return BtcAddrConverter.hash160ToBech32(hash160);
//   }

    public static String pubKeyToBchBesh32Addr(String pubKey33) {
        byte[] pubKeyBytes = HexFormat.of().parseHex(pubKey33);
        return BchCashAddr.createCashAddr(pubKeyBytes);
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

    public static Map<String, String> pubKeyToAddresses(byte[] pubKey) {
        String pubKeyStr = HexFormat.of().formatHex(pubKey);
        return pubKeyToAddresses(pubKeyStr);
    }

    public static String parsePkFromUnlockScript(String hexScript) {
        byte[] bScript = HexFormat.of().parseHex(hexScript);//HexFormat.of().parseHex(hexScript);
        int sigLen = Byte.toUnsignedInt(bScript[0]);//Length of signature;
        //Skip signature/跳过签名。
        //Read pubKey./读公钥
        byte pubKeyLenB = bScript[sigLen + 1]; //公钥长度
        int pubKeyLen = Byte.toUnsignedInt(pubKeyLenB);
        byte[] pubKeyBytes = new byte[pubKeyLen];
        System.arraycopy(bScript, sigLen + 2, pubKeyBytes, 0, pubKeyLen);
        return HexFormat.of().formatHex(pubKeyBytes);//HexFormat.of().formatHex(pubKeyBytes);
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
        String pubKey65 = recoverPK33ToPK65(str);
        if (pubKey65 != null)
            return HexFormat.of().parseHex(pubKey65);
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

    public static String pubKeyToFchAddr(String a) {
        byte[] h = pubKeyToHash160(HexFormat.of().parseHex(a));
        return hash160ToFchAddr(h);
    }
    
    private static byte[] pubKeyToHash160(String a) {
        return pubKeyToHash160(HexFormat.of().parseHex(a));
    }

    private static byte[] pubKeyToHash160(byte[] pubKeyBytes) {
        byte[] b = Hash.sha256(pubKeyBytes);
        byte[] h = Hash.Ripemd160(b);
        return h;
    }

    public static String pubKeyToFchAddr(byte[] a) {
        byte[] b = Hash.sha256(a);
        byte[] h = Hash.Ripemd160(b);
        return hash160ToFchAddr(h);
    }

    public static String pubKeyToMultiSigAddr(String a) {
        byte[] h = pubKeyToHash160(a);
        return hash160ToMultiAddr(h);
    }

    public static String pubKeyToMultiSigAddr(byte[] a) {
        byte[] b = Hash.sha256(a);
        byte[] h = Hash.Ripemd160(b);
        return hash160ToMultiAddr(h);
    }

    public static String pubKeyToBtcAddr(String a) {
        byte[] h = pubKeyToHash160(a);
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

    public static String pubKeyToBtcAddr(byte[] a) {
        byte[] b = Hash.sha256(a);
        byte[] h = Hash.Ripemd160(b);
        return hash160ToBtcAddr(h);
    }

    public static String pubKeyToTrxAddr(String a) {
        if (a == null) return null;
        String pubKey65;
        if (a.length() == 130) {
            pubKey65 = a;
        } else {
            pubKey65 = recoverPK33ToPK65(a);
        }
        if (pubKey65 == null) return null;
        String pubKey64 = pubKey65.substring(2);

        byte[] pubKey64Bytes = HexFormat.of().parseHex(pubKey64);
        byte[] pukHash64Hash = Hash.sha3(pubKey64Bytes);

        byte[] pukHashWithPrefix = new byte[21];
        pukHashWithPrefix[0] = 0x41;
        System.arraycopy(pukHash64Hash, 12, pukHashWithPrefix, 1, 20);

        byte[] c = Hash.sha256x2(pukHashWithPrefix);
        byte[] f = new byte[4];
        System.arraycopy(c, 0, f, 0, 4);
        byte[] addrRaw = BytesUtils.bytesMerger(pukHashWithPrefix, f);

        return Base58.encode(addrRaw);
    }

    public static String pubKeyToDogeAddr(String a) {
        byte[] h = pubKeyToHash160(a);
        return hash160ToDogeAddr(h);
    }

    public static String pubKeyToDogeAddr(byte[] a) {
        byte[] b = Hash.sha256(a);
        byte[] h = Hash.Ripemd160(b);
        return hash160ToDogeAddr(h);
    }

    public static String pubKeyToLtcAddr(String a) {
        byte[] h = pubKeyToHash160(a);
        return hash160ToLtcAddr(h);
    }

    public static String pubKeyToLtcAddr(byte[] a) {
        byte[] b = Hash.sha256(a);
        byte[] h = Hash.Ripemd160(b);
        String address = hash160ToLtcAddr(h);
        return address;
    }

    public static String pubKeyToEthAddr(String a) {

        String pubKey65;
        if (a.length() == 130) {
            pubKey65 = a;
        } else {
            pubKey65 = recoverPK33ToPK65(a);
        }

        String pubKey64 = pubKey65.substring(2);

        byte[] pubKey64Bytes = HexFormat.of().parseHex(pubKey64);
        byte[] pukHash64Hash = Hash.sha3(pubKey64Bytes);
        String fullHash = HexFormat.of().formatHex(pukHash64Hash);

        return "0x" + fullHash.substring(24);
    }

    public static String pubKeyToEthAddr(byte[] b) {
        String a = HexFormat.of().formatHex(b);

        String pubKey65 = recoverPK33ToPK65(a);

        String pubKey64 = pubKey65.substring(2);

        byte[] pubKey64Bytes = HexFormat.of().parseHex(pubKey64);
        byte[] pukHash64Hash = Hash.sha3(pubKey64Bytes);
        String fullHash = HexFormat.of().formatHex(pukHash64Hash);

        return "0x" + fullHash.substring(24);
    }

    public static byte[] getPriKey32(byte[] priKey) {
        byte[] priKey32Bytes;
        byte[] priKeyBytes;
        byte[] suffix;
        byte[] priKeyForHash;
        byte[] hash;
        byte[] hash4;
        int len = priKey.length;

        if (len == 52) {
            char[] priKeyUtf8Chars = BytesUtils.byteArrayToUtf8CharArray(priKey);
            BytesUtils.clearByteArray(priKey);
            priKey = crypto.Base58.base58CharArrayToByteArray(priKeyUtf8Chars);
            len = priKey.length;
        }

        switch (len) {
            case 32 -> priKey32Bytes = priKey;
            case 38 -> {
                priKeyBytes = priKey;
                suffix = new byte[4];
                priKeyForHash = new byte[34];
                System.arraycopy(priKeyBytes, 0, priKeyForHash, 0, 34);
                System.arraycopy(priKeyBytes, 34, suffix, 0, 4);
                hash = Hash.sha256x2(priKeyForHash);
                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);
                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (priKeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                priKey32Bytes = new byte[32];
                System.arraycopy(priKeyForHash, 1, priKey32Bytes, 0, 32);
            }
            case 37 -> {
                priKeyBytes = priKey;
                suffix = new byte[4];
                priKeyForHash = new byte[33];
                System.arraycopy(priKeyBytes, 0, priKeyForHash, 0, 33);
                System.arraycopy(priKeyBytes, 33, suffix, 0, 4);
                hash = Hash.sha256x2(priKeyForHash);
                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);
                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (priKeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                priKey32Bytes = new byte[32];
                System.arraycopy(priKeyForHash, 1, priKey32Bytes, 0, 32);
            }
            default -> {
                System.out.println("It's not a private key.");
                return null;
            }
        }

        return priKey32Bytes;
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

    public static boolean isValidPubKey(String puk) {
        String prefix = "";
        if (puk.length() > 2) prefix = puk.substring(0, 2);
        if (puk.length() == 66) {
            return prefix.equals("02") || prefix.equals("03");
        } else if (puk.length() == 130) {
            return prefix.equals("04");
        }
        return false;
    }

    public static String priKey32To38WifCompressed(String priKey32) {
        /*
        26字节长度为WIF compressed私钥格式。过程：
        在32位私钥加入版本号前缀0x80
        再加入压缩标志后缀0x01
        sha256x2(80+priKey32+01)取前4字节checksum
        对80+priKey32+01+checksum取base58编码
         */
        if(priKey32.startsWith("0x")||priKey32.startsWith("0X"))priKey32 = priKey32.substring(2);
        String priKey26;
        if (priKey32.length() != 64) {
            System.out.println("Private keys must be 32 bytes");
            return null;
        }
        // Keys that have compressed public components have an extra 1 byte on the end in dumped form.
        byte[] bytes32 = BytesUtils.hexToByteArray(priKey32);
        byte[] bytes38 = priKey32To38Compressed(bytes32);

        priKey26 = Base58.encode(bytes38);
        return priKey26;
    }

    

    public static byte[] priKey32To38Compressed(byte[] bytes32) {
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

    public static String priKey32To37(String priKey32) {
        /*
        26字节长度为WIF compressed私钥格式。过程：
        在32位私钥加入版本号前缀0x80
        sha256x2(80+priKey32+01)取前4字节checksum
        对80+priKey32+01+checksum取base58编码
         */

        String priKey37;
        if (priKey32.length() != 64) {
            System.out.println("Private keys must be 32 bytes");
            return null;
        }
        // Keys that have compressed public components have an extra 1 byte on the end in dumped form.
        byte[] bytes33 = new byte[33];
        byte[] bytes32 = BytesUtils.hexToByteArray(priKey32);
        bytes33[0] = (byte) 0x80;
        System.arraycopy(bytes32, 0, bytes33, 1, 32);

        byte[] hash = Hash.sha256x2(bytes33);
        byte[] hash4 = new byte[4];
        System.arraycopy(hash, 0, hash4, 0, 4);

        byte[] bytes37 = new byte[37];

        System.arraycopy(bytes33, 0, bytes37, 0, 33);
        System.arraycopy(hash4, 0, bytes37, 33, 4);

        priKey37 = Base58.encode(bytes37);
        return priKey37;
    }

    public static void showPubKeys(String pubKey) {
        Shower.printUnderline(4);
        System.out.println("- Public key compressed in hex:\n" + pubKey);
        System.out.println("- Public key uncompressed in hex:\n" + getPubKeyHexUncompressed(pubKey));

        System.out.println("- Public key WIF uncompressed:\n" + getPubKeyWifUncompressed(pubKey));
        System.out.println("- Public key WIF compressed with version 0:\n" + getPubKeyWifCompressedWithVer0(pubKey));
        System.out.println("- Public key WIF compressed without version:\n" + getPubKeyWifCompressedWithoutVer(pubKey));
    }

    @NotNull
    public static String getPubKey33(String pubKey) {
        switch (pubKey.length()) {
            case 66 -> {
                if (pubKey.startsWith("02") || pubKey.startsWith("03")) return pubKey;
            }
            case 130 -> {
                if (pubKey.startsWith("04")) return compressPk65To33(pubKey);
            }
            case 50 -> {
                return HexFormat.of().formatHex(Base58.decodeChecked(pubKey));
            }
            case 51 -> {
                return HexFormat.of().formatHex(Base58.decodeChecked(pubKey)).substring(2);
            }
        }
        return null;
    }

    public static byte[] getPriKey32(String priKey) {
        byte[] priKey32Bytes;
        byte[] priKeyBytes;
        byte[] suffix;
        byte[] priKeyForHash;
        byte[] hash;
        byte[] hash4;
        int len = priKey.length();


        switch (len) {
            case 64:
                priKey32Bytes = HexFormat.of().parseHex(priKey);
                break;
            case 52:
                if (!(priKey.substring(0, 1).equals("L") || priKey.substring(0, 1).equals("K"))) {
                    System.out.println("It's not a private key.");
                    return null;
                }
                priKeyBytes = Base58.decode(priKey);

                suffix = new byte[4];
                priKeyForHash = new byte[34];

                System.arraycopy(priKeyBytes, 0, priKeyForHash, 0, 34);
                System.arraycopy(priKeyBytes, 34, suffix, 0, 4);

                hash = Sha256Hash.hashTwice(priKeyForHash);

                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);

                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (priKeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                priKey32Bytes = new byte[32];
                System.arraycopy(priKeyForHash, 1, priKey32Bytes, 0, 32);
                break;
            case 51:
                if (!priKey.substring(0, 1).equals("5")) {
                    System.out.println("It's not a private key.");
                    return null;
                }

                priKeyBytes = Base58.decode(priKey);

                suffix = new byte[4];
                priKeyForHash = new byte[33];

                System.arraycopy(priKeyBytes, 0, priKeyForHash, 0, 33);
                System.arraycopy(priKeyBytes, 33, suffix, 0, 4);

                hash = Sha256Hash.hashTwice(priKeyForHash);

                hash4 = new byte[4];
                System.arraycopy(hash, 0, hash4, 0, 4);

                if (!Arrays.equals(suffix, hash4)) {
                    return null;
                }
                if (priKeyForHash[0] != (byte) 0x80) {
                    return null;
                }
                priKey32Bytes = new byte[32];
                System.arraycopy(priKeyForHash, 1, priKey32Bytes, 0, 32);
                break;
            default:
                System.out.println("It's not a private key.");
                return null;
        }

        return priKey32Bytes;
    }

    public static ECPublicKeyParameters pubKeyFromBytes(byte[] publicKeyBytes) {

        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());

        ECCurve curve = domainParameters.getCurve();

        ECPoint point = curve.decodePoint(publicKeyBytes);

        return new ECPublicKeyParameters(point, domainParameters);
    }

    public static ECPublicKeyParameters pubKeyFromHex(String publicKeyHex) {
        return pubKeyFromBytes(HexFormat.of().parseHex(publicKeyHex));
    }

    public static String pubKeyToHex(ECPublicKeyParameters publicKey) {
        return Hex.toHex(pubKeyToBytes(publicKey));
    }

    public static String priKeyToHex(ECPrivateKeyParameters privateKey) {
        BigInteger privateKeyValue = privateKey.getD();
        String hex = privateKeyValue.toString(16);
        while (hex.length() < 64) {  // 64 is for 256-bit key
            hex = "0" + hex;
        }
        return hex;
    }

    public static byte[] priKeyToBytes(ECPrivateKeyParameters privateKey) {
        return HexFormat.of().parseHex(priKeyToHex(privateKey));//Hex.decode(priKeyToHex(privateKey));
    }

    public static byte[] pubKeyToBytes(ECPublicKeyParameters publicKey) {
        return publicKey.getQ().getEncoded(true);
    }

    public static ECPrivateKeyParameters priKeyFromBytes(byte[] privateKey) {
        return priKeyFromHex(HexFormat.of().formatHex(privateKey));
    }

    public static ECPrivateKeyParameters priKeyFromHex(String privateKeyHex) {
        BigInteger privateKeyValue = new BigInteger(privateKeyHex, 16); // Convert hex to BigInteger
        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1"); // Use the same curve name as in key pair generation
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());
        return new ECPrivateKeyParameters(privateKeyValue, domainParameters);
    }

    public static ECPublicKeyParameters pubKeyFromPriKey(ECPrivateKeyParameters privateKey) {
        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());

        ECPoint Q = domainParameters.getG().multiply(privateKey.getD()); // Scalar multiplication of base point (G) and private key

        return new ECPublicKeyParameters(Q, domainParameters);
    }
//    public static Object getSessionKeyOrPubKey(String fid, SessionHandler sessionHandler, TalkIdHandler talkIdHandler, ContactHandler contactHandler, ApipClient apipClient) {
//        FcSession session = sessionHandler.getSessionById(fid);
//        if (session != null && session.getKeyBytes() != null) {
//            return session.getKeyBytes();
//        } else {
//            return getPubKey(fid, sessionHandler, talkIdHandler,contactHandler, apipClient);
//        }
//    }
    public static boolean isPubKey(String owner) {
        return Hex.isHexString(owner) 
        && (
            (owner.length() == 66 && (owner.startsWith("02") || owner.startsWith("03")))
            || (owner.length() == 130 && owner.startsWith("04"))
        ) ;
    }

//    public static String pubKeyToBchAddr(String a) {
//        byte[] pubKey = Hex.decode(a);
//        return CashAddress.createCashAddr(pubKey);
//    }
}
