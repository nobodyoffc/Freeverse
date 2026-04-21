package clients;

import core.fch.RawTxInfo;
import data.apipData.*;
import data.fcData.*;
import data.fchData.*;
import data.feipData.*;
import managers.WebhookManager;
import server.ApipApi;
import ui.Menu;
import constants.*;
import core.crypto.Decryptor;
import core.crypto.EncryptType;
import core.fch.Inputer;
import config.ApiAccount;
import config.ApiProvider;
import utils.Hex;
import utils.JsonUtils;
import utils.ObjectUtils;
import utils.StringUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.ACTIVE;
import static constants.Values.DESC;
import static data.apipData.FcQuery.PART;
import static server.ApipApi.*;
import static constants.FieldNames.*;
import static constants.FieldNames.FID;
import static constants.FieldNames.SIGN;
import static constants.OpNames.RATE;
import static constants.Strings.*;
import static constants.Values.*;
import static constants.Values.ASC;
import static core.crypto.KeyTools.prikeyToFid;
import static utils.ObjectUtils.objectToList;
import static utils.ObjectUtils.objectToMap;
import static utils.ObjectUtils.objectToClass;

public class ApipClient extends FcClient {

    public static final Integer DEFAULT_SIZE = 200;

    public static final String[] freeAPIs = new String[]{
            "http://127.0.0.1:8081/APIP",
//            "https://apip.cash/APIP",
//            "https://help.cash/APIP",
//            "http://127.0.0.1:8080/APIP"
    };
    public ApipClient() {
    }
    public ApipClient(ApiProvider apiProvider,ApiAccount apiAccount,byte[] symKey){
        super(apiProvider,apiAccount,symKey);
    }

