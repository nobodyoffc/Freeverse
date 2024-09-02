package startTools;

import appTools.Inputer;
import appTools.Menu;
import constants.Constants;

import crypto.*;
import fcData.AlgorithmId;
import javaTools.BytesTools;
import javaTools.Hex;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.MainNetParams;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;

import static crypto.Hash.getSign;
import static javaTools.FileTools.getAvailableFile;


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

        while(true) {
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
                case 26-> decryptFileSymKey(br);
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
        Decryptor decryptor = new Decryptor();
        try {
            System.out.println("Input the bundle in Base64:");
            String bundle = br.readLine();
            if(bundle==null){
                System.out.println("Bundle is null.");
                return;
            }
            String ask = "Input the password:";
            char[] password = Inputer.inputPassword(br,ask);
            if(password==null) return;
            byte[] bundleBytes = Base64.getDecoder().decode(bundle);
            CryptoDataByte cryptoDataByte  = decryptor.decryptBundleByPassword(bundleBytes,password);
            System.out.println(new String(cryptoDataByte.getData()));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void encryptWithPasswordBundle(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
        String msg = Inputer.inputMsg(br);
        String ask = "Input the password:";
        char[] password = Inputer.inputPassword(br, ask);
        assert msg != null;
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] bundle = encryptor.encryptToBundleByPassword(msgBytes, password);
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    private static void decryptAsyTwoWayBundle(BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        try {
            System.out.println("Input the bundle in Base64:");
            String bundle = br.readLine();
            if(bundle==null){
                System.out.println("Bundle is null.");
                return;
            }

            System.out.println("Input the pubKey in hex:");

            String pubKey = br.readLine();

            String ask = "Input the priKey in hex:";
            char[] priKey = Inputer.input32BytesKey(br, ask);
            byte[] bundleBytes = Base64.getDecoder().decode(bundle);
            byte[] priKeyBytes = BytesTools.charArrayToByteArray(priKey,StandardCharsets.UTF_8);
            System.out.println(decryptor.decryptBundleByAsyTwoWay(bundleBytes,priKeyBytes,Hex.fromHex(pubKey)));
        } catch (IOException e) {
            System.out.println("Something wrong:"+e.getMessage());
        }
        Menu.anyKeyToContinue(br);
    }

    private static void decryptAsyOneWayBundle(BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        try {
            System.out.println("Input the bundle in Base64:");
            String bundle = br.readLine();
            if(bundle==null){
                System.out.println("Bundle is null.");
                return;
            }
            String ask = "Input the priKey in hex:";
            char[] priKey = Inputer.input32BytesKey(br, ask);

            System.out.println(decryptor.decryptBundleByAsyOneWay(Base64.getDecoder().decode(bundle),BytesTools.utf8CharArrayToByteArray(priKey)));

        } catch (IOException e) {
            System.out.println("Something wrong:"+e.getMessage());
        }

        Menu.anyKeyToContinue(br);
    }

    private static void encryptAsyTwoWayBundle(BufferedReader br) {
        Encryptor encryptor = new Encryptor();
        String pubKeyB;
        String msg;
        String priKeyA;
        byte[]  priKeyABytes = new byte[0];
        while (true) {
            try {
                System.out.println("Input the recipient public key in hex:");
                pubKeyB = br.readLine();
                if (pubKeyB.length() != 66) {
                    System.out.println("The public key should be 66 characters of hex.");
                    continue;
                }
                String ask = "Input the sender's private Key:";
                priKeyA = Inputer.inputString(br);
                if (priKeyA == null) {
                    System.out.println("A private Key is required.");
                    continue;
                }

                if(Base58.isBase58Encoded(priKeyA))priKeyABytes = Base58.decode(priKeyA);
                else if(Hex.isHexString(priKeyA))priKeyABytes = Hex.fromHex(priKeyA);
                System.out.println("Input the msg:");
                msg = br.readLine();
                if("".equals(msg))return;
                break;
            } catch (Exception e) {
                System.out.println("BufferedReader wrong.");
                return;
            }
        }
        byte[] bundle = encryptor.encryptByAsyTwoWayToBundle(msg.getBytes(), priKeyABytes, Hex.fromHex(pubKeyB));//ecc.encryptAsyTwoWayBundle(eccAesData.getMsg(),eccAesData.getPubKeyB(),eccAesData.getPriKeyA());
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    private static CryptoDataByte getEncryptedEccAesDataTwoWay(BufferedReader br) {
        CryptoDataByte cryptoDataByte =new CryptoDataByte();
        String pubKeyB;
        String msg;
        String priKeyA;
        try {
            System.out.println("Input the recipient public key in hex:");
            pubKeyB = br.readLine();
            if (pubKeyB.length() != 66) {
                System.out.println("The public key should be 66 characters of hex.");
                return null;
            }
            cryptoDataByte.setPubKeyB(Hex.fromHex(pubKeyB));
            String ask = "Input the sender's private Key:";
            priKeyA = Inputer.inputString(br,ask);
            if(priKeyA==null)return null;
            byte[] priKeyABytes = new byte[0];
            if(Base58.isBase58Encoded(priKeyA))priKeyABytes = Base58.decode(priKeyA);
            else if(Hex.isHexString(priKeyA))priKeyABytes = Hex.fromHex(priKeyA);
            cryptoDataByte.setPriKeyA(priKeyABytes);
            System.out.println("Input the msg:");
            msg = br.readLine();
            cryptoDataByte.setData(msg.getBytes());
        }catch (Exception e){
            System.out.println("BufferedReader wrong.");
            return null;
        }
        return cryptoDataByte;
    }


    private static void encryptAsyOneWayBundle(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = getEncryptedEccAesDataOneWay(br);
        if(cryptoDataByte== null) return;
        if(cryptoDataByte.getData()==null){
            System.out.println( "Error: no message.");
            return;
        }
        byte[] bundle = encryptor.encryptByAsyOneWayToBundle(cryptoDataByte.getData(), cryptoDataByte.getPubKeyB());
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    private static void decryptFileSymKey(BufferedReader br) {
        File encryptedFile = getAvailableFile(br);
        Decryptor decryptor = new Decryptor();
        if(encryptedFile==null||encryptedFile.length()> Constants.MAX_FILE_SIZE_M * Constants.M_BYTES)return;
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        CryptoDataByte result = decryptor.decryptFileBySymKey(encryptedFile.getName(), "decrypted" + encryptedFile.getName(), BytesTools.hexCharArrayToByteArray(symKey));
        System.out.println(new String(result.getData()));
        Menu.anyKeyToContinue(br);
    }

    private static void encryptFileWithSymKey(BufferedReader br) {
        File originalFile = getAvailableFile(br);
        if(originalFile==null||originalFile.length()> Constants.MAX_FILE_SIZE_M * Constants.M_BYTES)return;

        String ask = "Input the symKey in hex:";
        char[] symKeyHex = Inputer.input32BytesKey(br, ask);
        if(symKeyHex==null) return;
        byte[] symKey = BytesTools.hexCharArrayToByteArray(symKeyHex);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
        String destFileName = Encryptor.makeEncryptedFileName(originalFile.getName());
        System.out.println(encryptor.encryptFileBySymKey(originalFile.getName(),destFileName,symKey));
        Menu.anyKeyToContinue(br);
    }

    private static void decryptWithSymKeyBundle(BufferedReader br) {
        System.out.println("Input the bundle in Base64:");
        String bundle;
        byte[] bundleBytes;
        try {
            bundle = br.readLine();
            if("".equals(bundle))return;
            bundleBytes = Base64.getDecoder().decode(bundle);
            if(bundleBytes==null)return;
        } catch (IOException e) {
            System.out.println("BufferedReader wrong;");
            return;
        }
        Decryptor decryptor = new Decryptor();
        String ask = "Input the symKey in hex:";
        char[] symKeyHex = Inputer.input32BytesKey(br, ask);
        if(symKeyHex==null) return;
        byte[] symKey = BytesTools.hexCharArrayToByteArray(symKeyHex);
        CryptoDataByte cryptoDataByte = decryptor.decryptBundleBySymKey(bundleBytes,symKey);

        System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }
    private static void encryptWithSymKeyBundle(BufferedReader br) {
        String msg = Inputer.inputMsg(br);
        if(msg==null)return;
        String ask = "Input the symKey in hex:";
        char[] symKeyHex = Inputer.input32BytesKey(br, ask);
        if(symKeyHex==null) return;
        byte[] symKey = BytesTools.hexCharArrayToByteArray(symKeyHex);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
        byte[] bundle = encryptor.encryptToBundleBySymKey(msg.getBytes(),symKey);
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }



    private static void decryptFileAsy(BufferedReader br) {
        File encryptedFile = getAvailableFile(br);
        if(encryptedFile==null||encryptedFile.length()> Constants.MAX_FILE_SIZE_M * Constants.M_BYTES)return;

        byte[] priKey = fch.Inputer.inputPriKeyHexOrBase58(br);
        if(priKey == null)return;
        System.out.println("Input the recipient private key in hex:");
        Decryptor decryptor = new Decryptor();
        String plainFileName = Decryptor.recoverEncryptedFileName(encryptedFile.getName());
        CryptoDataByte cryptoDataByte = decryptor.decryptFileByAsyOneWay(encryptedFile.getName(),plainFileName,priKey);
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    private static void encryptFileAsy(BufferedReader br) {

        File originalFile = getAvailableFile(br);
        if(originalFile==null||originalFile.length()> Constants.MAX_FILE_SIZE_M * Constants.M_BYTES)return;
        String pubKeyB;
        pubKeyB = getPubKey(br);
        if (pubKeyB == null) return;
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        String dataFileName = originalFile.getName();
        CryptoDataByte cryptoDataByte = encryptor.encryptFileByAsyOneWay(dataFileName,Encryptor.makeEncryptedFileName(dataFileName),Hex.fromHex(pubKeyB));
        System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }

    private static String getPubKey(BufferedReader br) {
        System.out.println("Input the recipient public key in hex:");
        String pubKeyB;
        try {
            pubKeyB = br.readLine();
        } catch (IOException e) {
            System.out.println("BufferedReader wrong:"+e.getMessage());
            return null;
        }
        if(pubKeyB.length()!=66){
            System.out.println("The public key should be 66 characters of hex.");
        }
        return pubKeyB;
    }


    private static void gainTimestamp() {
        long timestamp = System.currentTimeMillis();
        System.out.println(timestamp);
    }

    private static void encryptWithSymKey(BufferedReader br)  {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
        String msg = Inputer.inputMsg(br);
        if(msg==null)return;
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        if(symKey==null)return;
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(msg.getBytes(),BytesTools.hexCharArrayToByteArray(symKey));
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    private static void decryptWithSymKey(BufferedReader br) throws Exception {

        System.out.println("Input the cipher json string:");
        String cipherJson = br.readLine();
        Decryptor decryptor = new Decryptor();
        String ask = "Input the symKey in hex:";
        byte[] symKey = Inputer.inputSymKey32(br,ask);
        if(symKey==null)return;
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(cipherJson,symKey);
        System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }

    private static void encryptWithPassword(BufferedReader br) throws IOException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, NoSuchProviderException {
        // TODO Auto-generated method stub

        String ask = "Input the password no longer than 64:";
        char[] password = Inputer.inputPassword(br, ask);
        if(password==null)return;
        System.out.println("Password:"+Arrays.toString(password));

        System.out.println("Input the plaintext:");
        String msg = br.readLine();
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptByPassword(msg.getBytes(),password);
        System.out.println(cryptoDataByte.toNiceJson());

        Menu.anyKeyToContinue(br);
    }

    private static void decryptWithPassword(BufferedReader br) throws Exception {
        System.out.println("Input the cipher json:");
        String eccAesDataJson = br.readLine();
        CryptoDataByte cryptoDataByte;
        while(true) {
            String ask = "Input the password no longer than 64:";
            char[] password = Inputer.inputPassword(br, ask);
            if(password==null)return;
            Decryptor decryptor = new Decryptor();

            cryptoDataByte = decryptor.decryptJsonByPassword(eccAesDataJson,password);
            if(cryptoDataByte.getCode()==0)break;
            System.out.println("Wrong password. Try again.");
        }
        System.out.println(new String(cryptoDataByte.getData()));

        Menu.anyKeyToContinue(br);
    }


    private static void decryptAsy(BufferedReader br) throws Exception {
        // TODO Auto-generated method stub

        System.out.println("Input the json string of EccAesData:");
        String eccAesDataJson = br.readLine();

        decryptAsyJson(br, eccAesDataJson);

        Menu.anyKeyToContinue(br);

    }

    private static void decryptAsyJson(BufferedReader br, String eccAesDataJson)  {
        System.out.println("Input the recipient private key in hex:");
        byte[] priKey = fch.Inputer.inputPriKeyHexOrBase58(br);
        if(priKey==null)return;
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonByAsyOneWay(eccAesDataJson,priKey);
        System.out.println(new String(cryptoDataByte.getData()));
    }

    private static void decryptWithBtcEcc() {
        // TODO Auto-generated method stub

        System.out.println("Input the ciphertext encrypted with BtcAlgorithm:");
    }

    private static void encryptAsy(BufferedReader br)  {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = getEncryptedEccAesDataOneWay(br);
        assert cryptoDataByte != null;
        cryptoDataByte = encryptor.encryptByAsyOneWay(cryptoDataByte.getData(),cryptoDataByte.getPubKeyB());
        if (cryptoDataByte  == null) return;
        if(cryptoDataByte.getCode()!=0){
            System.out.println(cryptoDataByte.getMessage());
        }else System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    private static CryptoDataByte getEncryptedEccAesDataOneWay(BufferedReader br) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        System.out.println("Input the recipient public key in hex:");
        String pubKeyB;
        String msg;
        try {
            pubKeyB = br.readLine();
            if (pubKeyB==null || pubKeyB.length() != 66) {
                System.out.println("The public key should be 66 characters of hex.");
                return null;
            }
            cryptoDataByte.setPubKeyB(Hex.fromHex(pubKeyB));
            System.out.println("Input the msg:");
            msg = br.readLine();
            if("".equals(msg))return null;
            cryptoDataByte.setData(msg.getBytes());
        }catch (Exception e){
            System.out.println("BufferedReader wrong.");
            return null;
        }
        return cryptoDataByte;
    }

    private static void encryptWithBtcEcc() {
        // TODO Auto-generated method stub
        System.out.println("encryptWithBtcAlgo is under developing:");
    }

    public static void getRandom(BufferedReader br) throws IOException {
        // TODO Auto-generated method stub

        int len =0;
        while(true) {
            System.out.println("Input the bytes length of the random you want. Enter to exit:");
            String input = br.readLine();
            if ("".equals(input)) {
                return;
            }

            try {
                len = Integer.parseInt(input);
                break;
            }catch(Exception e) {
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
            
            System.out.println("No longer than 8 bytes, in number:\n----\n"+Math.abs(value)+"\n----");
        }else {
            System.out.println("Longer than 8 bytes, in hex:\n----\n"+ BytesTools.bytesToHexStringBE(bytes)+"\n----");
        }
        Menu.anyKeyToContinue(br);
    }

    private static void hex32Base58(BufferedReader br) throws IOException {
        String input=null;
        while (true) {
            System.out.println("Input 32 bytes hex or base58 string, enter to exit:");
            input = br.readLine();
            if ("".equals(input)) {
                return;
            }
            try {
                byte[] priKey = KeyTools.getPriKey32(input);
                if(priKey==null){
                    System.out.println("Only 64 chars hex or 52 chars base58 string can be accepted.");
                    continue;
                }
                String hex = Hex.toHex(priKey);
                System.out.println("----");
                System.out.println("- Private Key in Hex:\n"+ hex);
                System.out.println("- Private Key in Base58:\n"+ KeyTools.priKey32To38(hex));
                System.out.println("- Private Key in Base58 old style:\n"+KeyTools.priKey32To37(hex));
//
//                if(input.length()==64){
//                    System.out.println("Hex to Base58:"+"\n----");
//                    System.out.println("New: "+ KeyTools.priKey32To38(input));
//                    System.out.println("Old: "+KeyTools.priKey32To37(input)+"\n----");
//                }else if(input.length()==52){
//                    System.out.println("Base58 WIF compressed to Hex:"+"\n----");
//                    System.out.println(KeyTools.getPriKey32(input)+"\n----");
//                }else if(input.length()==51){
//                    System.out.println("Base58 WIF to Hex:"+"\n----");
//                    System.out.println(KeyTools.getPriKey32(input)+"\n----");
//                }else{
//                    System.out.println("Only 64 chars hex or 52 chars base58 string can be accepted.");
//                }
                String pubKey = Hex.toHex(KeyTools.priKeyToPubKey(priKey));
                printPubKeyAndAddresses(pubKey);
            }catch (Exception e){
                System.out.println("Only 64 chars hex or 52 chars base58 string can be accepted.");
                continue;
            }

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
            if(input.length()!=4){
                System.out.println("Input 4 characters you want them be in the end of your fid:");
                continue;
            }
            if(!Base58.isBase58Encoded(input)){
                System.out.println("It's not a Base58 encoded. The string can't contain '0', 'O', 'l', 'L', '+', '/'.");
                continue;
            }
            break;
        }
        long i =0;
        long j = 0;
        System.out.println("Finding...");
        while (true) {
            ECKey ecKey = new ECKey();
            String fid = KeyTools.pubKeyToFchAddr(ecKey.getPubKey());
            if(fid.substring(30).equals(input)){
                System.out.println("----");
                System.out.println("FID:"+fid);
                System.out.println("PubKey: "+ecKey.getPublicKeyAsHex());
                System.out.println("PriKeyHex: "+ecKey.getPrivateKeyAsHex());
                System.out.println("PriKeyBase58: "+ecKey.getPrivateKeyEncoded(MainNetParams.get()));
                System.out.println("----");
                System.out.println("Begin at: "+sdf.format(begin));
                Date end = new Date();
                System.out.println("End at: "+sdf.format(end));
                System.out.println("----");
                break;
            }
            i++;
            if(i%1000000==0) {
                j++;
                System.out.println(sdf.format(new Date())+": "+j+" million tryings.");
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

        printPubKeyAndAddresses(pubKey);

        Menu.anyKeyToContinue(br);
    }

    public static void printPubKeyAndAddresses(String pubKey) throws Exception {
        pubKey = KeyTools.getPubKey33(pubKey);
        KeyTools.showPubKeys(pubKey);
        Map<String, String> addrMap = KeyTools.pubKeyToAddresses(pubKey);

        System.out.println("----");

        System.out.println("FCH"+": "+ addrMap.get("fchAddr"));
        System.out.println("BTC"+": "+ addrMap.get("btcAddr"));
        System.out.println("ETH"+": "+ addrMap.get("ethAddr"));
        System.out.println("BCH"+": "+ addrMap.get("bchAddr"));
        System.out.println("DOGE"+": "+ addrMap.get("dogeAddr"));
        System.out.println("TRX"+": "+ addrMap.get("trxAddr"));
        System.out.println("LTC"+": "+ addrMap.get("ltcAddr"));


        System.out.println("----");
    }

    private static void sha256File(BufferedReader br) throws IOException {
        while(true) {
            System.out.println("Input the full path of the file to be hashed, enter to exit:");
            String filePath = br.readLine();
            if ("".equals(filePath)) {
                return;
            }
            // Create a File object with the specified path
            File file = new File(filePath);
            // Check if the file exists
            if(file.isDirectory()){
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
            System.out.println("sha256:" + hash );
            System.out.println("----");
            Menu.anyKeyToContinue(br);
        }
    }

    private static void sha256x2File(BufferedReader br) throws IOException {
        while(true) {
            System.out.println("Input the full path of the file to be hashed, enter to exit:");
            String filePath = br.readLine();
            if ("".equals(filePath)) {
                return;
            }
            // Create a File object with the specified path
            File file = new File(filePath);
            // Check if the file exists
            if(file.isDirectory()){
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
            System.out.println("file:" + filePath );
            System.out.println("sha256:" + hash);
            System.out.println("----");
            Menu.anyKeyToContinue(br);
        }
    }

    private static void sha256String(BufferedReader br)  {
        System.out.println("Input the string to be hashed:");
        String text = Inputer.inputStringMultiLine(br);
        String hash = Hash.sha256(text);
        System.out.println("----");
        System.out.println("raw string:");
        System.out.println("----");
        System.out.println(text);
        System.out.println("----");
        System.out.println("sha256:" +hash);
        System.out.println("----");
        Menu.anyKeyToContinue(br);
    }

    private static void sha256x2String(BufferedReader br)  {
        System.out.println("Input the string to be hashed:");
        String text = Inputer.inputStringMultiLine(br);
        String hash = Hash.sha256x2(text);
        System.out.println("----");
        System.out.println("raw string:");
        System.out.println("----");
        System.out.println(text);
        System.out.println("----");
        System.out.println("sha256:" +hash);
        System.out.println("----");
        Menu.anyKeyToContinue(br);
    }

    private static void symKeySign(BufferedReader br) {
        System.out.println("Input the symKey in hex, enter to exit:");
        String symKey;
        try {
            symKey = br.readLine();
            if("".equals(symKey)) {
                return;
            }

            while(true) {
                System.out.println("Input text to be signed, enter to input, 'q' to exit:");
                String text = Inputer.inputStringMultiLine(br);
                if("q".equals(text))return;
                String sign = getSign(symKey,text);
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
