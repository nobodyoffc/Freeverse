package appTools;

import apip.apipData.CidInfo;
import clients.*;
import constants.Constants;
import constants.Strings;
import crypto.*;
import crypto.Algorithm.Bitcore;
import fcData.AlgorithmId;
import fcData.FidTxMask;
import fcData.Signature;
import fch.*;
import fch.fchData.Address;
import fch.fchData.Cash;
import fch.fchData.P2SH;
import fch.fchData.SendTo;
import feip.feipData.Service;
import handlers.*;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.MainNetParams;
import org.jetbrains.annotations.Nullable;
import tools.BytesTools;
import tools.Hex;
import tools.JsonTools;
import tools.StringTools;
import tools.http.AuthType;
import tools.http.RequestMethod;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import static appTools.Inputer.askIfYes;
import static constants.Strings.newCashMapKey;
import static constants.Strings.spendCashMapKey;
import static crypto.Hash.getSign;
import static crypto.KeyTools.priKeyToFid;
import static crypto.KeyTools.priKeyToPubKey;
import static fcData.AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7;
import static fch.Inputer.inputFidArray;
import static fch.Inputer.inputGoodFid;
import static fch.TxCreator.DEFAULT_FEE_RATE;
import static tools.FileTools.getAvailableFile;

public class Home {
    private String myFid;
    private String myPubKey;
    private String myPriKeyCipher;
    private final Settings settings;
    private final ApipClient apipClient;
    private CashHandler cashHandler;
    private SecretHandler secretHandler;
    private ContactHandler contactHandler;
    private MailHandler mailHandler;
    private final BufferedReader br;
    
    // These could remain static if they're truly application-wide constants
    private static final String CLIENT_NAME = "FCH";

    public static final Object[] modules = new Object[]{
            Service.ServiceType.APIP,
            Handler.HandlerType.CASH,
            Handler.HandlerType.SECRET,
            Handler.HandlerType.CONTACT,
            Handler.HandlerType.MAIL
    };
    private static final Map<String,Object> SETTING_MAP = new HashMap<>();

    public Home(Settings settings,BufferedReader br) {
        this.settings = settings;
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.myFid = settings.getMainFid();
        this.myPubKey = settings.getMyPubKey();   
        this.myPriKeyCipher = settings.getMyPriKeyCipher();
        this.br = br;
    }


    public static void main(String[] args) throws Exception {
        Menu.welcome(CLIENT_NAME);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Settings settings = Starter.startClient(CLIENT_NAME, SETTING_MAP, br, modules);
        if(settings == null) return;

        byte[] symKey = settings.getSymKey();
        Home home = new Home(settings, br);
        home.menu(symKey);
    }

