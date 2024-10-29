package fch;

import apip.apipData.CidInfo;
import configure.ServiceType;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import fch.fchData.Address;
import fch.fchData.Cash;
import fch.fchData.P2SH;
import fch.fchData.SendTo;
import appTools.Starter;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import clients.apipClient.*;
import configure.ApiAccount;
import configure.Configure;
import constants.Constants;
import constants.Strings;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.Signature;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;
import appTools.Settings;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

import static appTools.Inputer.askIfYes;
import static constants.Strings.*;
import static fch.Inputer.inputFidArray;
import static fch.Inputer.inputGoodFid;
import static fch.TxCreator.DEFAULT_FEE_RATE;
import static constants.Constants.COIN_TO_SATOSHI;
import static crypto.KeyTools.priKeyToFid;
import static crypto.KeyTools.priKeyToPubKey;
import static fcData.AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7;


public class startFchClient {

    public static Configure configure;
    public static Settings settings;
    public static ApiAccount apipAccount;
    public static ApipClient apipClient;
    public static BufferedReader br ;
    public static String clientName= "FCH";

    public static String[] serviceAliases = new String[]{ServiceType.APIP.name()};

    public static Map<String,Object> settingMap = new HashMap<>();

   public static void main(String[] args) throws Exception {
       Menu.welcome(clientName);

       settingMap.put(Settings.SHARE_API, false);

       br = new BufferedReader(new InputStreamReader(System.in));

       settings = Starter.startClient(clientName, serviceAliases, settingMap, br);
       if(settings==null)return;
       configure = settings.getConfig();
       byte[] symKey = settings.getSymKey();
       apipAccount = settings.getApiAccount(ServiceType.APIP);
       apipClient = (ApipClient) settings.getClient(ServiceType.APIP);

       Menu menu = new Menu();

       ArrayList<String> menuItemList = new ArrayList<>();
       menuItemList.add("SendTx");
       menuItemList.add("SignMsgEcdsa");
       menuItemList.add("VerifyMsgEcdsa");
       menuItemList.add("SignMsgSchnorr");
       menuItemList.add("VerifyMsgSchnorr");
       menuItemList.add("PriKeyConvert");
       menuItemList.add("PubKeyConvert");
       menuItemList.add("AddressConvert");
       menuItemList.add("MultiSign");
       menuItemList.add("Settings");

       menu.add(menuItemList);

       while (true) {
           System.out.println(" << APIP Client>>");
           menu.show();
           int choice = menu.choose(br);
           switch (choice) {
               case 1 -> sendTx(symKey, br);
               case 2 -> signMsgEcdsa(symKey);
               case 3 -> verifyMsgEcdsa(br);
               case 4 -> signMsgSchnorr(symKey, br);
               case 5 -> verifyMsgSchnorr(br);
               case 6 -> priKeyConvert(symKey, br);
               case 7 -> pubKeyConvert(symKey);
               case 8 -> addressConvert(symKey, br);
               case 9 -> multiSign(symKey);
               case 10 -> {
                settings.setting(symKey,br,null);
                symKey = settings.getSymKey();
               }
               case 0 -> {
                   return;
               }
           }
       }
   }



private static void multiSign( byte[] symKey) {
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
               case 1 -> createFid();
               case 2 -> showFid();
               case 3 -> createTx();
               case 4 -> signTx(symKey);
               case 5 -> buildSignedTx();
               case 0 -> {
                   return;
               }
           }
       }
   }

   private static void buildSignedTx() {
       String[] signedData = Inputer.inputStringArray(br, "Input the signed data. Enter to end:", 0);

       String signedTx = TxCreator.buildSignedTx(signedData);
       if (signedTx == null) return;
       System.out.println(signedTx);
       Menu.anyKeyToContinue(br);
   }

   private static void signTx(byte[] symKey) {

       byte[] priKey;
       while (true) {
           System.out.println("Sign with Apip Buyer? y/n:");
           String input = Inputer.inputString(br);
           if ("y".equals(input)) {
               Decryptor decryptor = new Decryptor();
               CryptoDataByte cryptoDataByte = decryptor.decrypt(apipAccount.getUserPriKeyCipher(),symKey);
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

           System.out.println("Input the unsigned data json string: ");
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

   private static void createTx() {

       String fid = inputGoodFid(br, "Input the multisig fid:");

       Map<String, P2SH> p2shMap = apipClient.p2shByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, new String[]{fid});
       P2SH p2sh = p2shMap.get(fid);
       if (p2sh == null) {
           System.out.println(fid + " is not found.");
           return;
       }

       System.out.println(JsonTools.toNiceJson(p2sh));

       List<SendTo> sendToList = SendTo.inputSendToList(br);
       String msg = Inputer.inputString(br, "Input the message for OpReturn. Enter to ignore:");
       int msgLen;
       if ("".equals(msg)) {
           msg = null;
           msgLen = 0;
       } else msgLen = msg.getBytes().length;
       double sum = 0;
       for (SendTo sendTo : sendToList) {
           sum += sendTo.getAmount();
       }

       long fee = TxCreator.calcSizeMultiSign(0, sendToList.size(), msgLen, p2sh.getM(), p2sh.getN());
       double feeDouble = ParseTools.satoshiToCoin(fee);

       List<Cash> cashList = apipClient.cashValid(fid, sum + feeDouble, null, RequestMethod.POST, AuthType.FC_SIGN_BODY);
       byte[] rawTx = TxCreator.createMultiSignRawTx(cashList, sendToList, msg, p2sh, DEFAULT_FEE_RATE);

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

   private static void createFid() {
       String[] fids = inputFidArray(br, "Input FIDs. Enter to end:", 0);
       if (fids.length > 16) {
           System.out.println("The FIDs can not be more than 16.");
           return;
       }
       int m = Inputer.inputInteger(br, "How many signatures is required? ", 16);

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
       System.out.println("Your multisig FID: \n" + p2SH.getFid());
       Shower.printUnderline(10);
       Menu.anyKeyToContinue(br);
   }

   private static void showFid() {
       String fid = inputGoodFid(br, "Input the multisig FID:");
       if (!fid.startsWith("3")) {
           System.out.println("A multisig FID should start with '3'.");
           return;
       }
       System.out.println("Requesting APIP from " + apipAccount.getApiUrl());
       Map<String, P2SH> p2shMap = apipClient.p2shByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, new String[]{fid});
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

   private static void pubKeyConvert(byte[] symKey) throws Exception {
        String pubKey33;
        while (true) {
            System.out.println("Enter to convert the local public key, or input the public key:");
            String input = Inputer.inputString(br);
            if ("".equals(input)) {
                byte[] priKey = apipAccount.decryptUserPriKey(apipAccount.getUserPriKeyCipher(), symKey);
                pubKey33 = Hex.toHex(KeyTools.priKeyToPubKey(priKey));
                break;
            } else {
                pubKey33 = KeyTools.getPubKey33(input);
                if (pubKey33 == null){
                System.out.println("Wrong pubKey.");
                    continue;
                } 
                break;
            }
        }   
        Shower.printUnderline(10);
        System.out.println("FID: " + KeyTools.pubKeyToFchAddr(pubKey33));
        System.out.println("* PubKey 33 bytes compressed hex:\n" + pubKey33);
        System.out.println("* PubKey 65 bytes uncompressed hex:\n" + KeyTools.recoverPK33ToPK65(pubKey33));
        System.out.println("* PubKey WIF uncompressed:\n" + KeyTools.getPubKeyWifUncompressed(pubKey33));
        System.out.println("* PubKey WIF compressed with ver 0:\n" + KeyTools.getPubKeyWifCompressedWithVer0(pubKey33));
        System.out.println("* PubKey WIF compressed without ver:\n" + KeyTools.getPubKeyWifCompressedWithoutVer(pubKey33));
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
   }

   private static void addressConvert(byte[] symKey, BufferedReader br) throws Exception {
    Map<String, String> addrMap = null;
    String id=null;
    String pubKey=null;

    while (true) {
        System.out.println("Enter to convert the local FID, or input the address or pubKey:");
        String input = Inputer.inputString(br);
        if ("".equals(input)) {
            byte[] priKey = apipAccount.decryptUserPriKey(apipAccount.getUserPriKeyCipher(), symKey);
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
                } else if(id.startsWith("bitcoincash") || id.startsWith("bch")||id.startsWith("q")||id.startsWith("p")||id.startsWith("q")) {
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

   private static void priKeyConvert(byte[] symKey, BufferedReader br) {
       byte[] priKey;
       System.out.println("By a new FID? 'y' to confirm, others to use the local priKey:");
       String input = Inputer.inputString(br);
       if ("y".equals(input)) {
           priKey = KeyTools.inputCipherGetPriKey(br);
           if (priKey== null) return;
       } else priKey = apipAccount.decryptUserPriKey(apipAccount.getUserPriKeyCipher(), symKey);

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

   private static void sendTx(byte[] symKey, BufferedReader br) {
       byte[] priKey = apipAccount.decryptUserPriKey(apipAccount.getUserPriKeyCipher(), symKey);
       String fid = priKeyToFid(priKey);

       while(true) {
           System.out.println("Enter to send from " + fid + ". Press 'b' to input a private key, or 'c' to input a cipher of a private key:");
           String input = Inputer.inputString(br);
           if ("c".equals(input)) {
               priKey = KeyTools.inputCipherGetPriKey(br);
               if (priKey != null) break;
           } else if ("b".equals(input)) {
               input = Inputer.inputString(br);
               priKey = KeyTools.getPriKey32(input);
               if (priKey != null) break;
           } else if ("".equals(input)) {
               priKey = apipAccount.decryptUserPriKey(apipAccount.getUserPriKeyCipher(), symKey);
               if (priKey != null) break;
           }
       }

       String sender = priKeyToFid(priKey);
       System.out.println("Sender:  " + sender);

       CidInfo cidInfo = apipClient.cidInfoById(sender);
       if(cidInfo==null){
           System.out.println("Failed to get the balance of "+ sender);
           return;
       }

       System.out.println("Balance: "+ParseTools.satoshiToCoin(cidInfo.getBalance())+".\nCashes:  "+cidInfo.getCash());

       List<SendTo> sendToList = SendTo.inputSendToList(br);

       double sum = 0;
       for (SendTo sendTo : sendToList) sum += sendTo.getAmount();

       String urlHead = Constants.UrlHead_CID_CASH;

       System.out.println("Input the message written on the blockchain . Enter to ignore:");
       String msg = Inputer.inputString(br);

       System.out.println("Send to: ");
       for(SendTo sendTo:sendToList){
           System.out.println(sendTo.getFid()+" : "+sendTo.getAmount());
       }
       System.out.println("Message: "+msg);
       if(!askIfYes(br,"Are you sure to send?"))return;

       long fee = TxCreator.calcTxSize(0, sendToList.size(), msg.length());


       System.out.println("Getting cashes from " + urlHead + " ...");

       List<Cash> cashList = apipClient.cashValid(sender, sum + ((double) fee / COIN_TO_SATOSHI),null, RequestMethod.POST, AuthType.FC_SIGN_BODY);

       String txSigned = TxCreator.createTransactionSignFch(cashList, priKey, sendToList, msg);

       System.out.println("Signed tx:");
       Shower.printUnderline(10);
       System.out.println(txSigned);
       Shower.printUnderline(10);

       System.out.println("Broadcast with " + urlHead + " ...");

       String txid = apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);

       System.out.println("Sent Tx:");
       Shower.printUnderline(10);
       System.out.println(txid);
       Shower.printUnderline(10);
       Menu.anyKeyToContinue(br);
   }

   private static void signMsgEcdsa(byte[] symKey) {
       byte[] priKey;
       System.out.println("By a new FID? 'y' to confirm, others to use the local priKey:");
       String input = Inputer.inputString(br);
       if ("y".equals(input)) {
           priKey = KeyTools.inputCipherGetPriKey(br);
           if (priKey== null) return;
       } else priKey = apipAccount.decryptUserPriKey(apipAccount.getUserPriKeyCipher(), symKey);

       String signer = priKeyToFid(priKey);
       System.out.println("The signer is :" + signer);

       System.out.println("Input the message to be sign. Enter to exit:");
       String msg = Inputer.inputString(br);
       if ("".equals(msg)) return;

       Signature signature = new Signature();
       signature.sign(signer, priKey, BTC_EcdsaSignMsg_No1_NrC7);

    //    ECKey ecKey = ECKey.fromPrivate(priKey);
    //    System.out.println("Input the message to be sign. Enter to exit:");
    //    String sign = ecKey.signMessage(msg);
    //    Signature signature = new Signature(signer, msg, sign, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, null);
       System.out.println("Signature:");
       Shower.printUnderline(10);
       System.out.println(signature.toNiceJson());
       Shower.printUnderline(10);
       Menu.anyKeyToContinue(br);
   }

   private static void verifyMsgEcdsa(BufferedReader br) {
       System.out.println("Input the signature:");
       String input = Inputer.inputStringMultiLine(br);
       if (input == null) return;

       Signature signature = Signature.fromJson(input);
       if (signature == null) {
           System.out.println("Parse signature wrong.");
           return;
       }

       System.out.println("Resulet:"+signature.verify());
       Menu.anyKeyToContinue(br);
   }

   private static void signMsgSchnorr(byte[] symKey, BufferedReader br) {
       byte[] priKey;
       System.out.println("By a new FID? 'y' to confirm, others to use the local priKey:");
       String input = Inputer.inputString(br);
       if ("y".equals(input)) {
           priKey = KeyTools.inputCipherGetPriKey(br);
           if (priKey== null) return;
       } else priKey = apipAccount.decryptUserPriKey(apipAccount.getUserPriKeyCipher(), symKey);

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

   private static void verifyMsgSchnorr(BufferedReader br) {
       System.out.println("Input the signature:");
       String input = Inputer.inputStringMultiLine(br);
       if (input == null) return;

       Signature signature = Signature.parseSignature(input);
       if (signature == null) {
           System.out.println("Parse signature wrong.");
           return;
       }

       System.out.println("Resulet:"+signature.verify());
       Menu.anyKeyToContinue(br);
   }

//    public static byte[] resetPassword(BufferedReader br) {

//        byte[] passwordBytesOld;
//        while (true) {
//            System.out.print("Check password. ");

//            passwordBytesOld = Inputer.getPasswordBytes(br);
//            byte[] sessionKey = ApiAccount.decryptSessionKey(apipAccount.getSession().getSessionKeyCipher(), Hash.sha256x2(passwordBytesOld));
//            if (sessionKey != null) break;
//            System.out.println("Wrong password. Try again.");
//        }

//        byte[] passwordBytesNew;
//        passwordBytesNew = Inputer.inputAndCheckNewPassword(br);

//        byte[] symKeyOld = Hash.sha256x2(passwordBytesOld);

//        byte[] sessionKey = ApiAccount.decryptSessionKey(apipAccount.getSession().getSessionKeyCipher(), symKeyOld);
//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(apipAccount.getUserPriKeyCipher(), symKeyOld);

//        byte[] symKeyNew = Hash.sha256x2(passwordBytesNew);
//        String buyerPriKeyCipherNew = EccAes256K1P7.encryptWithSymKey(priKey, symKeyNew);
//        if (buyerPriKeyCipherNew == null) {
//            System.out.println("Encrypt buyer's priKey with new password wrong.");
//            return passwordBytesOld;
//        }
//        String sessionKeyCipherNew = EccAes256K1P7.encryptWithSymKey(sessionKey, symKeyNew);
//        if (sessionKeyCipherNew.contains("Error")) {
//            System.out.println("Get sessionKey wrong:" + sessionKeyCipherNew);
//        }
//        apipAccount.getSession().setSessionKeyCipher(sessionKeyCipherNew);
//        apipAccount.setUserPriKeyCipher(buyerPriKeyCipherNew);

//        ApiAccount.writeApipParamsToFile(apipAccount, APIP_Account_JSON);
//        return symKeyNew;
//    }

//    public static void refreshSessionKey(byte[] symKey) {
//        System.out.println("Refreshing ...");
//        byte[] priKey = apipAccount.decryptUserPriKey(apipAccount.getUserPriKeyCipher(), symKey);
//        apipClient.signInEcc(priKey, RequestBody.SignInMode.REFRESH);
//        System.out.println(JsonTools.toNiceJson(apipClient.getFcClientEvent().getResponseBody().getData()));
//    }

//    public static void checkApip(ApiAccount initApiAccount) {
//        Shower.printUnderline(20);
//        System.out.println("Apip Service:");

//        System.out.println("Requesting ...");
//        Service service = apipClient.serviceById(initApiAccount.getProviderId());
//        System.out.println(JsonTools.toNiceJson(service));

//        Shower.printUnderline(20);
//        System.out.println("User Params:");
//        System.out.println(JsonTools.toNiceJson(initApiAccount));
//        Shower.printUnderline(20);
//        Menu.anyKeyToContinue(br);
//    }

//    private static void resetApip(String fid,ApiAccount initApiAccount,Configure configure, byte[] symKey) {
//        if(!Configure.checkPassword(br, symKey, configure))return;
//        apipAccount = configure.getApiAccount(symKey, fid, ServiceType.APIP, null, false);
//    }
}
