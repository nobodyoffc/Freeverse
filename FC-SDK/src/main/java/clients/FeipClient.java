package clients;

import apip.apipData.CidInfo;
import appTools.Menu;
import appTools.Shower;
import constants.Constants;
import crypto.CryptoDataByte;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fch.Inputer;
import fch.Wallet;
import fch.fchData.SendTo;
import feip.FeipTools;
import feip.feipData.*;
import feip.feipData.Feip.ProtocolName;
import feip.feipData.serviceParams.Params;
import handlers.MailHandler;
import tools.Hex;
import tools.JsonTools;
import tools.http.AuthType;
import tools.http.RequestMethod;
import nasa.NaSaRpcClient;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static appTools.Inputer.askIfYes;
import static fch.Wallet.makeTx;

public class FeipClient {


    public static <T,E extends Enum<E>> String sendFeip(byte[] priKey, String offLineFid, List<SendTo> sendToList, Long cd, T data, Class<T> tClass, ProtocolName protocolName, ApipClient apipClient, NaSaRpcClient nasaClient, BufferedReader br, Class<E> enumClass, Map<String,String[]> opFieldsMap)  {
        
        if(data==null && br==null){
            System.out.println("The data is required when send "+protocolName.getName()+".");
            return null;
        }

        if(data==null){
            try {
                if(br!=null) data = Inputer.createFromUserInput(br,tClass,"op",opFieldsMap);
                else data = tClass.getDeclaredConstructor().newInstance();

            } catch (Exception e) {
                System.out.println("Failed to create instance of " + tClass.getName());
                return null;
            }
        }else if(br!=null){
            try {
                Inputer.updateFromUserInput(br, data, "op", enumClass, opFieldsMap);
            } catch (IOException | ReflectiveOperationException e) {
                System.out.println("Failed to update data from input.");
                return null;
            }
        }

        Feip feip = Feip.fromProtocolName(protocolName);
        feip.setData(data);
        return sendFeip(feip, cd, priKey, offLineFid, sendToList, apipClient, nasaClient, br);
    }


    
    public static String sendFeip(List<SendTo> sendToList, Long cd, byte[]priKey, String offLineFid, Feip feip, ApipClient apipClient, NaSaRpcClient nasaClient) {
        if (priKey == null && offLineFid == null) return null;
        String opReturnStr = feip.toJson();
        if(cd ==null) cd= Constants.CD_REQUIRED;
        int maxCashes = Wallet.MAX_CASHE_SIZE;

        String result = makeTx(priKey, offLineFid, sendToList, opReturnStr, cd, maxCashes, apipClient, null, nasaClient);

        if(priKey!=null && Hex.isHexString(result)) return broadcastFeip(apipClient, nasaClient, result);
        return result;
    }

    public static String makeFeipTxUnsigned(String fid, List< SendTo > sendToList, Feip feip, ApipClient apipClient) {
        String opReturnStr = feip.toJson();
        long cd = Constants.CD_REQUIRED;
        int maxCashes = Wallet.MAX_CASHE_SIZE;
        return makeTx(null,fid,sendToList,opReturnStr,cd,maxCashes,apipClient,null, null);
    }

