//package fch;
//
//import apip.apipData.CidInfo;
//import apip.apipData.RequestBody;
//import fch.fchData.Address;
//import fch.fchData.Cash;
//import fch.fchData.P2SH;
//import fch.fchData.SendTo;
//import appTools.Inputer;
//import appTools.Menu;
//import appTools.Shower;
//import clients.apipClient.*;
//import config.ApiAccount;
//import constants.Constants;
//import constants.Strings;
//import crypto.Hash;
//import crypto.KeyTools;
//import crypto.old.EccAes256K1P7;
//import fcData.Signature;
//import javaTools.BytesTools;
//import javaTools.JsonTools;
//import org.bitcoinj.core.ECKey;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.security.SignatureException;
//import java.util.*;
//
//import static fch.Inputer.inputFidArray;
//import static fch.Inputer.inputGoodFid;
//import static fch.TxCreator.DEFAULT_FEE_RATE;
//import static constants.Constants.APIP_Account_JSON;
//import static constants.Constants.COIN_TO_SATOSHI;
//import static constants.Strings.newCashMapKey;
//import static constants.Strings.spendCashMapKey;
//import static crypto.KeyTools.priKeyToFid;
//import static crypto.KeyTools.priKeyToPubKey;
//
//
//public class startFchClient {
//
//    static ApiAccount initApiAccount;
//    private static String via = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7";
//
//    public static void main(String[] args) throws Exception {
//        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//        byte[] sessionKey;
//        byte[] symKey;
//        while (true) {
//            Shower.printUnderline(20);
//            System.out.println("\nWelcome to the Freecash Wallet Tools with APIP Client.");
//            Shower.printUnderline(20);
//            System.out.println("Confirm or set your password...");
//            byte[] passwordBytes = Inputer.getPasswordBytes(br);
//            symKey = Hash.sha256x2(passwordBytes);
//            try {
//                initApiAccount = ApiAccount.checkApipAccount(br, passwordBytes);
//                if (initApiAccount == null) return;
//                sessionKey = ApiAccount.decryptSessionKey(initApiAccount.getSession().getSessionKeyCipher(), Hash.sha256x2(passwordBytes));
//                if (sessionKey == null) continue;
//                BytesTools.clearByteArray(passwordBytes);
//                break;
//            } catch (Exception e) {
//                e.printStackTrace();
//                System.out.println("Wrong password, try again.");
//            }
//        }
//
//        Menu menu = new Menu();
//
//        ArrayList<String> menuItemList = new ArrayList<>();
//        menuItemList.add("SendTx");
//        menuItemList.add("SignMsgEcdsa");
//        menuItemList.add("VerifyMsgEcdsa");
//        menuItemList.add("SignMsgSchnorr");
//        menuItemList.add("VerifyMsgSchnorr");
//        menuItemList.add("PriKeyConvert");
//        menuItemList.add("PubKeyConvert");
//        menuItemList.add("AddressConvert");
//        menuItemList.add("MultiSign");
//        menuItemList.add("Settings");
//
//        menu.add(menuItemList);
//
//        while (true) {
//            System.out.println(" << APIP Client>>");
//            menu.show();
//            int choice = menu.choose(br);
//            switch (choice) {
//                case 1 -> sendTx(symKey, br);
//                case 2 -> signMsgEcdsa(symKey, br);
//                case 3 -> verifyMsgEcdsa(br);
//                case 4 -> signMsgSchnorr(symKey, br);
//                case 5 -> verifyMsgSchnorr(br);
//                case 6 -> priKeyConvert(symKey, br);
//                case 7 -> pubKeyConvert(br);
//                case 8 -> addressConvert(br);
//                case 9 -> multiSign(sessionKey, symKey, br);
//                case 10 -> setting(sessionKey, symKey, br);
//                case 0 -> {
//                    BytesTools.clearByteArray(sessionKey);
//                    return;
//                }
//            }
//        }
//    }
//
//    private static void multiSign(byte[] sessionKey, byte[] symKey, BufferedReader br) {
//        Menu menu = new Menu();
//        menu.add("Create multisig FID")
//                .add("Show multisig FID")
//                .add("Creat a multisig raw TX")
//                .add("Sign a multisig raw TX")
//                .add("Build the final multisig TX")
//        ;
//        while (true) {
//            System.out.println(" << Multi Signature>>");
//
//            menu.show();
//            int choice = menu.choose(br);
//            switch (choice) {
//                case 1 -> createFid(sessionKey, br);
//                case 2 -> showFid(sessionKey, br);
//                case 3 -> createTx(sessionKey, br);
//                case 4 -> signTx(symKey, br);
//                case 5 -> buildSignedTx(br);
//                case 0 -> {
//                    BytesTools.clearByteArray(sessionKey);
//                    return;
//                }
//            }
//        }
//    }
//
//    private static void buildSignedTx(BufferedReader br) {
//        String[] signedData = Inputer.inputStringArray(br, "Input the signed data. Enter to end:", 0);
//
//        String signedTx = TxCreator.buildSignedTx(signedData);
//        if (signedTx == null) return;
//        System.out.println(signedTx);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void signTx(byte[] symKey, BufferedReader br) {
//
//        byte[] priKey;
//        while (true) {
//            System.out.println("Sign with Apip Buyer? y/n:");
//            String input = Inputer.inputString(br);
//            if ("y".equals(input))
//                priKey = EccAes256K1P7.decryptJsonBytes(initApiAccount.getUserPriKeyCipher(), symKey);
//            else {
//                try {
//                    priKey = KeyTools.inputCipherGetPriKey(br);
//                } catch (Exception e) {
//                    System.out.println("Wrong input. Try again.");
//                    continue;
//                }
//            }
//
//            if (priKey == null) {
//                System.out.println("Get priKey wrong");
//                return;
//            }
//
//            System.out.println("Input the unsigned data json string: ");
//            String multiSignDataJson = Inputer.inputStringMultiLine(br);
//
//            showRawTxInfo(multiSignDataJson, br);
//
//            System.out.println("Multisig data signed by " + priKeyToFid(priKey) + ":");
//            Shower.printUnderline(60);
//            System.out.println(TxCreator.signSchnorrMultiSignTx(multiSignDataJson, priKey));
//            BytesTools.clearByteArray(priKey);
//            Shower.printUnderline(60);
//            Menu.anyKeyToContinue(br);
//
//            input = Inputer.inputString(br, "Sign with another priKey?y/n");
//            if (!"y".equals(input)) {
//                BytesTools.clearByteArray(priKey);
//                return;
//            }
//        }
//    }
//
//    public static void showRawTxInfo(String multiSignDataJson, BufferedReader br) {
//        MultiSigData multiSigData = MultiSigData.fromJson(multiSignDataJson);
//
//        byte[] rawTx = multiSigData.getRawTx();
//        Map<String, Object> result;
//        try {
//            result = RawTxParser.parseRawTxBytes(rawTx);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return;
//        }
//        List<Cash> spendCashList = (List<Cash>) result.get(spendCashMapKey);
//        List<Cash> issuredCashList = (List<Cash>) result.get(newCashMapKey);
//        String msg = (String) result.get(Strings.OPRETURN);
//
//        Map<String, Cash> spendCashMap = new HashMap<>();
//        for (Cash cash : multiSigData.getCashList()) spendCashMap.put(cash.getCashId(), cash);
//
//        System.out.println("You are spending:");
//        Shower.printUnderline(60);
//        System.out.print(Shower.formatString("cashId", 68));
//        System.out.print(Shower.formatString("owner", 38));
//        System.out.println(Shower.formatString("fch", 20));
//        Shower.printUnderline(60);
//        for (Cash cash : spendCashList) {
//            Cash niceCash = spendCashMap.get(cash.getCashId());
//            if (niceCash == null) {
//                System.out.println("Warning： The cash " + cash.getCashId() + "in the rawTx is unfounded.");
//                return;
//            }
//            System.out.print(Shower.formatString(niceCash.getCashId(), 68));
//            System.out.print(Shower.formatString(niceCash.getOwner(), 38));
//            System.out.println(Shower.formatString(String.valueOf(ParseTools.satoshiToCoin(niceCash.getValue())), 20));
//        }
//        Shower.printUnderline(60);
//        Menu.anyKeyToContinue(br);
//        System.out.println("You are paying:");
//        Shower.printUnderline(60);
//        System.out.print(Shower.formatString("FID", 38));
//        System.out.println(Shower.formatString("fch", 20));
//        Shower.printUnderline(60);
//        for (Cash cash : issuredCashList) {
//            System.out.print(Shower.formatString(cash.getOwner(), 38));
//            System.out.println(Shower.formatString(String.valueOf(ParseTools.satoshiToCoin(cash.getValue())), 20));
//        }
//        Shower.printUnderline(60);
//        Shower.printUnderline(60);
//        Menu.anyKeyToContinue(br);
//        Shower.printUnderline(60);
//
//        if (msg != null) {
//            System.out.println("The message in OP_RETURN is: ");
//            Shower.printUnderline(60);
//            System.out.println(msg);
//            Shower.printUnderline(60);
//            Menu.anyKeyToContinue(br);
//        }
//    }
//
//    private static void createTx(byte[] sessionKey, BufferedReader br) {
//
//        String fid = inputGoodFid(br, "Input the multisig fid:");
//        ApipClientEvent apipClientData = BlockchainAPIs.p2shByIdsPost(initApiAccount.getApiUrl(), new String[]{fid}, initApiAccount.getVia(), sessionKey);
//
//        if (apipClientData == null || apipClientData.checkResponse() != 0) {
//            System.out.println(JsonTools.toNiceJson(apipClientData.getResponseBody()));
//            return;
//        }
//        Map<String, P2SH> p2shMap = DataGetter.getP2SHMap(apipClientData.getResponseBody().getData());
//        P2SH p2sh = p2shMap.get(fid);
//        if (p2sh == null) {
//            System.out.println(fid + " is not found.");
//            return;
//        }
//
//        System.out.println(JsonTools.toNiceJson(p2sh));
//
//        List<SendTo> sendToList = SendTo.inputSendToList(br);
//        String msg = Inputer.inputString(br, "Input the message for OpReturn. Enter to ignore:");
//        int msgLen;
//        if ("".equals(msg)) {
//            msg = null;
//            msgLen = 0;
//        } else msgLen = msg.getBytes().length;
//        double sum = 0;
//        for (SendTo sendTo : sendToList) {
//            sum += sendTo.getAmount();
//        }
//
//        long fee = TxCreator.calcSizeMultiSign(0, sendToList.size(), msgLen, p2sh.getM(), p2sh.getN());
//        double feeDouble = fee / COIN_TO_SATOSHI;
//        apipClientData = WalletAPIs.cashValidForPayPost(initApiAccount.getApiUrl(), fid, sum + feeDouble, initApiAccount.getVia(), sessionKey);
//
//        if(apipClientData.checkResponse()!=0) return;
//
//        List<Cash> cashList = DataGetter.getCashList(apipClientData.getResponseBody().getData());
//
//        byte[] rawTx = TxCreator.createMultiSignRawTx(cashList, sendToList, msg, p2sh, DEFAULT_FEE_RATE);
//
//        MultiSigData multiSignData = new MultiSigData(rawTx, p2sh, cashList);
//
//        System.out.println("Multisig data unsigned:");
//        Shower.printUnderline(10);
//        System.out.println(multiSignData.toJson());
//        Shower.printUnderline(10);
//
//        System.out.println("Next step: sign it separately with the priKeys of: ");
//        Shower.printUnderline(10);
//        for (String fid1 : p2sh.getFids()) System.out.println(fid1);
//        Shower.printUnderline(10);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void createFid(byte[] sessionKey, BufferedReader br) {
//        String[] fids = inputFidArray(br, "Input FIDs. Enter to end:", 0);
//        if (fids.length > 16) {
//            System.out.println("The FIDs can not be more than 16.");
//            return;
//        }
//        int m = Inputer.inputInteger(br, "How many signatures is required? ", 16);
//
//        ApipClientEvent apipClientData = BlockchainAPIs.fidByIdsPost(initApiAccount.getApiUrl(), fids, initApiAccount.getVia(), sessionKey);
//        if(apipClientData.checkResponse()!=0) return;
//
//        Map<String, Address> fidMap = DataGetter.getAddressMap(apipClientData.getResponseBody().getData());
//
//        List<byte[]> pubKeyList = new ArrayList<>();
//        for (String fid : fids) {
//            String pubKey = fidMap.get(fid).getPubKey();
//            pubKeyList.add(HexFormat.of().parseHex(pubKey));
//        }
//
//        P2SH p2SH = TxCreator.genMultiP2sh(pubKeyList, 2);
//
//        String mFid = p2SH.getFid();
//
//        Shower.printUnderline(10);
//        System.out.println("The multisig information is: \n" + JsonTools.toNiceJson(p2SH));
//        System.out.println("It's generated from :");
//        for (String fid : fids) {
//            System.out.println(fid);
//        }
//        Shower.printUnderline(10);
//        System.out.println("Your multisig FID: \n" + p2SH.getFid());
//        Shower.printUnderline(10);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void showFid(byte[] sessionKey, BufferedReader br) {
//        String fid = inputGoodFid(br, "Input the multisig FID:");
//        if (!fid.startsWith("3")) {
//            System.out.println("A multisig FID should start with '3'.");
//            return;
//        }
//        System.out.println("Requesting APIP from " + initApiAccount.getApiUrl());
//        ApipClientEvent apipClientData = BlockchainAPIs.p2shByIdsPost(initApiAccount.getApiUrl(), new String[]{fid}, initApiAccount.getVia(), sessionKey);
//        if(apipClientData.checkResponse()!=0) return;
//        ;
//        Map<String, P2SH> p2shMap = DataGetter.getP2SHMap(apipClientData.getResponseBody().getData());
//        P2SH p2sh = p2shMap.get(fid);
//
//        if (p2sh == null) {
//            System.out.println(fid + " is not found.");
//            return;
//        }
//        Shower.printUnderline(10);
//        System.out.println("Multisig:");
//        System.out.println(JsonTools.toNiceJson(p2sh));
//        Shower.printUnderline(10);
//        System.out.println("The members:");
//
//        apipClientData = IdentityAPIs.cidInfoByIdsPost(initApiAccount.getApiUrl(), p2sh.getFids(), initApiAccount.getVia(), sessionKey);
//        if (apipClientData == null || apipClientData.checkResponse() != 0) {
//            System.out.println(JsonTools.toNiceJson(apipClientData.getResponseBody()));
//            return;
//        }
//        Map<String, CidInfo> cidInfoMap = DataGetter.getCidInfoMap(apipClientData.getResponseBody().getData());
//        System.out.println(JsonTools.toNiceJson(cidInfoMap));
//        Shower.printUnderline(10);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void pubKeyConvert(BufferedReader br) throws Exception {
//        System.out.println("Input the public key:");
//        String input = Inputer.inputString(br);
//        String pubKey33 = KeyTools.getPubKey33(input);
//        Shower.printUnderline(10);
//        System.out.println("* PubKey 33 bytes compressed hex:\n" + pubKey33);
//        System.out.println("* PubKey 65 bytes uncompressed hex:\n" + KeyTools.recoverPK33ToPK65(pubKey33));
//        System.out.println("* PubKey WIF uncompressed:\n" + KeyTools.getPubKeyWifUncompressed(pubKey33));
//        System.out.println("* PubKey WIF compressed with ver 0:\n" + KeyTools.getPubKeyWifCompressedWithVer0(pubKey33));
//        System.out.println("* PubKey WIF compressed without ver:\n" + KeyTools.getPubKeyWifCompressedWithoutVer(pubKey33));
//        Shower.printUnderline(10);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void addressConvert(BufferedReader br) throws Exception {
//        System.out.println("Input the address or public key:");
//        String input = Inputer.inputString(br);
//
//        Map<String, String> addrMap = new HashMap<>();
//        String pubKey;
//
//        if (input.startsWith("F") || input.startsWith("1") || input.startsWith("D") || input.startsWith("L")) {
//            byte[] hash160 = KeyTools.addrToHash160(input);
//            addrMap = KeyTools.hash160ToAddresses(hash160);
//        } else {
//            pubKey = KeyTools.getPubKey33(input);
//            addrMap = KeyTools.pubKeyToAddresses(pubKey);
//        }
//        Shower.printUnderline(10);
//        System.out.println(JsonTools.toNiceJson(addrMap));
//        Shower.printUnderline(10);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void priKeyConvert(byte[] symKey, BufferedReader br) {
//        byte[] priKey;
//        System.out.println("By a new FID? 'y' to confirm, others to use the local priKey:");
//        String input = Inputer.inputString(br);
//        if ("y".equals(input)) {
//            priKey = KeyTools.inputCipherGetPriKey(br);
//            if (priKey== null) return;
//        } else priKey = initApiAccount.decryptUserPriKey(initApiAccount.getUserPriKeyCipher(), symKey);
//
//        if (priKey == null) return;
//        String fid = priKeyToFid(priKey);
//        Shower.printUnderline(10);
//        System.out.println("* FID:" + fid);
//        System.out.println("* PubKey:" + HexFormat.of().formatHex(priKeyToPubKey(priKey)));
//
//        System.out.println("* PriKey 32 bytes:");
//        String priKey32 = HexFormat.of().formatHex(priKey);
//        System.out.println(priKey32);
//        System.out.println("* PriKey WIF:");
//        System.out.println(KeyTools.priKey32To37(priKey32));
//        System.out.println("* PriKey WIF compressed:");
//        System.out.println(KeyTools.priKey32To38(priKey32));
//        Shower.printUnderline(10);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void sendTx(byte[] symKey, BufferedReader br) {
//        byte[] priKey = initApiAccount.decryptUserPriKey(initApiAccount.getUserPriKeyCipher(), symKey);
//        String fid = priKeyToFid(priKey);
//
//        System.out.println("A priKey is needed. 'g' to generate a new one. 'i' to input a priKey, others to use the local priKey of:" + fid);
//        String input = Inputer.inputString(br);
//        if ("i".equals(input)) {
//            priKey = KeyTools.inputCipherGetPriKey(br);
//            if (priKey== null) return;
//        } else if ("g".equals(input)) {
//            priKey = KeyTools.genNewFid(br).getPrivKeyBytes();
//        }
//
//        String sender = priKeyToFid(priKey);
//        System.out.println("The sender is :" + sender);
//        byte[] sessionKey = initApiAccount.decryptSessionKey(initApiAccount.getSession().getSessionKeyCipher(), symKey);
//        ApipClientEvent apipClientData;
//
//        apipClientData = BlockchainAPIs.fidByIdsPost(initApiAccount.getApiUrl(), new String[]{sender}, initApiAccount.getVia(), sessionKey);
//        if(apipClientData.checkResponse()!=0) {
//            System.out.println("The fid is no found in the APIP.");
//        } else {
//            Map<String, Address> fidMap = DataGetter.getAddressMap(apipClientData.getResponseBody().getData());
//            Address addr = fidMap.get(sender);
//            if (addr == null) {
//                System.out.println("The fid is no found in the APIP.");
//                return;
//            } else JsonTools.gsonPrint(addr);
//        }
//        List<SendTo> sendToList = SendTo.inputSendToList(br);
//        double sum = 0;
//        for (SendTo sendTo : sendToList) sum += sendTo.getAmount();
//
//        String urlHead = Constants.UrlHead_CID_CASH;
//
//        System.out.println("Input the opreturn message. Enter to ignore:");
//        String msg = Inputer.inputString(br);
//
//        long fee = TxCreator.calcTxSize(0, sendToList.size(), msg.length());
//
//
//        System.out.println("Getting cashes from " + urlHead + " ...");
//
//        apipClientData = WalletAPIs.cashValidForPayPost(urlHead, sender, sum + ((double) fee / COIN_TO_SATOSHI), initApiAccount.getVia(), sessionKey);
//        if (apipClientData == null || apipClientData.checkResponse() != 0) {
//            System.out.println("Failed to get cashes." + apipClientData.getMessage() + apipClientData.getResponseBody().getData());
//            return;
//        }
//
//        List<Cash> cashList = DataGetter.getCashList(apipClientData.getResponseBody().getData());
//
//        String txSigned = TxCreator.createTransactionSignFch(cashList, priKey, sendToList, msg);
//
//        System.out.println("Signed tx:");
//        Shower.printUnderline(10);
//        System.out.println(txSigned);
//        Shower.printUnderline(10);
//
//        System.out.println("Broadcast with " + urlHead + " ...");
//        apipClientData = WalletAPIs.broadcastTxPost(urlHead, txSigned, initApiAccount.getVia(), sessionKey);
//        if (apipClientData.checkResponse() != 0) {
//            System.out.println(apipClientData.getCode() + ": " + apipClientData.getMessage());
//            if (apipClientData.getResponseBody().getData() != null)
//                System.out.println(apipClientData.getResponseBody().getData());
//            return;
//        }
//
//        System.out.println("Sent Tx:");
//        Shower.printUnderline(10);
//        System.out.println((String) apipClientData.getResponseBody().getData());
//        Shower.printUnderline(10);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void signMsgEcdsa(byte[] symKey, BufferedReader br) {
//        byte[] priKey;
//        System.out.println("By a new FID? 'y' to confirm, others to use the local priKey:");
//        String input = Inputer.inputString(br);
//        if ("y".equals(input)) {
//            priKey = KeyTools.inputCipherGetPriKey(br);
//            if (priKey== null) return;
//        } else priKey = initApiAccount.decryptUserPriKey(initApiAccount.getUserPriKeyCipher(), symKey);
//
//        String signer = priKeyToFid(priKey);
//        System.out.println("The signer is :" + signer);
//
//        ECKey ecKey = ECKey.fromPrivate(priKey);
//
//        System.out.println("Input the message to be sign. Enter to exit:");
//        String msg = Inputer.inputString(br);
//        if ("".equals(msg)) return;
//
//        String sign = ecKey.signMessage(msg);
//        Signature signature = new Signature(signer, msg, sign, Constants.EcdsaBtcMsg_No1_NrC7);
//        System.out.println("Signature:");
//        Shower.printUnderline(10);
//        System.out.println(signature.toJsonAsyShortNice());
//        Shower.printUnderline(10);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void verifyMsgEcdsa(BufferedReader br) {
//        System.out.println("Input the signature:");
//        String input = Inputer.inputStringMultiLine(br);
//        if (input == null) return;
//
//        Signature signature = Signature.parseSignature(input);
//        if (signature == null) {
//            System.out.println("Parse signature wrong.");
//            return;
//        }
//
//        String signPubKey = null;
//        try {
//            signPubKey = ECKey.signedMessageToKey(signature.getMsg(), signature.getSign()).getPublicKeyAsHex();
//            System.out.println("Check result:");
//            Shower.printUnderline(10);
//            System.out.println(signature.getFid().equals(KeyTools.pubKeyToFchAddr(signPubKey)));
//            Shower.printUnderline(10);
//            Menu.anyKeyToContinue(br);
//        } catch (SignatureException e) {
//            System.out.println("Check signature wrong." + e.getMessage());
//        }
//    }
//
//    private static void signMsgSchnorr(byte[] symKey, BufferedReader br) {
//        byte[] priKey;
//        System.out.println("By a new FID? 'y' to confirm, others to use the local priKey:");
//        String input = Inputer.inputString(br);
//        if ("y".equals(input)) {
//            priKey = KeyTools.inputCipherGetPriKey(br);
//            if (priKey== null) return;
//        } else priKey = initApiAccount.decryptUserPriKey(initApiAccount.getUserPriKeyCipher(), symKey);
//
//        String signer = priKeyToFid(priKey);
//        System.out.println("The signer is :" + signer);
//
//        ECKey ecKey = ECKey.fromPrivate(priKey);
//
//        System.out.println("Input the message to be sign. Enter to exit:");
//        String msg = Inputer.inputString(br);
//        if ("".equals(msg)) return;
//
//        String sign = Wallet.schnorrMsgSign(msg, priKey);
//        Signature signature = new Signature(signer, msg, sign, Constants.EcdsaBtcMsg_No1_NrC7);
//
//        System.out.println("Signature:");
//        Shower.printUnderline(10);
//        System.out.println(signature.toJsonAsyShortNice());
//        Shower.printUnderline(10);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void verifyMsgSchnorr(BufferedReader br) {
//        System.out.println("Input the signature:");
//        String input = Inputer.inputStringMultiLine(br);
//        if (input == null) return;
//
//        Signature signature = Signature.parseSignature(input);
//        if (signature == null) {
//            System.out.println("Parse signature wrong.");
//            return;
//        }
//        try {
//
//            System.out.println("Result:");
//            Shower.printUnderline(10);
//            System.out.println(Wallet.schnorrMsgVerify(signature.getMsg(), signature.getSign(), signature.getFid()));
//            Shower.printUnderline(10);
//            Menu.anyKeyToContinue(br);
//        } catch (IOException e) {
//            System.out.println("Checking signature wrong." + e.getMessage());
//        }
//    }
//
//    public static void setting(byte[] sessionKey, byte[] symKey, BufferedReader br) {
//        System.out.println("setting...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.add("Check APIP", "Reset APIP", "Refresh SessionKey", "Change password");
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> checkApip(initApiAccount, sessionKey, br);
//                case 2 -> resetApip(initApiAccount, br);
//                case 3 -> sessionKey = refreshSessionKey(symKey);
//                case 4 -> {
//                    byte[] symKeyNew = resetPassword(br);
//                    if (symKeyNew == null) break;
//                    symKey = symKeyNew;
//
//                }
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static byte[] resetPassword(BufferedReader br) {
//
//        byte[] passwordBytesOld;
//        while (true) {
//            System.out.print("Check password. ");
//
//            passwordBytesOld = Inputer.getPasswordBytes(br);
//            byte[] sessionKey = ApiAccount.decryptSessionKey(initApiAccount.getSession().getSessionKeyCipher(), Hash.sha256x2(passwordBytesOld));
//            if (sessionKey != null) break;
//            System.out.println("Wrong password. Try again.");
//        }
//
//        byte[] passwordBytesNew;
//        passwordBytesNew = Inputer.inputAndCheckNewPassword(br);
//
//        byte[] symKeyOld = Hash.sha256x2(passwordBytesOld);
//
//        byte[] sessionKey = ApiAccount.decryptSessionKey(initApiAccount.getSession().getSessionKeyCipher(), symKeyOld);
//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(initApiAccount.getUserPriKeyCipher(), symKeyOld);
//
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
//        initApiAccount.getSession().setSessionKeyCipher(sessionKeyCipherNew);
//        initApiAccount.setUserPriKeyCipher(buyerPriKeyCipherNew);
//
//        ApiAccount.writeApipParamsToFile(initApiAccount, APIP_Account_JSON);
//        return symKeyNew;
//    }
//
//    public static byte[] refreshSessionKey(byte[] symKey) {
//        System.out.println("Refreshing ...");
//        return signInEccPost(symKey, RequestBody.SignInMode.REFRESH);
//    }
//
//    public static void checkApip(ApiAccount initApiAccount, byte[] sessionKey, BufferedReader br) {
//        Shower.printUnderline(20);
//        System.out.println("Apip Service:");
//        String urlHead = initApiAccount.getApiUrl();
//        String[] ids = new String[]{initApiAccount.getProviderId()};
//        String via = initApiAccount.getVia();
//
//        System.out.println("Requesting ...");
//        ApipClientEvent apipClientData = ConstructAPIs.serviceByIdsPost(urlHead, ids, via, sessionKey);
//        System.out.println(apipClientData.getResponseBodyStr());
//
//        Shower.printUnderline(20);
//        System.out.println("User Params:");
//        System.out.println(JsonTools.toNiceJson(initApiAccount));
//        Shower.printUnderline(20);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void resetApip(ApiAccount initApiAccount, BufferedReader br) {
//        byte[] passwordBytes = Inputer.getPasswordBytes(br);
//        initApiAccount.updateApipAccount(br, passwordBytes);
//    }
//}
