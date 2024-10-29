package clients;

import apip.apipData.CidInfo;
import appTools.Menu;
import appTools.Shower;
import clients.apipClient.ApipClient;
import constants.Constants;
import crypto.CryptoDataByte;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fch.Inputer;
import fch.Wallet;
import feip.FeipTools;
import feip.feipData.Feip;
import javaTools.Hex;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;

import java.io.BufferedReader;

public class FeipClient {



    public static String sendFeip(String fid, String userPriKeyCipher, byte[] symKey, ApipClient apipClient, BufferedReader br, 
                                     String protocol, String version, String operation, Feip feip) {

        byte[] priKey = Client.decryptPriKey(userPriKeyCipher, symKey);
        if (priKey == null) {
            System.out.println("Failed to get the private key of " + fid);
            return null;
        }

        String opReturnStr = feip.toJson();
        long cd = Constants.CD_REQUIRED;
        int maxCashes = Wallet.MAX_CASHE_SIZE;

        if (Inputer.askIfYes(br, "Are you sure to do the following operation on chain?\n" + feip.toNiceJson() + "\n")) {
            String txSigned = Wallet.makeTx(br, priKey, null, null, opReturnStr, cd, maxCashes, apipClient, null);
            if (txSigned == null) {
                System.out.println("Failed to make tx.");
                return null;
            }
            String txId = apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if (Hex.isHex32(txId)) {
                System.out.println("The " + protocol + " operation is completed: " + txId + 
                                   ".\nWait a few minutes for confirmations before updating...");
                return txId;
            } else {
                System.out.println("Failed to perform " + protocol + " operation: " + txId);
            }
        }
        return null;
    }

    public static void setMaster(String fid, String userPriKeyCipher, long bestHeight, byte[] symKey, ApipClient apipClient, BufferedReader br) {
        if(userPriKeyCipher==null){
            System.out.println("The private key is required when set master.");
            return;
        }
        String master;
        String masterPubKey;

        byte[] priKey = Client.decryptPriKey(userPriKeyCipher,symKey);
        if(priKey==null){
            System.out.println("Failed to get private Key.");
            return;
        }

        while (true) {
            master = Inputer.inputString(br, "Input the FID or Public Key of the master:");

            if (Hex.isHexString(master)) {
                masterPubKey = master;
                master = KeyTools.pubKeyToFchAddr(master);
            }else {
                if (KeyTools.isValidFchAddr(master)){
                    CidInfo masterInfo = apipClient.cidInfoById(master);
                    if(masterInfo==null){
                        System.out.println("Failed to get CID info.");
                        return;
                    }
                    masterPubKey = masterInfo.getPubKey();
                }else {
                    System.out.println("It's not a good FID or public Key. Try again.");
                    continue;
                }
            }
            break;
        }

        if(!Inputer.askIfYes(br,"The master will get your private key and control all your on chain assets and rights. Are you sure to set?"))
            return;

        CryptoDataByte cipher = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7).encryptByAsyOneWay(priKey, Hex.fromHex(masterPubKey));
        if(cipher==null || cipher.getCode()!=0){
            System.out.println("Failed to encrypt priKey.");
            return;
        }
        String priKeyCipher = cipher.toJson();

        String dataOnChainJson = FeipTools.getMasterData(master,masterPubKey,priKeyCipher);
        long requiredCd = 0;
        int maxCashes=20;

        if (bestHeight > Constants.CDD_CHECK_HEIGHT)
            requiredCd = Constants.CD_REQUIRED;

        if("".equals(userPriKeyCipher)){
            String rawTx = Wallet.makeTxForCs(br,fid,null,dataOnChainJson,requiredCd,20,apipClient);
            System.out.println("Sign below TX with CryptoSign:");
            Shower.printUnderline(10);
            System.out.println(rawTx);
            Shower.printUnderline(10);
        }else {

            String result = Wallet.sendTxByApip(br,priKey, null, dataOnChainJson, requiredCd, maxCashes, apipClient);
            if (Hex.isHexString(result))
                System.out.println("The master was set. Wait for a few minutes for the confirmation on chain.");
            else System.out.println("Some thing wrong:" + result);
        }
        Menu.anyKeyToContinue(br);

    }

    public static void setCid(String fid, String userPriKeyCipher, long bestHeight, byte[] symKey, ApipClient apipClient, BufferedReader br) {
        String cid;
        cid = Inputer.inputString(br, "Input the name you want to give the address");
        if(FeipTools.isGoodCidName(cid)){
            String dataOnChainJson = FeipTools.getCidRegisterData(cid);
            long requiredCd = 0;
            int maxCashes=20;

            if (bestHeight > Constants.CDD_CHECK_HEIGHT)
                requiredCd = Constants.CD_REQUIRED;

            if("".equals(userPriKeyCipher)){
                String rawTx = Wallet.makeTxForCs(br, fid,null,dataOnChainJson,requiredCd,20,apipClient);
                System.out.println("Sign below TX with CryptoSign:");
                Shower.printUnderline(10);
                System.out.println(rawTx);
                Shower.printUnderline(10);
            }else {
                byte[] priKey = Client.decryptPriKey(userPriKeyCipher,symKey);
                if(priKey==null)return;
                String result = Wallet.sendTxByApip(br, priKey, null, dataOnChainJson, requiredCd, maxCashes, apipClient);
                if (Hex.isHexString(result))
                    System.out.println("CID was set. Wait for a few minutes for the confirmation on chain.");
                else System.out.println("Some thing wrong:" + result);
            }
            Menu.anyKeyToContinue(br);
        }
    }

    // Add more protocol-specific methods as needed...
}