    public void menu(byte[] symKey) throws Exception {
        Menu menu = new Menu("Home");
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
                case 2 -> cashHandler.menu();
                case 3 -> handleTxMenu(symKey);
                case 4 -> signAndVerify(myPriKeyCipher, symKey, br);
                case 5 -> encryptAndDecrypt(myPriKeyCipher, symKey, br);
                case 6 -> keyAndAddress(myPubKey,myPriKeyCipher, symKey, br);
                case 7 -> hash(br);
                case 8 -> multiSign(myPriKeyCipher, symKey,apipClient,br);
                case 9 -> secretHandler.menu(br, false);
                case 10 -> contactHandler.menu(br, false);
                case 11 -> mailHandler.menu();
                case 12 -> switchAsWatchingFid(br);
                case 13 -> {
                    settings.setting(br,null);
                    symKey = settings.getSymKey();
                }
                case 0 -> {
                    return;
                }
            }
        }
    }

    private void handleTxMenu(byte[] symKey) throws Exception {
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
                case 3 -> signRawTx(myPriKeyCipher,symKey, br);
                case 4 -> broadcastTx(apipClient,br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private void listTx() {
        System.out.println("Requesting txSearch...");
        List<String> last=null;
        while(true){
            List<FidTxMask> result = apipClient.txByFid(myFid,20,last==null?null:last.toArray(new String[0]),RequestMethod.POST,AuthType.FC_SIGN_BODY);
            if(result==null)return;
            if(result.size()<20)break;
            last = apipClient.getFcClientEvent().getResponseBody().getLast();
            FidTxMask.showFidTxMaskList(result,"Your TXs",0);
            if(!askIfYes(br,"Continue?"))break;
        }
    }

    private void switchAsWatchingFid(BufferedReader br2) {
        System.out.println("Current FID: "+myFid);
        if(myPriKeyCipher == null) {
            System.out.println("It is a watching FID.");
        }
        if(!myFid.equals(settings.getMainFid())&& askIfYes(br2, "Switch to main FID?")) {
            myFid = settings.getMainFid();
            myPubKey = settings.getMyPubKey();
            myPriKeyCipher = settings.getMyPriKeyCipher();
            System.out.println("Acting as main FID: "+myFid);
            Menu.anyKeyToContinue(br);
            return;
        }
        String watchingFid;
        if(askIfYes(br2, "Switch to a watching FID?")) {
            if(settings.getWatchFidPubKeyMap()==null || settings.getWatchFidPubKeyMap().isEmpty()){
                if(askIfYes(br,"No watching FID yet. Add it?"))
                    watchingFid = settings.addWatchingFids(br, apipClient,CLIENT_NAME);
                else return;
            }else{
                watchingFid = Inputer.chooseOneKeyFromMap(settings.getWatchFidPubKeyMap(), false, null, "Choose one to act as:", br2);
            }
            if(watchingFid == null) {
                if(askIfYes(br,"Add new watching FID?"))
                    watchingFid = settings.addWatchingFids(br, apipClient, CLIENT_NAME);
                else return;
            }
            myFid = watchingFid;
            myPubKey = settings.getWatchFidPubKeyMap().get(watchingFid);
            if(!myFid.equals(settings.getMainFid()))myPriKeyCipher=null;
            System.out.println("Acting as watching FID: "+myFid);
            Menu.anyKeyToContinue(br);
        }
    }

    public static void decryptWithPasswordFromBundle(BufferedReader br) {
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

    public static void encryptWithPasswordBundle(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        String msg = Inputer.inputMsg(br);
        String ask = "Input the password:";
        char[] password = Inputer.inputPassword(br, ask);
        if(password==null)return;
        assert msg != null;
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] bundle = encryptor.encryptToBundleByPassword(msgBytes, password);
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptAsyTwoWayFromBundle(byte[] priKey, BufferedReader br) {
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

            priKey = checkPriKey(priKey, br);
            if(priKey ==null) return;
            byte[] bundleBytes = Base64.getDecoder().decode(bundle);
            System.out.println(decryptor.decryptBundleByAsyTwoWay(bundleBytes,priKey, Hex.fromHex(pubKey)));
        } catch (IOException e) {
            System.out.println("Something wrong:"+e.getMessage());
        }
        Menu.anyKeyToContinue(br);
    }

    public static void decryptAsyOneWayFromBundle(byte[] priKey, BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        try {
            System.out.println("Input the bundle in Base64:");
            String bundle = br.readLine();
            if(bundle==null){
                System.out.println("Bundle is null.");
                return;
            }
            priKey = checkPriKey(priKey, br);
            if(priKey ==null) return;

            System.out.println(decryptor.decryptBundleByAsyOneWay(Base64.getDecoder().decode(bundle),priKey));

        } catch (IOException e) {
            System.out.println("Something wrong:"+e.getMessage());
        }

        Menu.anyKeyToContinue(br);
    }

    public static void encryptAsyTwoWayBundle(BufferedReader br) {
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
                priKeyA = Inputer.inputString(br);
                if (priKeyA == null) {
                    System.out.println("A private key is required.");
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

    @SuppressWarnings("unused")
    public static CryptoDataByte getEncryptedEccAesDataTwoWay(BufferedReader br) {
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

    public static void encryptAsyOneWayBundle(BufferedReader br) {
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

    public static void decryptFileSymKey(BufferedReader br) {
        File encryptedFile = getAvailableFile(br);
        Decryptor decryptor = new Decryptor();
        if(encryptedFile==null||encryptedFile.length()> Constants.MAX_FILE_SIZE_M * Constants.M_BYTES)return;
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        if(symKey==null)return;
        CryptoDataByte result = decryptor.decryptFileBySymKey(encryptedFile.getName(), "decrypted" + encryptedFile.getName(), BytesTools.hexCharArrayToByteArray(symKey));
        System.out.println(new String(result.getData()));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptFileWithSymKey(BufferedReader br) {
        File originalFile = getAvailableFile(br);
        if(originalFile==null||originalFile.length()> Constants.MAX_FILE_SIZE_M * Constants.M_BYTES)return;

        String ask = "Input the symKey in hex:";
        char[] symKeyHex = Inputer.input32BytesKey(br, ask);
        if(symKeyHex==null) return;
        byte[] symKey = BytesTools.hexCharArrayToByteArray(symKeyHex);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        String destFileName = Encryptor.makeEncryptedFileName(originalFile.getName());
        System.out.println(encryptor.encryptFileBySymKey(originalFile.getName(),destFileName,symKey));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptWithSymKeyFromBundle(BufferedReader br) {
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

    public static void encryptWithSymKeyToBundle(BufferedReader br) {
        String msg = Inputer.inputMsg(br);
        if(msg==null)return;
        String ask = "Input the symKey in hex:";
        char[] symKeyHex = Inputer.input32BytesKey(br, ask);
        if(symKeyHex==null) return;
        byte[] symKey = BytesTools.hexCharArrayToByteArray(symKeyHex);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        byte[] bundle = encryptor.encryptToBundleBySymKey(msg.getBytes(),symKey);
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptFileAsy(byte[] priKey, BufferedReader br) {
        File encryptedFile = getAvailableFile(br);
        if(encryptedFile==null||encryptedFile.length()> Constants.MAX_FILE_SIZE_M * Constants.M_BYTES)return;

        priKey = checkPriKey(priKey, br);
        if(priKey ==null) return;
        System.out.println("Input the recipient private key in hex:");
        Decryptor decryptor = new Decryptor();
        String plainFileName = Decryptor.recoverEncryptedFileName(encryptedFile.getName());
        CryptoDataByte cryptoDataByte = decryptor.decryptFileByAsyOneWay(encryptedFile.getName(),plainFileName,priKey);
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    public static void encryptFileAsy(BufferedReader br) {

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

    public static String getPubKey(BufferedReader br) {
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

    public static void gainTimestamp() {
        long timestamp = System.currentTimeMillis();
        System.out.println(timestamp);
    }

    public static void encryptWithSymKeyToJson(BufferedReader br)  {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        String msg = Inputer.inputMsg(br);
        if(msg==null)return;
        String ask = "Input the symKey in hex:";
        char[] symKey = Inputer.input32BytesKey(br, ask);
        if(symKey==null)return;
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(msg.getBytes(),BytesTools.hexCharArrayToByteArray(symKey));
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    public static void decryptWithSymKeyFromJson(BufferedReader br) throws Exception {

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

    public static void encryptWithPasswordToJson(BufferedReader br) throws IOException{

        String ask = "Input the password no longer than 64:";
        char[] password = Inputer.inputPassword(br, ask);
        if(password==null)return;
        System.out.println("Password:"+ Arrays.toString(password));

        System.out.println("Input the plaintext:");
        String msg = br.readLine();
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptByPassword(msg.getBytes(),password);
        System.out.println(cryptoDataByte.toNiceJson());

        Menu.anyKeyToContinue(br);
    }

    public static void decryptWithPasswordFromJson(BufferedReader br) throws Exception {
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

    public static void decryptAsyFromJson(byte[] priKey, BufferedReader br) throws Exception {
        priKey = checkPriKey(priKey, br);
        if(priKey ==null) return;
        System.out.println("Input the json string of EccAesData:");
        String eccAesDataJson = br.readLine();

        decryptAsyJson(priKey, br, eccAesDataJson);

        Menu.anyKeyToContinue(br);

    }

    public static void decryptAsyJson(byte[] priKey, BufferedReader br, String eccAesDataJson)  {
        priKey = checkPriKey(priKey, br);
        if (priKey == null) return;
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonByAsyOneWay(eccAesDataJson,priKey);
        System.out.println(new String(cryptoDataByte.getData()));
    }

    @Nullable
    public static byte[] checkPriKey(byte[] priKey, BufferedReader br) {
        if(priKey ==null || Inputer.askIfYes(br,"Input a new private key?")){
            System.out.println("Input the recipient private key in hex:");
            priKey = fch.Inputer.inputPriKeyHexOrBase58(br);
        }
        return priKey;
    }

    public static void decryptWithBtcEcc(byte[] priKey, BufferedReader br) {

        String cipher = Inputer.inputString(br,"Input the cipher:");
        Decryptor decryptor = new Decryptor();
        priKey = checkPriKey(priKey, br);
        if(priKey ==null) return;
        CryptoDataByte cryptoDataByte = decryptor.decrypt(cipher,priKey);
        if(cryptoDataByte.getCode()!=0){
            System.out.println(cryptoDataByte.getMessage());
        }else System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptAsyToJson(BufferedReader br)  {
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

    public static CryptoDataByte getEncryptedEccAesDataOneWay(BufferedReader br) {
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

    public static void encryptWithBtcEcc(BufferedReader br) {
        String msg = Inputer.inputString(br,"Input the plaintext:");
        if(msg==null)return;
        String pubKey = getPubKey(br);
        if(pubKey==null)return;

        Encryptor encryptor = new Encryptor(AlgorithmId.BitCore_EccAes256);
        CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(msg.getBytes(),Hex.fromHex(pubKey));
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    private static void printRandomInMultipleFormats(byte[] bytes) {
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

    public static void getRandom(BufferedReader br) throws IOException {
        int len;
        while(true) {
            System.out.println("Input the bytes length of the random you want. Enter to exit:");
            String input = br.readLine();
            if ("".equals(input)) {
                return;
            }

            try {
                len = Integer.parseInt(input);
                break;
            }catch(Exception ignored) {
            }
        }

        byte[] bytes = BytesTools.getRandomBytes(len);
        printRandomInMultipleFormats(bytes);
        Menu.anyKeyToContinue(br);
    }

    public static void hex32Base58(BufferedReader br) throws IOException {
        String input;
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
                String pubKey = Hex.toHex(KeyTools.priKeyToPubKey(priKey));
                printPubKeyAndAddresses(pubKey);
            }catch (Exception e){
                System.out.println("Only 64 chars hex or 52 chars base58 string can be accepted.");
            }
        }
    }

    public static void findNiceFid(BufferedReader br) throws IOException {
        String input;
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

    public static void pubKeyToAddrs(BufferedReader br) throws Exception {
        System.out.println("Input the public key, enter to exit:");
        String pubKey;
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
        if(addrMap==null){
            System.out.println("Failed to make addresses from the public key.");
            return;
        }

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

    public static void sha256File(BufferedReader br) throws IOException {
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
            byte[] hash = Hash.sha256Stream(file);
            if(hash==null) {
                System.out.println("Error calculating SHA-256 hash.");
                break;
            }
            System.out.println("----");
            System.out.println("file:" + filePath);
            System.out.println("sha256:" + Hex.toHex(hash) );
            System.out.println("----");
            Menu.anyKeyToContinue(br);
        }
    }

    public static void sha256x2File(BufferedReader br) throws IOException {
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
            if(hash==null) {
                System.out.println("Error calculating SHA-256 hash.");
                break;
            }
            System.out.println("----");
            System.out.println("file:" + filePath );
            System.out.println("sha256x2:" + hash);
            System.out.println("----");
            Menu.anyKeyToContinue(br);
        }
    }

    public static void sha256String(BufferedReader br)  {
        String text = Inputer.inputStringMultiLine(br);
        if (text==null)return;
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

    public static void sha256x2String(BufferedReader br)  {
        String text = Inputer.inputStringMultiLine(br);
        if(text==null)return;
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

    public static void symKeySign(BufferedReader br) {
        System.out.println("Input the symKey in hex, enter to exit:");
        String symKey;
        try {
            symKey = br.readLine();
            if("".equals(symKey)) {
                return;
            }

            while(true) {
                String text = Inputer.inputStringMultiLine(br);
                if(text==null)return;
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

    public static void encryptBitcoreToBundle(BufferedReader br2) {
        String msg = Inputer.inputString(br2,"Input the message to be encrypted:");
        String pubKey = Inputer.inputString(br2,"Input the public key in hex:");
        byte[] encrypted = Bitcore.encrypt(msg.getBytes(), Hex.fromHex(pubKey));
        if(encrypted==null){
            System.out.println("Encrypt failed.");
            return;
        }
        System.out.println("Cipher: \n"+Base64.getEncoder().encodeToString(encrypted));

    }

    public static void decryptBitcoreFromBundle(byte[] priKey, BufferedReader br) {
        String cipher = Inputer.inputString(br,"Input the cipher:");
        priKey = checkPriKey(priKey, br);
        if(priKey ==null) return;
        byte[] decrypted;
        try {
            decrypted = Bitcore.decrypt(Base64.getDecoder().decode(cipher), priKey);
            if(decrypted==null){
                System.out.println("Decrypt failed.");
                return;
            }
            System.out.println("Decrypted: \n"+new String(decrypted));
        } catch (Exception e) {
            System.out.println("Decrypt failed.");
            e.printStackTrace();
        }
    }

    public static void broadcastTx(ApipClient apipClient, BufferedReader br) {
        String rawTx = Inputer.inputString(br, "Input the raw tx hex or Base64:");
        String result = TxCreator.decodeTxFch(rawTx);
        System.out.println("Check your Tx:\n" + result);
        Menu.anyKeyToContinue(br);
        if(askIfYes(br, "Something wrong? y/other to broadcast")) return;
        result = apipClient.broadcastTx(rawTx, RequestMethod.GET, AuthType.FREE);
        System.out.println(result);
        Menu.anyKeyToContinue(br);
    }

    public static void decodeTxFch(BufferedReader br2) {
        String rawTx = Inputer.inputString(br2, "Input the raw tx hex or Base64:");
        String result = TxCreator.decodeTxFch(rawTx);
        System.out.println(result);
        Menu.anyKeyToContinue(br2);
    }

    public static void signRawTx(String userPriKeyCipher, byte[] symKey, BufferedReader br2) {
        String rawTx = Inputer.inputString(br2, "Input the raw tx hex or Base64:");
        String decodeJson = Wallet.decodeTxFch(rawTx);
        if(decodeJson==null){
            System.out.println("Failed to decoded the TX.");
            return;
        }
        System.out.println("Decoded:");
        System.out.println(decodeJson);
        Menu.anyKeyToContinue(br2);
        byte[] priKey = Client.decryptPriKey(userPriKeyCipher,symKey);
        String result = Wallet.signRawTx(rawTx, priKey);
        if(result==null)return;
        System.out.println("Signed by "+KeyTools.priKeyToFid(priKey)+".");
        System.out.println("Base64:\n\t"+result);
        System.out.println("Hex:\n\t"+ StringTools.base64ToHex(result));
        Menu.anyKeyToContinue(br2);
    }

    public static void multiSign(String userPriKeyCipher, byte[] symKey, ApipClient apipClient, BufferedReader br) {
           Menu menu = new Menu();
           menu.add("Create multisig FID")
                   .add("Show multisig FID")
                   .add("Creat a multisig raw TX")
                   .add("Sign a multisig raw TX")
                   .add("Build the final multisig TX")
           ;
           while (true) {
               System.out.println(" << Multi Signature>>");

               menu.show();
               int choice = menu.choose(br);
               switch (choice) {
                   case 1 -> createFid(apipClient,br);
                   case 2 -> showFid(apipClient,br);
                   case 3 -> createTx(apipClient,br);
                   case 4 -> signTx(userPriKeyCipher,symKey,br);
                   case 5 -> buildSignedTx(br);
                   case 0 -> {
                       return;
                   }
               }
           }
       }

    public static void buildSignedTx(BufferedReader br) {
           String[] signedData = Inputer.inputStringArray(br, "Input the signed data. Enter to end:", 0);

           String signedTx = TxCreator.buildSignedTx(signedData);
           if (signedTx == null) return;
           System.out.println(signedTx);
           Menu.anyKeyToContinue(br);
       }

    public static void signTx(String userPriKeyCipher, byte[] symKey, BufferedReader br) {

           byte[] priKey;
           while (true) {
               System.out.println("Sign with Apip Buyer? y/n:");
               String input = Inputer.inputString(br);
               if ("y".equals(input)) {
                   Decryptor decryptor = new Decryptor();
                   CryptoDataByte cryptoDataByte = decryptor.decrypt(userPriKeyCipher,symKey);
                   if(cryptoDataByte==null || cryptoDataByte.getCode()!=0){
                       System.out.println("Failed to decrypt priKey.");
                       return;
                   }
                   priKey = cryptoDataByte.getData();
               }else {
                   try {
                       priKey = KeyTools.inputCipherGetPriKey(br);
                   } catch (Exception e) {
                       System.out.println("Wrong input. Try again.");
                       continue;
                   }
               }

               if (priKey == null) {
                   System.out.println("Get priKey wrong");
                   return;
               }

               System.out.println("Set the unsigned data json string. ");
               String multiSignDataJson = Inputer.inputStringMultiLine(br);

               showRawTxInfo(multiSignDataJson, br);

               System.out.println("Multisig data signed by " + priKeyToFid(priKey) + ":");
               Shower.printUnderline(60);
               System.out.println(TxCreator.signSchnorrMultiSignTx(multiSignDataJson, priKey));
               BytesTools.clearByteArray(priKey);
               Shower.printUnderline(60);
               Menu.anyKeyToContinue(br);

               input = Inputer.inputString(br, "Sign with another priKey?y/n");
               if (!"y".equals(input)) {
                   BytesTools.clearByteArray(priKey);
                   return;
               }
           }
       }

    public static void showRawTxInfo(String multiSignDataJson, BufferedReader br) {
           MultiSigData multiSigData = MultiSigData.fromJson(multiSignDataJson);

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
           for (Cash cash : multiSigData.getCashList()) spendCashMap.put(cash.getCashId(), cash);

           System.out.println("You are spending:");
           Shower.printUnderline(60);
           System.out.print(Shower.formatString("cashId", 68));
           System.out.print(Shower.formatString("owner", 38));
           System.out.println(Shower.formatString("fch", 20));
           Shower.printUnderline(60);
           for (Cash cash : spendCashList) {
               Cash niceCash = spendCashMap.get(cash.getCashId());
               if (niceCash == null) {
                   System.out.println("Warning： The cash " + cash.getCashId() + "in the rawTx is unfounded.");
                   return;
               }
               System.out.print(Shower.formatString(niceCash.getCashId(), 68));
               System.out.print(Shower.formatString(niceCash.getOwner(), 38));
               System.out.println(Shower.formatString(String.valueOf(ParseTools.satoshiToCoin(niceCash.getValue())), 20));
           }
           Shower.printUnderline(60);
           Menu.anyKeyToContinue(br);
           System.out.println("You are paying:");
           Shower.printUnderline(60);
           System.out.print(Shower.formatString("FID", 38));
           System.out.println(Shower.formatString("fch", 20));
           Shower.printUnderline(60);
           for (Cash cash : issuredCashList) {
               System.out.print(Shower.formatString(cash.getOwner(), 38));
               System.out.println(Shower.formatString(String.valueOf(ParseTools.satoshiToCoin(cash.getValue())), 20));
           }
           Shower.printUnderline(60);
           Shower.printUnderline(60);
           Menu.anyKeyToContinue(br);
           Shower.printUnderline(60);

           if (msg != null) {
               System.out.println("The message in OP_RETURN is: ");
               Shower.printUnderline(60);
               System.out.println(msg);
               Shower.printUnderline(60);
               Menu.anyKeyToContinue(br);
           }
       }

    public static void createTx(ApipClient apipClient, BufferedReader br) {

           String fid = inputGoodFid(br, "Input the multisig fid:");

           Map<String, P2SH> p2shMap = apipClient.p2shByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, fid);
           P2SH p2sh = p2shMap.get(fid);
           if (p2sh == null) {
               System.out.println(fid + " is not found.");
               return;
           }

           System.out.println(JsonTools.toNiceJson(p2sh));

           List<SendTo> sendToList = SendTo.inputSendToList(br);
           String msg = Inputer.inputString(br, "Input the message for OpReturn. Enter to ignore:");
           int msgLen;
           byte[] msgBytes = null;
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

           List<Cash> cashList = apipClient.cashValid(fid, sum, null, sendToList.size(),msgLen, RequestMethod.POST, AuthType.FC_SIGN_BODY);
           byte[] rawTx = TxCreator.createUnsignedTxFch(cashList, sendToList, msgBytes, p2sh, DEFAULT_FEE_RATE);

           MultiSigData multiSignData = new MultiSigData(rawTx, p2sh, cashList);

           System.out.println("Multisig data unsigned:");
           Shower.printUnderline(10);
           System.out.println(multiSignData.toJson());
           Shower.printUnderline(10);

           System.out.println("Next step: sign it separately with the priKeys of: ");
           Shower.printUnderline(10);
           for (String fid1 : p2sh.getFids()) System.out.println(fid1);
           Shower.printUnderline(10);
           Menu.anyKeyToContinue(br);
       }

    public static void createFid(ApipClient apipClient, BufferedReader br) {
           String[] fids = inputFidArray(br, "Input FIDs. Enter to end:", 0);
           if (fids.length > 16) {
               System.out.println("The FIDs can not be more than 16.");
               return;
           }
           int m = Inputer.inputInt(br, "How many signatures is required? ", 16);

           Map<String, Address> fidMap = apipClient.fidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, fids);
           if(fidMap==null) return;

           List<byte[]> pubKeyList = new ArrayList<>();
           for (String fid : fids) {
               String pubKey = fidMap.get(fid).getPubKey();
               pubKeyList.add(HexFormat.of().parseHex(pubKey));
           }

           P2SH p2SH = TxCreator.genMultiP2sh(pubKeyList, m);

        //    String mFid = p2SH.getFid();

           Shower.printUnderline(10);
           System.out.println("The multisig information is: \n" + JsonTools.toNiceJson(p2SH));
           System.out.println("It's generated from :");
           for (String fid : fids) {
               System.out.println(fid);
           }
           Shower.printUnderline(10);
           if(p2SH==null)return;
        String fid = p2SH.getFid();
        System.out.println("Your multisig FID: \n" + fid);
           Shower.printUnderline(10);
           Menu.anyKeyToContinue(br);
       }

    public static void showFid(ApipClient apipClient, BufferedReader br) {
           String fid = inputGoodFid(br, "Input the multisig FID:");
           if(fid==null)return;
           if (!fid.startsWith("3")) {
               System.out.println("A multisig FID should start with '3'.");
               return;
           }
           System.out.println("Requesting APIP from " + apipClient.getUrlHead());
           Map<String, P2SH> p2shMap = apipClient.p2shByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, fid);
           P2SH p2sh = p2shMap.get(fid);

           if (p2sh == null) {
               System.out.println(fid + " is not found.");
               return;
           }
           Shower.printUnderline(10);
           System.out.println("Multisig:");
           System.out.println(JsonTools.toNiceJson(p2sh));
           Shower.printUnderline(10);
           System.out.println("The members:");

           Map<String, CidInfo> cidInfoMap = apipClient.cidInfoByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, p2sh.getFids());
           if (cidInfoMap == null) {
               return;
           }
           System.out.println(JsonTools.toNiceJson(cidInfoMap));
           Shower.printUnderline(10);
           Menu.anyKeyToContinue(br);
       }

    public static void pubKeyConvert(String pubKey, BufferedReader br) {
            String pubKey33 = null;
            while (true) {
                String input = Inputer.inputString(br,"Input the new public key. Enter to use the local public key:");
                if ("".equals(input)) {
                    pubKey33 = pubKey;
                } else {
                    try{
                        pubKey33 = KeyTools.getPubKey33(input);
                    }catch(Exception e){
                        System.out.println("Wrong pubKey.");
                        if(!askIfYes(br,"Try again?")) return;
                    }
                }
                if(pubKey33==null){
                    System.out.println("Wrong pubKey.");
                    if(!askIfYes(br,"Try again?")) return;
                } else break;
            }
            Shower.printUnderline(10);
            System.out.println("FID: \n" + KeyTools.pubKeyToFchAddr(pubKey33));
            Shower.printUnderline(10);
            System.out.println("* PubKey 33 bytes compressed hex:\n" + pubKey33);
            Shower.printUnderline(10);
            System.out.println("* PubKey 65 bytes uncompressed hex:\n" + KeyTools.recoverPK33ToPK65(pubKey33));
            Shower.printUnderline(10);
            System.out.println("* PubKey WIF uncompressed:\n" + KeyTools.getPubKeyWifUncompressed(pubKey33));
            Shower.printUnderline(10);
            System.out.println("* PubKey WIF compressed with ver 0:\n" + KeyTools.getPubKeyWifCompressedWithVer0(pubKey33));
            Shower.printUnderline(10);
            System.out.println("* PubKey WIF compressed without ver:\n" + KeyTools.getPubKeyWifCompressedWithoutVer(pubKey33));
            Shower.printUnderline(10);
            Menu.anyKeyToContinue(br);
       }

    public static void addressConvert(String myPriKeyCipher, byte[] symKey, BufferedReader br) throws Exception {
        Map<String, String> addrMap = null;
        String id=null;
        String pubKey=null;

        while (true) {
            System.out.println("Enter to convert the local FID, or input the address or pubKey:");
            String input = Inputer.inputString(br);
            if ("".equals(input)) {
                byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
                pubKey = Hex.toHex(KeyTools.priKeyToPubKey(priKey));
                addrMap = KeyTools.pubKeyToAddresses(pubKey);
                break;
            }else if(input.length()<66) {
                id = input;
                try{
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
                        continue;
                    }
                }catch(Exception e){
                    System.out.println("Wrong Address. Try again.");
                    continue;
                }
                break;
            }else if(KeyTools.isValidPubKey(input)) {
                pubKey = KeyTools.getPubKey33(input);
                addrMap = KeyTools.pubKeyToAddresses(pubKey);
                break;
            }
            System.out.println("Wrong FID. Try again.");
        }
        if(addrMap==null) return;
        Shower.printUnderline(10);
        System.out.println(JsonTools.toNiceJson(addrMap));
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
       }

    public static void priKeyConvert(String userPriKeyCipher, byte[] symKey, BufferedReader br) {
           byte[] priKey;
           System.out.println("By a new FID? 'y' to confirm, others to use the local priKey:");
           String input = Inputer.inputString(br);
           if ("y".equals(input)) {
               priKey = KeyTools.inputCipherGetPriKey(br);
               if (priKey== null) return;
           } else priKey = Client.decryptPriKey(userPriKeyCipher, symKey);

           if (priKey == null) return;
           String fid = priKeyToFid(priKey);
           Shower.printUnderline(10);
           System.out.println("* FID:" + fid);
           System.out.println("* PubKey:" + HexFormat.of().formatHex(priKeyToPubKey(priKey)));

           System.out.println("* PriKey 32 bytes:");
           String priKey32 = HexFormat.of().formatHex(priKey);
           System.out.println(priKey32);
           System.out.println("* PriKey WIF:");
           System.out.println(KeyTools.priKey32To37(priKey32));
           System.out.println("* PriKey WIF compressed:");
           System.out.println(KeyTools.priKey32To38(priKey32));
           Shower.printUnderline(10);
           Menu.anyKeyToContinue(br);
       }

    public static void signMsgEcdsa(String userPriKeyCipher, byte[] symKey, BufferedReader br) {
           byte[] priKey;
           System.out.println("By a new FID? 'y' to confirm, others to use the local priKey:");
           String input = Inputer.inputString(br);
           if ("y".equals(input)) {
               priKey = KeyTools.inputCipherGetPriKey(br);
               if (priKey== null) return;
           } else priKey = Client.decryptPriKey(userPriKeyCipher, symKey);

           String signer = priKeyToFid(priKey);
           System.out.println("The signer is :" + signer);

           System.out.println("Input the message to be sign. Enter to exit:");
           String msg = Inputer.inputString(br);
           if ("".equals(msg)) return;

           Signature signature = new Signature();
           signature.sign(signer, priKey, BTC_EcdsaSignMsg_No1_NrC7);
           System.out.println("Signature:");
           Shower.printUnderline(10);
           System.out.println(signature.toNiceJson());
           Shower.printUnderline(10);
           Menu.anyKeyToContinue(br);
       }

    public static void signMsgSchnorr(String userPriKeyCipher, byte[] symKey, BufferedReader br) {
           byte[] priKey;
           System.out.println("By a new FID? 'y' to confirm, others to use the local priKey:");
           String input = Inputer.inputString(br);
           if ("y".equals(input)) {
               priKey = KeyTools.inputCipherGetPriKey(br);
               if (priKey== null) return;
           } else priKey = Client.decryptPriKey(userPriKeyCipher, symKey);

           String signer = priKeyToFid(priKey);
           System.out.println("The signer is :" + signer);

           System.out.println("Input the message to be sign. Enter to exit:");
           String msg = Inputer.inputString(br);
           if ("".equals(msg)) return;

           Signature signature = new Signature();
           signature.sign(msg, priKey, AlgorithmId.FC_SchnorrSignMsg_No1_NrC7);
           System.out.println("Signature:");
           Shower.printUnderline(10);
           System.out.println(signature.toNiceJson());
           Shower.printUnderline(10);
           Menu.anyKeyToContinue(br);
       }

    public static void verify(BufferedReader br) {
           System.out.println("Set signature.");
           String input = Inputer.inputStringMultiLine(br);
           if (input == null) return;

           Signature signature = null;

           try {
               signature = Signature.parseSignature(input);
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

    public static void keyAndAddress(String pubKey, String myPriKeyCipher, byte[] symKey, BufferedReader br) throws Exception {
           Menu menu = new Menu("Key & Address");
           menu
           .add("Generate Random")
           .add("Find Nice FID")
           .add("PriKey Convert")
           .add("PubKey Convert")
           .add("Address Convert");

           while (true) {
               System.out.println(" << Key & Address >>");
               menu.show();
               int choice = menu.choose(br);
               switch (choice) {
                   case 1 -> getRandom(br);
                   case 2 -> findNiceFid(br);
                   case 3 -> priKeyConvert(myPriKeyCipher,symKey, br);
                   case 4 -> pubKeyConvert(pubKey, br);
                   case 5 -> addressConvert(myPriKeyCipher,symKey, br);
                   case 0 -> {
                       return;
                   }
               }
           }
       }

    public static void hash(BufferedReader br) throws IOException {
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
                       String hash = Hash.sha256(text);
                       System.out.println("raw string: "+text);
                       System.out.println("sha256:");
                       System.out.println("----");
                       System.out.println(hash);
                       System.out.println("----");
                       Menu.anyKeyToContinue(br);
                   }
                   case 2 -> {
                       while(true) {
                           System.out.println("Input the full path of the file to be hashed, enter to exit:");
                           String filePath = Inputer.inputString(br);
                           if ("".equals(filePath)) break;

                           File file = new File(filePath);
                           if(file.isDirectory()){
                               System.out.println("It's a directory.");
                               break;
                           }
                           if (!file.exists()) {
                               System.out.println("File does not exist.");
                               break;
                           }
                           byte[] hash = Hash.sha256Stream(file);
                           if(hash==null) {
                               System.out.println("Error calculating SHA-256 hash.");
                               break;
                           }
                           System.out.println("file:" + filePath);
                           System.out.println("sha256:");
                           System.out.println("----");
                           System.out.println(Hex.toHex(hash));
                           System.out.println("----");
                           Menu.anyKeyToContinue(br);
                       }
                   }
                   case 3 -> {
                       String text = Inputer.inputStringMultiLine(br);
                       if(text==null)break;
                       String hash = Hash.sha256x2(text);
                       System.out.println("----");
                       System.out.println("raw string: "+text);
                       System.out.println("sha256x2:");
                       System.out.println("----");
                       System.out.println(hash);
                       System.out.println("----");
                       Menu.anyKeyToContinue(br);
                   }
                   case 4 -> {
                       while(true) {
                           System.out.println("Input the full path of the file to be hashed, enter to exit:");
                           String filePath = Inputer.inputString(br);
                           if ("".equals(filePath)) break;

                           File file = new File(filePath);
                           if(file.isDirectory()){
                               System.out.println("It's a directory.");
                               break;
                           }
                           if (!file.exists()) {
                               System.out.println("File does not exist.");
                               break;
                           }
                           String hash = Hash.sha256x2(file);
                           if(hash==null) {
                               System.out.println("Error calculating SHA-256x2 hash.");
                               break;
                           }
                           System.out.println("file:" + filePath);
                           System.out.println("sha256x2:");
                           System.out.println("----");
                           System.out.println(hash);
                           System.out.println("----");
                           Menu.anyKeyToContinue(br);
                       }
                   }
                   case 5 -> {
                       String text = Inputer.inputStringMultiLine(br);
                       byte[] hashBytes = Hash.sha512(text.getBytes());
                       System.out.println("----");
                       System.out.println("raw string: "+text);
                       System.out.println("sha512:");
                       System.out.println("----");
                       System.out.println(Hex.toHex(hashBytes));
                       System.out.println("----");
                       Menu.anyKeyToContinue(br);
                   }
                   case 6 -> {
                       String text = Inputer.inputStringMultiLine(br);
                       String hash = Hash.sha512x2(text);
                       System.out.println("----");
                       System.out.println("raw string: "+text);
                       System.out.println("sha512x2:" + hash);
                       System.out.println("----");
                       Menu.anyKeyToContinue(br);
                   }
                   case 7 -> {
                       String text = Inputer.inputStringMultiLine(br);
                       if(text==null)break;
                       byte[] hashBytes = Hash.Ripemd160(text.getBytes());
                       System.out.println("----");
                       System.out.println("raw string: "+text);
                       System.out.println("ripemd160:");
                       System.out.println("----");
                       System.out.println(Hex.toHex(hashBytes));
                       System.out.println("----");
                       Menu.anyKeyToContinue(br);
                   }
                   case 8 -> {
                       String text = Inputer.inputStringMultiLine(br);
                       String hash = Hash.sha3String(text);
                       System.out.println("raw string: "+text);
                       System.out.println("sha3:" );
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

    public static void signAndVerify(String userPriKeyCipher, byte[] symKey, BufferedReader br) {
           Menu menu = new Menu();
           menu.add("Sign Message Ecdsa")
               .add("Sign Message Schnorr")
               .add("Sign with SymKey")
               .add("Verify Signature");

           while (true) {
               System.out.println(" << Sign & Verify >>");
               menu.show();
               int choice = menu.choose(br);
               switch (choice) {
                   case 1 -> signMsgEcdsa(userPriKeyCipher, symKey, br);
                   case 2 -> signMsgSchnorr(userPriKeyCipher,symKey, br);
                   case 3 -> signWithSymKey(br);
                   case 4 -> verify(br);
                   case 0 -> {
                       return;
                   }
               }
           }
       }

    public static void signWithSymKey(BufferedReader br) {
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

    public static void encryptAndDecrypt(String userPriKeyCipher, byte[] symKey, BufferedReader br) {
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
            byte[] priKey = Client.decryptPriKey(userPriKeyCipher, symKey);
           while (true) {
               System.out.println(" << Encrypt & Decrypt >>");
               menu.show();
               int choice = menu.choose(br);
               try {
                   switch (choice) {
                       case 1 -> encryptWithSymKeyToJson(br);
                       case 2 -> encryptWithSymKeyToBundle(br);
                       case 3 -> encryptWithPasswordToJson(br);
                       case 4 -> encryptWithPasswordBundle(br);
                       case 5 -> encryptAsyToJson(br);
                       case 6 -> encryptAsyOneWayBundle(br);
                       case 7 -> encryptAsyTwoWayBundle(br);
                       case 8 -> encryptFileWithSymKey(br);
                       case 9 -> encryptFileAsy(br);
                       case 10 -> encryptBitcoreToBundle(br);
                       case 11 -> decryptWithSymKeyFromJson(br);
                       case 12 -> decryptWithSymKeyFromBundle(br);
                       case 13 -> decryptWithPasswordFromJson(br);
                       case 14 -> decryptWithPasswordFromBundle(br);
                       case 15 -> decryptAsyFromJson(priKey, br);
                       case 16 -> decryptAsyOneWayFromBundle(priKey, br);
                       case 17 -> decryptAsyTwoWayFromBundle(priKey, br);
                       case 18 -> decryptFileSymKey(br);
                       case 19 -> decryptFileAsy(priKey, br);
                       case 20 -> decryptBitcoreFromBundle(priKey,br);
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

//    private void initializeClients(byte[] symKey) {
//        this.cashHandler = new CashHandler(settings);
//        this.secretHandler = new SecretHandler(settings);
//        this.contactHandler = new ContactHandler(settings);
//        this.mailHandler = new MailHandler(settings);
//    }
}
