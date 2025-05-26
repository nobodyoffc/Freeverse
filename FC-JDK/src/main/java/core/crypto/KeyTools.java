package core.crypto;

import clients.ApipClient;
import constants.Constants;
import core.crypto.old.EccAes256K1P7;
import ui.Shower;


import data.fcData.ContactDetail;
import data.fcData.FcSession;
import data.fcData.TalkIdInfo;
import handlers.ContactHandler;
import handlers.SessionHandler;
import handlers.TalkIdHandler;
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
        ECKey ecKey = secretWordsToPrikey(secret);
        System.out.println("Prikey:"+ecKey.getPrivateKeyAsWiF(new MainNetParams()));
        System.out.println("Prikey hex:"+ecKey.getPublicKeyAsHex());
    }
    public static ECKey secretWordsToPrikey(String secretWords){
        byte[] secretBytes = secretWords.getBytes();
        byte[] hash = Hash.sha256(secretBytes);
        return ECKey.fromPrivate(hash);
    }

    public static String getPubkey(String fid, SessionHandler sessionHandler, TalkIdHandler talkIdHandler, ContactHandler contactHandler, ApipClient apipClient) {
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
            ContactDetail contact = contactHandler.getContact(fid);
            if(contact!=null)pubkey = contact.getPubkey();
            if(pubkey!=null)return pubkey;
        }
        if(apipClient!=null){
            pubkey = apipClient.getPubkey(fid, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        char[] password = Inputer.inputPassword(br, "Input a password to encrypt it:");
        byte[] prikey32 = ecKey.getPrivKeyBytes();
        String cipher = EccAes256K1P7.encryptKeyWithPassword(prikey32, password);
        password = Inputer.inputPassword(br, "Check the password:");
        try {
            prikey32 = EccAes256K1P7.decryptJsonBytes(cipher, BytesUtils.utf8CharArrayToByteArray(password));
            if (prikey32 == null) {
                System.out.println("Failed to generate new prikey.");
                return null;
            }
            ECKey ecKey1 = ECKey.fromPrivate(prikey32);
            Address checkAddress = Address.fromKey(FchMainNetwork.MAINNETWORK, ecKey1);

            if (!address.toString().equals(checkAddress.toString())) {
                System.out.println("Failed to generate new prikey.");
                return null;
            }
            System.out.println("New prikey is ready:");
            Shower.printUnderline(10);
            System.out.println("Prikey:" + ecKey.getPrivateKeyAsWiF(FchMainNetwork.MAINNETWORK));
            Shower.printUnderline(10);
            System.out.println("FID:" + address);
            System.out.println("Pubkey:" + publicKeyAsHex);
            System.out.println("PrikeyCipher:" + cipher);
            Shower.printUnderline(10);
            System.out.println("* Keep the prikey cipher and the password carefully." +
                    "\n* They are both required to recover the prikey.");
        } catch (Exception e) {
            System.out.println("Failed to generate new prikey.");
        }
        return ecKey;
    }

    public static ECKey genNewFid(BufferedReader br) {
        MainNetParams netParams = FchMainNetwork.MAINNETWORK;
        ECKey ecKey = new ECKey();
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

    public static String parsePkFromUnlockScript(String hexScript) {
        byte[] bScript = HexFormat.of().parseHex(hexScript);//HexFormat.of().parseHex(hexScript);
        int sigLen = Byte.toUnsignedInt(bScript[0]);//Length of signature;
        //Skip signature/跳过签名。
        //Read pubkey./读公钥
        byte pubkeyLenB = bScript[sigLen + 1]; //公钥长度
        int pubkeyLen = Byte.toUnsignedInt(pubkeyLenB);
        byte[] pubkeyBytes = new byte[pubkeyLen];
        System.arraycopy(bScript, sigLen + 2, pubkeyBytes, 0, pubkeyLen);
        return HexFormat.of().formatHex(pubkeyBytes);//HexFormat.of().formatHex(pubkeyBytes);
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
//    public static Object getSessionKeyOrPubkey(String fid, SessionHandler sessionHandler, TalkIdHandler talkIdHandler, ContactHandler contactHandler, ApipClient apipClient) {
//        FcSession session = sessionHandler.getSessionById(fid);
//        if (session != null && session.getKeyBytes() != null) {
//            return session.getKeyBytes();
//        } else {
//            return getPubkey(fid, sessionHandler, talkIdHandler,contactHandler, apipClient);
//        }
//    }
    public static boolean isPubkey(String owner) {
        return Hex.isHexString(owner) 
        && (
            (owner.length() == 66 && (owner.startsWith("02") || owner.startsWith("03")))
            || (owner.length() == 130 && owner.startsWith("04"))
        ) ;
    }

//    public static String pubkeyToBchAddr(String a) {
//        byte[] pubkey = Hex.decode(a);
//        return CashAddress.createCashAddr(pubkey);
//    }
}