    @Nullable
    public static String broadcastFeip(ApipClient apipClient, NaSaRpcClient nasaClient,String txSigned) {
        if (txSigned == null) {
            System.out.println("Failed to make tx.");
                return null;
        }

        String txId;
        if(apipClient != null)
            txId = apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        else if(nasaClient != null)
            txId = nasaClient.sendRawTransaction(txSigned);
        else {
            System.out.println("No apipClient or nasaClient to broadcast the tx.");
            return null;
        }
        if(!Hex.isHexString(txId)) {
            System.out.println("Failed to perform FEIP operation: " + txId);
            return null;
        }
        System.out.println("Sent: "+txId);
        return txId;
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

        if(!askIfYes(br,"The master will get your private key and control all your on chain assets and rights. Are you sure to set?"))
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

    /**
     * If the priKey is null, the offLineFid is required. The return value is the unsigned tx in Base64.
     * If the priKey is not null, the offLineFid is ignored. The return value is the txId.
     * If the data is null, and the br is not null, the user will be asked to input the data, else return null.
     * If the data is not null, and the br is not null, the user will be asked to update the data.
     * If the apipClient isn't null, the method will get the cash list and broadcast Tx by apipClient.
     * If the nasaClient isn't null, the method will get the cash list and broadcast Tx by naSaRpcClient.
     * If the esClient isn't null, the method will get the cash list by esClient.
     * If the br isn't null, the user can input or update the data and decide to send the Tx or not.
     * @return The TxId of send Tx or UnsignedTx in Base64.
     */
    public static String protocol(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable ProtocolData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, ProtocolData.class, ProtocolName.PROTOCOL, apipClient, nasaClient, br, ProtocolData.Op.class, ProtocolData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String code(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable CodeData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, CodeData.class, ProtocolName.CODE, apipClient, nasaClient, br, CodeData.Op.class, CodeData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String cid(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable CidData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, CidData.class, ProtocolName.CID, apipClient, nasaClient, br, CidData.Op.class, CidData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String nobody(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable NobodyData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, NobodyData.class, ProtocolName.NOBODY, apipClient, nasaClient, br, null, null);
    }

    /**
     * The method is similar to protocol.
     */
    public static String service(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable ServiceData data, Class<? extends Params> paramsClass,@Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        try {
            // Create ServiceData with op field handling
            ServiceData serviceData = Inputer.createFromUserInput(br, ServiceData.class, "op", ServiceData.OP_FIELDS);
            if(serviceData==null)return null;

            // Create Params without op field handling
            Params params = Inputer.createFromUserInput(br, paramsClass, null, null);
            serviceData.setParams(params);

            Feip feip = Feip.fromProtocolName(ProtocolName.SERVICE);
            feip.setData(serviceData);

            String result = sendFeip(feip, null, priKey, offLineFid, sendToList, apipClient, nasaClient, br);

            if (result == null) return null;
            return result;
            
        } catch (IOException | ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private static String sendFeip(Feip feip, Long cd, byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        System.out.println("OP_RETURN:");
        Shower.printUnderline(20);
        System.out.println(feip.toNiceJson());
        Shower.printUnderline(20);

        if(br !=null && offLineFid ==null && !askIfYes(br,"Send it?")) return null;

        String result = sendFeip(sendToList, cd, priKey, offLineFid, feip, apipClient, nasaClient);
        if(result==null) return null;
        if(br !=null && !Hex.isHex32(result)){
            String str = JsonTools.strToNiceJson(result);
            if(str==null)str = result;
            System.out.println("Unsigned Tx:");
            Shower.printUnderline(20);
            System.out.println(str);
            Shower.printUnderline(20);
            Menu.anyKeyToContinue(br);
        }
        return result;
    }

    /**
     * The method is similar to protocol.
     */
    public static String master(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable MasterData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, MasterData.class, ProtocolName.MASTER, apipClient, nasaClient, br, null, null);
    }

    /**
     * The method is similar to protocol.
     */
    public static String mail(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable MailData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, MailData.class, ProtocolName.MAIL, apipClient, nasaClient, br, MailHandler.MailOp.class, MailData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String statement(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable StatementData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, StatementData.class, ProtocolName.STATEMENT, apipClient, nasaClient, br, null, null);
    }

    /**
     * The method is similar to protocol.
     */
    public static String homepage(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable HomepageData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, HomepageData.class, ProtocolName.HOMEPAGE, apipClient, nasaClient, br, null, null);
    }

    /**
     * The method is similar to protocol.
     */
    public static String noticeFee(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable NoticeFeeData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, NoticeFeeData.class, ProtocolName.NOTICE_FEE, apipClient, nasaClient, br, null, null);
    }

    /**
     * The method is similar to protocol.
     */
    public static String nid(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable NidData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, NidData.class, ProtocolName.NID, apipClient, nasaClient, br, NidData.Op.class, NidData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String contact(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList,
            @Nullable ContactData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, ContactData.class, ProtocolName.CONTACT, apipClient, nasaClient, br, ContactData.Op.class, ContactData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String box(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable BoxData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, BoxData.class, ProtocolName.BOX, apipClient, nasaClient, br, BoxData.Op.class, BoxData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String proof(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable ProofData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, ProofData.class, ProtocolName.PROOF, apipClient, nasaClient, br, ProofData.Op.class, ProofData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String app(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable AppData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, AppData.class, ProtocolName.APP, apipClient, nasaClient, br, AppData.Op.class, AppData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String reputation(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable ReputationData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, ReputationData.class, ProtocolName.REPUTATION, apipClient, nasaClient, br, null, null);
    }

    /**
     * The method is similar to protocol.
     */
    public static String secret(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable SecretData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, SecretData.class, ProtocolName.SECRET, apipClient, nasaClient, br, SecretData.Op.class, SecretData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String team(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList, 
            @Nullable TeamData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, TeamData.class, ProtocolName.TEAM, apipClient, nasaClient, br, TeamData.Op.class, TeamData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String group(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList,
                               Long cd, @Nullable GroupData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, cd, data, GroupData.class, ProtocolName.GROUP, apipClient, nasaClient, br, GroupData.Op.class, GroupData.OP_FIELDS);
    }

    /**
     * The method is similar to protocol.
     */
    public static String token(@Nullable byte[] priKey, @Nullable String offLineFid, @Nullable List<SendTo> sendToList,
            @Nullable TokenData data, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient nasaClient, @Nullable BufferedReader br) {
        return sendFeip(priKey, offLineFid, sendToList, null, data, TokenData.class, ProtocolName.TOKEN, apipClient, nasaClient, br, TokenData.Op.class, TokenData.OP_FIELDS);
    }

    // Add more protocol-specific methods as needed...
}