    public List<Multisig> myMultisigs(String fid) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FIDS).addNewValues(fid);
        return multisigSearch(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
    }

    public void checkMaster(String prikeyCipher,byte[] symKey,BufferedReader br) {
        byte[] prikey = new Decryptor().decryptJsonBySymkey(prikeyCipher,symKey).getData();
        if (prikey == null) {
            log.error("Failed to decrypt prikey.");
        }

        String fid = prikeyToFid(prikey);
        Freer cid = freerById(fid);
        if (cid == null) {
            System.out.println("This fid was never seen on chain. Send some fch to it.");
            Menu.anyKeyToContinue(br);
            return;
        }
        if (cid.getMaster() != null) {
            System.out.println("The master of " + fid + " is " + cid.getMaster());
            return;
        }
        if (Inputer.askIfYes(br, "Assign the master for " + fid + "?"))
            FeipClient.setMaster(fid, prikeyCipher, bestHeight, symKey, this, br);
    }

    //OpenAPIs: Ping(Client),GetService(Client),SignIn,SignInEccAPI,Totals

    public Map<String, String> totals(RequestMethod requestMethod, AuthType authType) {
        //Request
        Object data =requestJsonByFcdsl(TOTALS, VER_1, null,authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMap(data,String.class,String.class);
    }
    public Long bestHeight() {
        //Request
        Block block = bestBlock(RequestMethod.POST,AuthType.ENCRYPTED);
        if(block==null)return null;
        return block.getHeight();
    }

    public Block bestBlock(RequestMethod requestMethod, AuthType authType) {
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data = requestByFcdslOther(BEST_BLOCK, VER_1, null, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(BEST_BLOCK, VER_1, null, authType);
        }
        return objectToClass(data,Block.class);
    }

    private Object requestJsonByUrlParams(ApipApi api, String ver, Map<String, String> paramMap, AuthType authType1) {
        String sn = api.getSn();
        String name = api.getName();
        return requestJsonByUrlParams(sn, ver, name, paramMap, authType1);
    }

    private Object requestByFcdslOther(ApipApi api, String ver, Map<String, String> other, AuthType authType1, RequestMethod requestMethod) {
        String sn = api.getSn();
        String name = api.getName();
        return requestByFcdslOther(sn, ver, name, other, authType1,requestMethod);
    }

    private Object requestJsonByFcdsl(ApipApi api, String ver, Fcdsl fcdsl, AuthType authType1, byte[] authKey, RequestMethod requestMethod) {
        String sn = api.getSn();
        String name = api.getName();
        return requestJsonByFcdsl(sn, ver, name, fcdsl, authType1,authKey, requestMethod);
    }


    public Object requestBytes(ApipApi api, String ver, ApipClientEvent.RequestBodyType requestBodyType, @javax.annotation.Nullable Fcdsl fcdsl, @javax.annotation.Nullable Map<String,String> paramMap, AuthType authType, @javax.annotation.Nullable byte[] authKey, RequestMethod httpMethod){
        String sn = api.getSn();
        String name = api.getName();
        return requestBytes(sn,ver,name, requestBodyType,fcdsl,paramMap,authType,authKey,httpMethod);
    }

    public Object requestByIds(ApipApi api, String ver, RequestMethod requestMethod, AuthType authType, String... ids) {
        String sn = api.getSn();
        String name = api.getName();
        return requestByIds(requestMethod,sn,ver,name,authType,ids);
    }

    public ReplyBody general(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType) {
        //Request
        requestJsonByFcdsl(GENERAL, VER_1, fcdsl,authType,sessionKey, requestMethod);
        return apipClientEvent.getResponseBody();
    }

    public String broadcastTx(String txHex, RequestMethod requestMethod, AuthType authType){
        if(txHex==null)return null;
        if(!Hex.isHexString(txHex))txHex = StringUtils.base64ToHex(txHex);
        if(txHex==null)return null;
        Map<String, String> otherMap = new HashMap<>() ;
        otherMap.put(RAW_TX,txHex);
        Object data = requestByFcdslOther(BROADCAST_TX, VER_1, otherMap, authType, requestMethod);
        return (String)data;
    }

    public String decodeTx(String txHex, RequestMethod requestMethod, AuthType authType){
        Map<String, String> otherMap = new HashMap<>() ;
        otherMap.put(RAW_TX,txHex);
        Object data = requestByFcdslOther(DECODE_TX, VER_1, otherMap, authType, requestMethod);
        return data==null ? null: JsonUtils.toNiceJson(data);
    }


    public Map<String, Block>blockByHeights(RequestMethod requestMethod, AuthType authType, String... heights){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(Strings.HEIGHT).addNewValues(heights);

        Object data = requestJsonByFcdsl(BLOCK_BY_HEIGHTS, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToMap(data,String.class,Block.class);
    }

    public String getPubkey(String fid, RequestMethod requestMethod, AuthType authType) {
        Object data = requestByIds(FREER_BY_IDS, VER_1, requestMethod, authType, fid);
        try {
            return data == null ? null : objectToMap(data, String.class, Freer.class).get(fid).getPubkey();
        }catch (Exception e){
            return null;
        }
    }

    public Map<String, Block> blockByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(BLOCK_BY_IDS, VER_1, requestMethod, authType, ids);
        return objectToMap(data,String.class,Block.class);
    }

    public List<Block> blockSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        try {
            Object data = requestJsonByFcdsl(BLOCK_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
            
            if(data == null) {
                System.out.println("Received null response from server");
                return null;
            }
            return objectToList(data,Block.class);
        } catch (Exception e) {
            System.out.println("Error in blockSearch: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * If amount !=null, the non-null cd will be ignored.
     */
    public List<Cash> cashValid(String fid, @Nullable Double amount, @Nullable Long cd, @Nullable Integer outputSize, @Nullable Integer msgSize, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Map<String,String> paramMap = new HashMap<>();
        if(amount!=null)paramMap.put(AMOUNT, String.valueOf(amount));
        if(fid!=null)paramMap.put(FID,fid);
        if(amount==null && cd!=null)paramMap.put(CD, String.valueOf(cd));
        if(outputSize!=null)paramMap.put(OUTPUT_SIZE, String.valueOf(outputSize));
        if(msgSize!=null)paramMap.put(MSG_SIZE, String.valueOf(msgSize));  
        fcdsl.addOther(paramMap);
        return cashValid(fcdsl, requestMethod,authType);
    }

    public Map<String, Long> balanceByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(BALANCE_BY_IDS, VER_1, requestMethod, authType, ids);
        return objectToMap(data,String.class,Long.class);
    }

    public List<Cash> cashValid(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(CASH_VALID, VER_1, fcdsl,authType, sessionKey, requestMethod);
        return objectToList(data, Cash.class);
    }

    public Map<String, Cash> cashByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(CASH_BY_IDS, VER_1, requestMethod, authType, ids);
        return objectToMap(data,String.class, Cash.class);
    }

    public List<Cash> cashSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(CASH_SEARCH, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, Cash.class);
    }
    public List<Utxo> getUtxo(String id, double amount, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FID).addNewValues(id);
        Fcdsl.setSingleOtherMap(fcdsl, AMOUNT, String.valueOf(amount));

        Object data = requestJsonByFcdsl(GET_UTXO, VER_1, fcdsl,authType, sessionKey, requestMethod);
        return objectToList(data,Utxo.class);
    }

    public Map<String, OpReturn> opReturnByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(OP_RETURN_BY_IDS, VER_1, requestMethod, authType, ids);
        return objectToMap(data,String.class,OpReturn.class);
    }

    public List<OpReturn> opReturnSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(OP_RETURN_SEARCH, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,OpReturn.class);
    }

    public Map<String, Multisig> multisigByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(MULTISIG_BY_IDS, VER_1, requestMethod, authType, ids);
        return objectToMap(data,String.class, Multisig.class);
    }
    public List<Multisig> multisigSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(MULTISIG_SEARCH, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, Multisig.class);
    }

    public Map<String, P2SH> p2shByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(P2SH_BY_IDS, VER_1, requestMethod, authType, ids);
        return objectToMap(data,String.class, P2SH.class);
    }
    public List<P2SH> p2shSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(P2SH_SEARCH, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, P2SH.class);
    }

    public Map<String, Tx> txByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(TX_BY_IDS, VER_1, requestMethod, authType, ids);
        return objectToMap(data,String.class,Tx.class);
    }

    public List<Tx> txSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(TX_SEARCH, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,Tx.class);
    }

    public List<FidTxMask>  txByFid(String fid, int size, String[] last, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl =txByFidQuery(fid, size, last);
        Object data = requestJsonByFcdsl(TX_BY_FID, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, FidTxMask.class);
    }
    public static Fcdsl txByFidQuery(String fid, int size, @javax.annotation.Nullable String[] last){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery()
                .addNewTerms()
                .addNewFields("inMarks.owner","outMarks.owner")
                .addNewValues(fid);
        if(last!=null){
            fcdsl.addAfter(java.util.List.of(last));
        }
        if(size!=0)
            fcdsl.addSize(size);
        return fcdsl;
    }
    public FchChainInfo chainInfo() {
        return chainInfo(null,RequestMethod.GET,AuthType.FREE);
    }

    public FchChainInfo chainInfo(Long height, RequestMethod requestMethod, AuthType authType) {
        Map<String,String> params=null;
        if(height!=null){
            params = new HashMap<>();
            params.put(FieldNames.HEIGHT, String.valueOf(height));
        }
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data = requestByFcdslOther(CHAIN_INFO, VER_1, params, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(CHAIN_INFO, VER_1, params,AuthType.FREE);
        }

        return objectToClass(data,FchChainInfo.class);
    }

    public Map<Long,Long> blockTimeHistory(Long startTime, Long endTime, Integer count, RequestMethod requestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);

        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data =requestByFcdslOther(BLOCK_TIME_HISTORY, VER_1, params, authType, requestMethod);
        }else {
            data =requestJsonByUrlParams(BLOCK_TIME_HISTORY, VER_1, params,AuthType.FREE);
        }

        return ObjectUtils.objectToMap(data,Long.class,Long.class);
    }

    public Map<Long,String> difficultyHistory(Long startTime, Long endTime, Integer count, RequestMethod requestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data = requestByFcdslOther(DIFFICULTY_HISTORY, VER_1, params, authType, requestMethod);
        }else {
            data =requestJsonByUrlParams(DIFFICULTY_HISTORY, VER_1, params,AuthType.FREE);
        }

        return ObjectUtils.objectToMap(data,Long.class,String.class);
    }

    @NotNull
    private static Map<String, String> makeHistoryParams(Long startTime, Long endTime, Integer count) {
        if(count ==null|| count ==0) count =Constants.DefaultSize;

        Map<String, String> params = new HashMap<>();
        if(startTime !=null)params.put(FieldNames.START_TIME, String.valueOf(startTime));
        if(endTime !=null)params.put(FieldNames.END_TIME, String.valueOf(endTime));
        params.put(FieldNames.COUNT, String.valueOf(count));
        return params;
    }

    public Map<Long,String> hashRateHistory(Long startTime, Long endTime, Integer count, RequestMethod requestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data =requestByFcdslOther(HASH_RATE_HISTORY, VER_1, params, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(HASH_RATE_HISTORY, VER_1, params,AuthType.FREE);
        }
        return ObjectUtils.objectToMap(data,Long.class,String.class);
    }

    //Identity APIs
    public Map<String, Freer> freerByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(FREER_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class, Freer.class);
    }

    public Map<String, String> cidByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(CID_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class, String.class);
    }

    public Map<String, String> cidAvatarByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(CID_AVATAR_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class, String.class);
    }

    public Freer freerById(String id) {
        Map<String, Freer> map = freerByIds(RequestMethod.POST, AuthType.ENCRYPTED, id);
        try {
            return map.get(id);
        }catch (Exception e){
            log.error("Failed to get Cid info: {}",e.getMessage());
            return null;
        }
    }
    public Freer getFidCid(String id) {
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(ID,id);
        Object data = requestJsonByUrlParams(GET_FID_CID, VER_1, paramMap,AuthType.FREE);
        return objectToClass(data, Freer.class);
    }

    public Map<String, String> getFidCidMap(List<String> fidList) {
        fidList.remove(null);
        Map<String, Freer> cidInfoMap = this.freerByIds(RequestMethod.POST, AuthType.ENCRYPTED, fidList.toArray(new String[0]));
        if (cidInfoMap == null) return null;
        Map<String, String> fidCidMap = new HashMap<>();
        for (String fid:cidInfoMap.keySet()) {
            Freer cid = cidInfoMap.get(fid);
            if(cid !=null)fidCidMap.put(fid, cid.getCid());
        }
        return fidCidMap;
    }

    //TODO untested
    public List <Freer> searchCidList(BufferedReader br, boolean choose){
        String part = Inputer.inputString(br,"Input the FID, CID, used CIDs or a part of any one of them:");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(ID,FieldNames.USED_CIDS).addNewValue(part);
        List<Freer> result = freerSearch(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null || result.isEmpty())return null;
        return Freer.showCidList("Chose CIDs",result,20,choose,br);
    }

    public List<Freer> freerSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(FREER_SEARCH, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, Freer.class);
    }
    public List<FreerHist> freerHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "3");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(FREER_HISTORY, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, FreerHist.class);
    }

    public Map<String, String[]> fidCidSeek(String searchStr, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(ID,CID).addNewValue(searchStr);
        Object data = requestJsonByFcdsl(FID_CID_SEEK, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToMap(data,String.class,String[].class);
    }

    public Map<String, String[]> fidCidSeek(String fid_or_cid){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(ID,CID).addNewValue(fid_or_cid);
        Map<String,String> map = new HashMap<>();
        map.put(PART,fid_or_cid);
        Object data = requestJsonByUrlParams(FID_CID_SEEK, VER_1, map, AuthType.FREE);
        return objectToMap(data,String.class,String[].class);
    }

    public Map<String, Nobody> nobodyByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(NOBODY_BY_IDS, VER_1, requestMethod, authType, ids);
        return objectToMap(data,String.class,Nobody.class);
    }

    public List<Nobody> nobodySearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(NOBODY_SEARCH, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,Nobody.class);
    }

    /**
     * 批量检查是否nobody
     */
    public Map<String, Boolean> checkNobodies(RequestMethod requestMethod, AuthType authType,  String... ids) {
        Object data = requestByIds(CHECK_NOBODIES, VER_1, requestMethod, authType, ids);
        return objectToMap(data,String.class,Boolean.class);
    }

    public List<FreerHist> homeHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "9");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(HOME_HISTORY, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, FreerHist.class);
    }

    public List<FreerHist> noticeFeeHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "10");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(NOTICE_FEE_HISTORY, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, FreerHist.class);
    }

    public List<FreerHist> reputationHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(REPUTATION_HISTORY, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, FreerHist.class);
    }

    public Map<String, String> avatars(String[] fids, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(fids);
        Object data = requestJsonByFcdsl(AVATARS, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToMap(data,String.class,String.class);
    }

    public byte[] getAvatar(String fid){
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(FID,fid);
        Object data = requestBytes(GET_AVATAR, VER_1, ApipClientEvent.RequestBodyType.NONE,null,paramMap, AuthType.FREE,null, RequestMethod.GET);
        return (byte[])data;
    }


    // Construct
    public Map<String, Protocol> protocolByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(PROTOCOL_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Protocol.class);
    }

    public Protocol protocolById(String id){
        Map<String, Protocol> map = protocolByIds(RequestMethod.POST,AuthType.ENCRYPTED, id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Protocol> protocolSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(PROTOCOL_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Protocol.class);
    }


    public List<ProtocolHistory> protocolOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(PROTOCOL_OP_HISTORY, VER_1, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProtocolHistory.class);
    }


    public List<ProtocolHistory> protocolRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(PROTOCOL_RATE_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProtocolHistory.class);
    }

    public Map<String, Code> codeByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(CODE_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Code.class);
    }

    public Code codeById(String id){
        Map<String, Code> map = codeByIds(RequestMethod.POST,AuthType.ENCRYPTED, id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Code> codeSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(CODE_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Code.class);
    }

    public List<CodeHistory> codeOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(CODE_OP_HISTORY, VER_1, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,CodeHistory.class);
    }

    public List<CodeHistory> codeRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(CODE_RATE_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,CodeHistory.class);
    }

    public Map<String, Service> serviceByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(SERVICE_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Service.class);
    }

    public List<Service> serviceSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SERVICE_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Service.class);
    }

    public List<ServiceHistory> serviceOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SERVICE_OP_HISTORY, VER_1, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ServiceHistory.class);
    }

    public List<ServiceHistory> serviceRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SERVICE_RATE_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ServiceHistory.class);
    }

    public List<Service> getServiceListByType(String type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.TYPE).addNewValues(type);
        fcdsl.addNewExcept().addNewTerms().addNewFields(ACTIVE).addNewValues(FALSE);
        serviceList = serviceSearch(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        return serviceList;
    }

    public List<Service> getServiceListByOwnerAndType(String owner, @Nullable ServiceType type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(owner);
        fcdsl.addNewExcept().addNewTerms().addNewFields(CLOSED).addNewValues(TRUE);
        if(type!=null)fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.TYPE).addNewValues(type.name());
        serviceList = serviceSearch(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        return serviceList;
    }

    public List<Service> getServiceListByDealerAndType(String dealer, @Nullable ServiceType type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(DEALER).addNewValues(dealer);
        fcdsl.addNewExcept().addNewTerms().addNewFields(CLOSED).addNewValues(TRUE);
        if(type!=null)fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.TYPE).addNewValues(type.toString());
        serviceList = serviceSearch(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        return serviceList;
    }

    public Service serviceById(String id){
        Map<String, Service> map = serviceByIds(RequestMethod.POST,AuthType.ENCRYPTED, id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public Map<String, App> appByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(APP_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,App.class);
    }

    public App appById(String id){
        Map<String, App> map = appByIds(RequestMethod.POST,AuthType.ENCRYPTED, id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<App> appSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(APP_SEARCH, VER_1, fcdsl, AuthType.ENCRYPTED, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToList(data,App.class);
    }


    public List<AppHistory> appOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(APP_OP_HISTORY, VER_1, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,AppHistory.class);
    }


    public List<AppHistory> appRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(APP_RATE_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,AppHistory.class);
    }
//Organize
    public Map<String, Square> squareByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(SQUARE_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class, Square.class);
    }
    public List<Square> squareSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SQUARE_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data, Square.class);
    }

    public List<SquareHistory> squareOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SQUARE_OP_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,SquareHistory.class);
    }
    public Map<String, String[]> squareMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(SQUARE_MEMBERS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,String[].class);
    }

    public List<Square> mySquares(String fid, Long sinceHeight, Integer size, @NotNull final List<String> last, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        if(sinceHeight!=null)
            fcdsl.getQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(String.valueOf(sinceHeight));
        if(size!=null)fcdsl.addSize(size);
        if(!last.isEmpty())fcdsl.addAfter(last);
        Object data = requestJsonByFcdsl(MY_SQUARES, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        List<String> newLast = this.getFcClientEvent().getResponseBody().getLast();
        last.clear();
        last.addAll(newLast);
        return objectToList(data, Square.class);
    }

    public Map<String, Team> teamByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(TEAM_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Team.class);
    }
    public List<Team> teamSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(TEAM_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Team.class);
    }

    public List<TeamHistory> teamOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(TEAM_OP_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }

    public List<TeamHistory> teamRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(TEAM_RATE_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }
    public Map<String, String[]> teamMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(TEAM_MEMBERS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,String[].class);
    }

    public Map<String, String[]> teamExMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(TEAM_EX_MEMBERS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,String[].class);
    }
    public Map<String,Team> teamOtherPersons(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(TEAM_OTHER_PERSONS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Team.class);
    }
    public List<Team> myTeams(String fid, Long sinceHeight, Integer size, @NotNull final List<String> last, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        if(sinceHeight!=null)
            fcdsl.getQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(String.valueOf(sinceHeight));
        if(size!=null)fcdsl.addSize(size);
        if(!last.isEmpty())fcdsl.addAfter(last);
        
        Object data = requestJsonByFcdsl(MY_TEAMS, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        List<String> newLast = this.getFcClientEvent().getResponseBody().getLast();
        last.clear();
        last.addAll(newLast);
        return objectToList(data,Team.class);
    }

    public Map<String, Box> boxByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(BOX_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Box.class);
    }
    public List<Box> boxSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(BOX_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Box.class);
    }

    public List<BoxHistory> boxHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(BOX_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,BoxHistory.class);
    }

    public Map<String, Contact> contactByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(CONTACT_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Contact.class);
    }
    public List<Contact> contactSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(CONTACT_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Contact.class);
    }

    public List<Contact> contactDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(CONTACTS_DELETED, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Contact.class);
    }

    public List<Contact> freshContactSinceHeight(String myFid, Long lastHeight, Integer size, final List<String> last, Boolean active) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity(CONTACT);
        String heightStr = String.valueOf(lastHeight);
        fcdsl.addNewQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(heightStr);

        fcdsl.getQuery().addNewTerms().addNewFields(OWNER).addNewValues(myFid);
        if(active!=null) {
            fcdsl.getQuery().addNewMatch().addNewFields(ACTIVE).addNewValue(String.valueOf(active));
        }
        fcdsl.addSize(size);

        fcdsl.addSort(LAST_HEIGHT, DESC).addSort(ID,ASC);

        if(last!=null && !last.isEmpty())fcdsl.addAfter(last);

        ReplyBody result = this.general(fcdsl,RequestMethod.POST, AuthType.ENCRYPTED);

        Object data = result.getData();
        return objectToList(data, Contact.class);
    }

    public Map<String, Secret> secretByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(SECRET_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Secret.class);
    }
    public List<Secret> secretSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SECRET_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Secret.class);
    }

    public List<Secret> secretDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SECRETS_DELETED, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Secret.class);
    }

//    public List<Secret> freshSecretSinceHeight(String myFid, Long lastHeight, Integer size, List<String> last, Boolean active) {
//        Fcdsl fcdsl = new Fcdsl();
//        fcdsl.setIndex(SECRET);
//        String heightStr = String.valueOf(lastHeight);
//        fcdsl.addNewQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(heightStr);
//
//        fcdsl.getQuery().addNewTerms().addNewFields(OWNER).addNewValues(myFid);
//        if(active!=null) {
//            fcdsl.getQuery().addNewMatch().addNewFields(ACTIVE).addNewValue(String.valueOf(active));
//        }
//        fcdsl.addSize(size);
//
//        fcdsl.addSort(LAST_HEIGHT,Strings.ASC).addSort(Secret_Id,ASC);
//
//        if(last!=null && !last.isEmpty())fcdsl.addAfter(last);
//
//        ReplyBody result = this.general(fcdsl,RequestMethod.POST, AuthType.FC_SIGN_BODY);
//
//        Object data = result.getData();
//        return objectToList(data, Secret.class);
//    }


    public <T> List<T> loadSinceHeight(String index,String idField,String sortField,String termField,String myFid, Long lastHeight, Integer size, List<String> last, Boolean active,Class<T> tClass) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity(index);
        String heightStr = String.valueOf(lastHeight);
        fcdsl.addNewQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(heightStr);

        fcdsl.getQuery().addNewTerms().addNewFields(termField).addNewValues(myFid);
        if(active!=null) {
            fcdsl.getQuery().addNewMatch().addNewFields(ACTIVE).addNewValue(String.valueOf(active));
        }
        fcdsl.addSize(size);

        fcdsl.addSort(sortField, ASC).addSort(idField,ASC);

        if(last!=null && !last.isEmpty())fcdsl.addAfter(last);

        ReplyBody result = this.general(fcdsl,RequestMethod.POST, AuthType.ENCRYPTED);

        Object data = result.getData();
        return objectToList(data, tClass);
    }

    public Map<String, Mail> mailByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(MAIL_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Mail.class);
    }
    public List<Mail> mailSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(MAIL_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }

    public List<Mail> mailDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(MAILS_DELETED, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }
    public List<Mail> mailThread(String fidA, String fidB, Long startTime, Long endTime, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(SENDER,RECIPIENT).addNewValues(fidA);
        if(startTime!=null||endTime!=null){
            Range range = fcdsl.getQuery().addNewRange();
            range.addNewFields(BIRTH_TIME);
            if(startTime!=null)range.addGte(String.valueOf(startTime/1000));
            if(endTime!=null)range.addLt(String.valueOf(endTime/1000));
        }
        fcdsl.addNewFilter().addNewTerms().addNewFields(SENDER,RECIPIENT).addNewValues(fidB);
        Object data = requestJsonByFcdsl(MAIL_THREAD, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }

    public List<Mail> freshMailSinceHeight(String myFid, long lastHeight, Integer defaultRequestSize, List<String> last, Boolean active) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity(MAIL);
        String heightStr = String.valueOf(lastHeight);
        fcdsl.addNewQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(heightStr);

        fcdsl.getQuery().addNewTerms().addNewFields(SENDER,RECIPIENT).addNewValues(myFid);
        if(active!=null) {
            fcdsl.getQuery().addNewMatch().addNewFields(ACTIVE).addNewValue(String.valueOf(active));
        }
        fcdsl.addSize(defaultRequestSize);

        fcdsl.addSort(LAST_HEIGHT, DESC).addSort(ID,ASC);

        if(last!=null && !last.isEmpty())fcdsl.addAfter(last);
//        return this.mailSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);


        ReplyBody result = this.general(fcdsl,RequestMethod.POST, AuthType.ENCRYPTED);

        Object data = result.getData();
        return objectToList(data, Mail.class);
    }

    public Map<String, Proof> proofByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(PROOF_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Proof.class);
    }
    public List<Proof> proofSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(PROOF_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Proof.class);
    }

    public List<ProofHistory> proofHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(PROOF_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProofHistory.class);
    }


    public Map<String, Statement> statementByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(STATEMENT_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Statement.class);
    }
    public List<Statement> statementSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(STATEMENT_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Statement.class);
    }
    public List<Nid> nidSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(NID_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Nid.class);
    }

    public Map<String, String> oidByNids(List<String> nids, RequestMethod requestMethod, AuthType authType) {
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.NIDS,JsonUtils.toJson(nids));
        Object data = requestJsonByFcdsl(OID_BY_NIDS, VER_1, fcdsl, authType, sessionKey, requestMethod);
        return ObjectUtils.objectToMap(data,String.class,String.class);
    }

    public Map<String, Token> tokenByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(TOKEN_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Token.class);
    }
    public List<Token> tokenSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(TOKEN_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Token.class);
    }

    public List<TokenHistory> tokenHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(TOKEN_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TokenHistory.class);
    }

    public List<Token> myTokens(String fid, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.FID).addNewValues(fid);
        Object data = requestJsonByFcdsl(MY_TOKENS, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data, Token.class);
    }

    public Map<String, TokenHolder> tokenHoldersByIds(RequestMethod requestMethod, AuthType authType, String... tokenIds){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(Token_Id).addNewValues(tokenIds);
        Object data = requestJsonByFcdsl(TOKEN_HOLDERS_BY_IDS, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMap(data,String.class,TokenHolder.class);
    }

    public List<TokenHolder> tokenHolderSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(TOKEN_HOLDER_SEARCH, VER_1, fcdsl,authType,sessionKey, requestMethod);
        return ObjectUtils.objectToList(data,TokenHolder.class);
    }
    public List<UnconfirmedInfo> unconfirmed(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(ApipApi.UNCONFIRMED, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToList(data,UnconfirmedInfo.class);
    }

    public Map<String, List<Cash>> unconfirmedCaches(RequestMethod requestMethod, AuthType authType, String... ids){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(ids);
        Object data = requestJsonByFcdsl(UNCONFIRMED_CASHES, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMapWithListValues(data, String.class, Cash.class);
    }

    public Double feeRate(RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(FEE_RATE, VER_1, null, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return (Double)data;
    }


    public Map<String, String> addresses(String addrOrPubkey, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.ADDR_OR_PUB_KEY, addrOrPubkey);
        Object data = requestJsonByFcdsl(ADDRESSES, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMap(data,String.class,String.class);
    }
    public String encrypt(EncryptType encryptType, String message, String key, String fid, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        EncryptIn encryptIn = new EncryptIn();
        encryptIn.setType(encryptType);
        encryptIn.setMsg(message);
        switch (encryptType){
            case Symkey -> {
                encryptIn.setSymkey(key);
                encryptIn.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
            }
            case Password -> {
                encryptIn.setPassword(key);
                encryptIn.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
            }
            case AsyOneWay -> {
                if(key!=null) {
                    encryptIn.setPubkey(key);
                }else if(fid!=null){
                    encryptIn.setFid(fid);
                }
                encryptIn.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            }
            default -> {
                return null;
            }
        }
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.ENCRYPT_INPUT, JsonUtils.toJson(encryptIn));
        Object data = requestJsonByFcdsl(ENCRYPT, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return objectToClass(data,String.class);
    }
    public boolean verify(String signature, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, SIGN, signature);
        Object data = requestJsonByFcdsl(VERIFY, VER_1, fcdsl, authType,sessionKey, requestMethod);
        if(data==null) return false;
        return (boolean) data;
    }

    public String sha256(String text, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, text);
        Object data = requestJsonByFcdsl(SHA_256, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }
    public String sha256x2(String text, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, text);
        Object data = requestJsonByFcdsl(SHA_256_X_2, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }
    public String sha256Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SHA_256_HEX, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String sha256x2Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SHA_256_X_2_HEX, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String ripemd160Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(RIPEMD_160_HEX, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String KeccakSha3Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(KECCAK_SHA_3_HEX, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String hexToBase58(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(HEX_TO_BASE_58, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String checkSum4Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(CHECK_SUM_4_HEX, VER_1, fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String offLineTx(String fromFid, List<Cash> sendToList, String msg, Long cd, String ver, RequestMethod requestMethod, AuthType authType){
        if(requestMethod.equals(RequestMethod.POST)) {
            Fcdsl fcdsl = new Fcdsl();
            RawTxInfo rawTxInfo = new RawTxInfo();
            rawTxInfo.setSender(fromFid);
            rawTxInfo.setOpReturn(msg);
            rawTxInfo.setOutputs(sendToList);
            rawTxInfo.setCd(cd);
            rawTxInfo.setVer(ver);
            Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.DATA_FOR_OFF_LINE_TX, JsonUtils.toJson(rawTxInfo));
            Object data = requestJsonByFcdsl(OFF_LINE_TX, VER_1, fcdsl, authType, sessionKey, requestMethod);
            return (String) data;
        }

        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(VER,ver);
        paramMap.put("fromFid",fromFid);
        List<String> toList = new ArrayList<>();
        List<String> amountList = new ArrayList<>();

        if(sendToList!=null && !sendToList.isEmpty()) {
            for (Cash sendTo : sendToList) {
                toList.add(sendTo.getOwner());
                amountList.add(String.valueOf(sendTo.getAmount()));
            }
            paramMap.put("toFids", String.join(",", toList));
            paramMap.put("amounts", String.join(",", amountList));
        }
        if(msg!=null)paramMap.put(MESSAGE,msg);
        if(cd!=0)paramMap.put(CD,String.valueOf(cd));

        Object data = requestJsonByUrlParams(OFF_LINE_TX, VER_1, paramMap, authType);
        return (String) data;
    }

    public String circulating(){
        Object data = requestBase(CIRCULATING.getName(), ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String totalSupply(){
        Object data = requestBase(TOTAL_SUPPLY.getName(), ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String richlist(){
        Object data = requestBase(RICHLIST.getName(), ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String freecashInfo(){
        Object data = requestBase(FREECASH_INFO.getName(), ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }
//
//    public String swapRegister(String sid){
//        fcClientEvent = SwapHallAPIs.swapRegisterPost(apiAccount.getApiUrl(), sid,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String)data;
//    }
//
//    public List<String> swapUpdate(Map<String, Object> uploadMap){
//        fcClientEvent = SwapHallAPIs.swapUpdatePost(apiAccount.getApiUrl(), uploadMap,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringList(data);
//    }
//    public SwapStateData swapState(String sid, String[] last){
//        fcClientEvent = SwapHallAPIs.getSwapState(apiAccount.getApiUrl(), sid);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        try{
//            return gson.fromJson(gson.toJson(data),SwapStateData.class);
//        }catch (Exception e){
//            fcClientEvent.setMessage(fcClientEvent.getMessage()+(String)data);
//            return null;
//        }
//    }
//
//    public SwapLpData swapLp(String sid, String[] last){
//        fcClientEvent = SwapHallAPIs.getSwapLp(apiAccount.getApiUrl(), sid);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        try{
//            return gson.fromJson(gson.toJson(data),SwapLpData.class);
//        }catch (Exception e){
//            fcClientEvent.setMessage(fcClientEvent.getMessage()+(String)data);
//            return null;
//        }
//    }
//
//    public List<SwapAffair> swapFinished(String sid, String[] last){
//        fcClientEvent = SwapHallAPIs.getSwapFinished(apiAccount.getApiUrl(), sid,last);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getSwapAffairList(data);
//    }
//
//    public List<SwapAffair> swapPending(String sid){
//        fcClientEvent = SwapHallAPIs.getSwapPending(apiAccount.getApiUrl(), sid);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getSwapAffairList(data);
//    }
//
//    public List<SwapPriceData> swapPrices(String sid, String gTick, String mTick, List<String> last){
//        fcClientEvent = SwapHallAPIs.getSwapPrice(apiAccount.getApiUrl(), sid,gTick,mTick,last);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getSwapPriceDataList(data);
//    }
//    public void setClientData(ApipClientEvent clientData) {
//        this.fcClientEvent = clientData;
//    }

//    public Fcdsl getFcdsl() {
//        return fcdsl;
//    }
//
//    public void setFcdsl(Fcdsl fcdsl) {
//        this.fcdsl = fcdsl;
//    }
    //Webhook APIs
    public Map<String, String> checkSubscription(String method, String endpoint) {
        WebhookManager.WebhookRequestBody webhookRequestBody = new WebhookManager.WebhookRequestBody();
        webhookRequestBody.setEndpoint(endpoint);
        webhookRequestBody.setOp(CHECK);
        if(method.equals(HOOK_NEW_CASH_BY_FIDS.getName()))
            return newCashListByIds(webhookRequestBody);
        else if(method.equals(NEW_OP_RETURN_HOOK_BY_FIDS))
            return newOpReturnListByIds(webhookRequestBody);
        return null;
    }
    //
    public String subscribeWebhook(String method, Object data, String endpoint) {
        WebhookManager.WebhookRequestBody webhookRequestBody = new WebhookManager.WebhookRequestBody();
        webhookRequestBody.setEndpoint(endpoint);
        webhookRequestBody.setMethod(method);
//        webhookRequestBody.setUserId(apiAccount.getUserId());
        webhookRequestBody.setOp(SUBSCRIBE);
        webhookRequestBody.setData(data);
        Map<String, String> dataMap=null;
        if(method.equals(HOOK_NEW_CASH_BY_FIDS.getName()))
            dataMap = newCashListByIds(webhookRequestBody);
        else if(method.equals(NEW_OP_RETURN_HOOK_BY_FIDS))
            dataMap=newOpReturnListByIds(webhookRequestBody);

        if(dataMap==null)return null;
        return dataMap.get(HOOK_USER_ID);
    }

    public Map<String, String> newCashListByIds(WebhookManager.WebhookRequestBody webhookRequestBody){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.WEBHOOK_REQUEST_BODY, JsonUtils.toJson(webhookRequestBody));
        Object data = requestJsonByFcdsl(ApipApi.HOOK_NEW_CASH_BY_FIDS, VER_1, fcdsl, AuthType.ENCRYPTED, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToMap(data,String.class,String.class);
    }

    public Map<String, String> newOpReturnListByIds(WebhookManager.WebhookRequestBody webhookRequestBody){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.WEBHOOK_REQUEST_BODY, JsonUtils.toJson(webhookRequestBody));
        Object data = requestJsonByFcdsl(NEW_OP_RETURN_HOOK_BY_FIDS, VER_1, fcdsl, AuthType.ENCRYPTED, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToMap(data,String.class,String.class);
    }
    public Freer searchCidOrFid(BufferedReader br) {
        String choose = chooseFid(br);
        if (choose == null) return null;
        return freerById(choose);
    }

    public String chooseFid(BufferedReader br) {
        while (true) {
            String input = Inputer.inputString(br, "Input CID, FID or a part of them:");
            Map<String, String[]> result = fidCidSeek(input, RequestMethod.POST, AuthType.ENCRYPTED);
            if (result == null || result.isEmpty()) return null;
            Object chosen = Inputer.chooseOneFromMapArray(result, false, false, "Choose one:", br);
            if (chosen == null) {
                if (Inputer.askIfYes(br, "Try again?")) continue;
                else return null;
            } else {
                return chosen.toString();
            }
        }
    }
    @Nullable
    public static List<?> simpleSearch(ApipClient apipClient, Class<?> dataClass, String indexName, String searchFieldName, String searchValue, String sinceFieldName, long sinceHeight, List<Sort> sortList, int size, final List<String> last) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addEntity(indexName);
        if(searchFieldName!=null)fcdsl.addNewQuery().addNewTerms().addNewFields(searchFieldName).addNewValues(searchValue);
        if(sinceFieldName!=null)fcdsl.getQuery().addNewRange().addNewFields(sinceFieldName).addGt(String.valueOf(sinceHeight));
        for(Sort sort: sortList)fcdsl.addSort(sort.getField(),sort.getOrder());
        if(last !=null)fcdsl.addAfter(last).addSize(size);
    
        ReplyBody result = apipClient.general(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null || result.getData()==null) return null;
        if(last!=null){
            last.clear();
            last.addAll(result.getLast());
        }
        return ObjectUtils.objectToList(result.getData(), dataClass);
    }
    public void updateUnconfirmedValidCash(List<Cash> meetList, String fid) {
        if(meetList==null || meetList.isEmpty())return;
        List<Cash> addingList = new ArrayList<>();
        List<String> removingIdList = new ArrayList<>();
        Map<String, List<Cash>> result = unconfirmedCaches(RequestMethod.POST, AuthType.ENCRYPTED,fid);
        if(result!=null){
            List<Cash> unconfirmedCashList = result.get(fid);
            if(unconfirmedCashList!=null){
                for(Cash cash : unconfirmedCashList){
                    if(cash.isValid() && fid!=null){
                        addingList.add(cash);
                    }else{
                        removingIdList.add(cash.getId());
                    }
                }
            }
            if(!addingList.isEmpty()&& fid!=null){
                meetList.addAll(addingList);
            }
            if(!removingIdList.isEmpty()){
                for(String id : removingIdList){
                    meetList.removeIf(cash -> cash.getId().equals(id));
                }
            }
        }
    }
    
    @Nullable
    public <T> Map<String,T> loadOnChainItemByIds(String index, Class<T> tClass, List<String> ids) {
        if (ids == null || ids.isEmpty()) return null;
        
        // Create Fcdsl object
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity(index);
        fcdsl.addIds(ids);

        // Make request
        ReplyBody result = general(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null || result.getData()==null) return null;
        // Convert and return result
        List<T> dataList = objectToList(result.getData(),tClass);
        Map<String,T> resultMap = new HashMap<>();
        for(T data : dataList){
            try {
                java.lang.reflect.Field field = data.getClass().getDeclaredField(ID);
                field.setAccessible(true);
                String id = field.get(data).toString();
                resultMap.put(id, data);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                System.out.println("Error accessing the ID field: " + e.getMessage());
            }
        }
        return resultMap;
    }

    public Map<String, Text> textByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(TEXT_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Text.class);
    }

    public Text textById(String id){
        Map<String, Text> map = textByIds(RequestMethod.POST,AuthType.ENCRYPTED, id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Text> textSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(TEXT_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Text.class);
    }

    public List<TextHistory> textOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(TEXT_OP_HISTORY, VER_1, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TextHistory.class);
    }

    public List<TextHistory> textRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(TEXT_RATE_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TextHistory.class);
    }

    public Map<String, Remark> remarkByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(REMARK_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Remark.class);
    }

    public Remark remarkById(String id){
        Map<String, Remark> map = remarkByIds(RequestMethod.POST,AuthType.ENCRYPTED, id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Remark> remarkSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(REMARK_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Remark.class);
    }

    public List<RemarkHistory> remarkOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(REMARK_OP_HISTORY, VER_1, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,RemarkHistory.class);
    }

    public List<RemarkHistory> remarkRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(REMARK_RATE_HISTORY, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,RemarkHistory.class);
    }

    // News APIs
    public Map<String, News> newsByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(NEWS_BY_IDS, VER_1, requestMethod, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,News.class);
    }

    public News newsById(String id){
        Map<String, News> map = newsByIds(RequestMethod.POST,AuthType.ENCRYPTED, id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<News> newsSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(NEWS_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,News.class);
    }

    // Entity APIs - Generic entity access
    /**
     * Get entities by IDs with specified entity class
     * @param entityName the entity/index name (e.g., "cash", "block", "freer")
     * @param entityClass the class to deserialize to
     * @param requestMethod HTTP request method
     * @param authType authentication type
     * @param ids the entity IDs to fetch
     * @return Map of ID to entity
     */
    public <T> Map<String, T> entityByIds(String entityName, Class<T> entityClass, RequestMethod requestMethod, AuthType authType, String... ids) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity(entityName);
        fcdsl.addIds(ids);
        Object data = requestJsonByFcdsl(ENTITY_BY_IDS, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data == null) return null;
        return objectToMap(data, String.class, entityClass);
    }

    /**
     * Get entities by IDs, returning raw Object map
     * @param entityName the entity/index name
     * @param requestMethod HTTP request method
     * @param authType authentication type
     * @param ids the entity IDs to fetch
     * @return Map of ID to Object
     */
    public Map<String, Object> entityByIds(String entityName, RequestMethod requestMethod, AuthType authType, String... ids) {
        return entityByIds(entityName, Object.class, requestMethod, authType, ids);
    }

    /**
     * Search entities with specified entity class
     * @param entityName the entity/index name (e.g., "cash", "block", "freer")
     * @param entityClass the class to deserialize to
     * @param fcdsl the search criteria
     * @param requestMethod HTTP request method
     * @param authType authentication type
     * @return List of entities
     */
    public <T> List<T> entitySearch(String entityName, Class<T> entityClass, Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType) {
        if (fcdsl == null) fcdsl = new Fcdsl();
        fcdsl.setEntity(entityName);
        
        // Add default sorts if sort is null or empty
        if (fcdsl.getSort() == null || fcdsl.getSort().isEmpty()) {
            Map<String, String> defaultSorts = EntityProperty.getDefaultSortsByName(entityName);
            if (defaultSorts != null) {
                for (Map.Entry<String, String> entry : defaultSorts.entrySet()) {
                    fcdsl.addSort(entry.getKey(), entry.getValue());
                }
            }
        }
        
        Object data = requestJsonByFcdsl(ENTITY_SEARCH, VER_1, fcdsl, authType, sessionKey, requestMethod);
        if(data == null) return null;
        return objectToList(data, entityClass);
    }

    /**
     * Search entities, returning raw Object list
     * @param entityName the entity/index name
     * @param fcdsl the search criteria
     * @param requestMethod HTTP request method
     * @param authType authentication type
     * @return List of Objects
     */
    public List<Object> entitySearch(String entityName, Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType) {
        return entitySearch(entityName, Object.class, fcdsl, requestMethod, authType);
    }
}
