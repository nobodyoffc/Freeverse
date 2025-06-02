package clients;

import core.fch.RawTxInfo;
import data.apipData.*;
import data.fchData.*;
import data.feipData.*;
import handlers.WebhookManager;
import ui.Menu;
import constants.*;
import core.crypto.Decryptor;
import core.crypto.EncryptType;
import data.fcData.AlgorithmId;
import data.fcData.ReplyBody;
import data.fcData.FidTxMask;
import core.fch.Inputer;
import config.ApiAccount;
import config.ApiProvider;
import server.ApipApiNames;
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

import static data.apipData.FcQuery.PART;
import static server.ApipApiNames.*;
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
            "https://apip.cash/APIP",
            "https://help.cash/APIP",
            "http://127.0.0.1:8080/APIP",
            "http://127.0.0.1:8081/APIP"
    };
    public ApipClient() {
    }
    public ApipClient(ApiProvider apiProvider,ApiAccount apiAccount,byte[] symKey){
        super(apiProvider,apiAccount,symKey);
    }

    public List<Multisign> myMultisigns(String fid) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FIDS).addNewValues(fid);
        return multisignSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }

    public void checkMaster(String prikeyCipher,byte[] symKey,BufferedReader br) {
        byte[] prikey = new Decryptor().decryptJsonBySymkey(prikeyCipher,symKey).getData();
        if (prikey == null) {
            log.error("Failed to decrypt prikey.");
        }

        String fid = prikeyToFid(prikey);
        Cid cid = cidInfoById(fid);
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
        Object data =requestJsonByFcdsl(null, VERSION_1, TOTALS,null,authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMap(data,String.class,String.class);
    }
    public Long bestHeight() {
        //Request
        Block block = bestBlock(RequestMethod.POST,AuthType.FC_SIGN_BODY);
        if(block==null)return null;
        return block.getHeight();
    }

    public Block bestBlock(RequestMethod requestMethod, AuthType authType) {
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data = requestByFcdslOther(SN_2, VERSION_1, BEST_BLOCK, null, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(SN_2, VERSION_1, BEST_BLOCK,null,AuthType.FREE);
        }
        return objectToClass(data,Block.class);
    }
    public ReplyBody general(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType) {
        //Request
        requestJsonByFcdsl(SN_1, VERSION_1, GENERAL,fcdsl,authType,sessionKey, requestMethod);
        return apipClientEvent.getResponseBody();
    }

    public String broadcastTx(String txHex, RequestMethod requestMethod, AuthType authType){
        if(txHex==null)return null;
        if(!Hex.isHexString(txHex))txHex = StringUtils.base64ToHex(txHex);
        if(txHex==null)return null;
        Map<String, String> otherMap = new HashMap<>() ;
        otherMap.put(RAW_TX,txHex);
        Object data = requestByFcdslOther(SN_18, VERSION_1, BROADCAST_TX, otherMap, authType, requestMethod);
        return (String)data;
    }

    public String decodeTx(String txHex, RequestMethod requestMethod, AuthType authType){
        Map<String, String> otherMap = new HashMap<>() ;
        otherMap.put(RAW_TX,txHex);
        Object data = requestByFcdslOther(SN_18, VERSION_1, DECODE_TX, otherMap, authType, requestMethod);
        return data==null ? null: JsonUtils.toNiceJson(data);
    }

    public List<Cash> getCashesFree(String fid, int size, List<String> after) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
        fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
        fcdsl.addSort(CD,ASC).addSort(ID,ASC);
        if(size>0)fcdsl.addSize(size);
        if(after!=null)fcdsl.addAfter(after);
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, CASH_SEARCH, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        return ObjectUtils.objectToList(data,Cash.class);
    }

    public Map<String, BlockInfo>blockByHeights(RequestMethod requestMethod, AuthType authType, String... heights){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(Strings.HEIGHT).addNewValues(heights);

        Object data = requestJsonByFcdsl(SN_2, VERSION_1, BLOCK_BY_HEIGHTS, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToMap(data,String.class,BlockInfo.class);
    }

    public String getPubkey(String fid, RequestMethod requestMethod, AuthType authType) {
        Object data = requestByIds(requestMethod,SN_2, VERSION_1, FID_BY_IDS, authType, fid);
        try {
            return data == null ? null : objectToMap(data, String.class, Cid.class).get(fid).getPubkey();
        }catch (Exception e){
            return null;
        }
    }

    public Map<String, BlockInfo> blockByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, BLOCK_BY_IDS, authType, ids);
        return objectToMap(data,String.class,BlockInfo.class);
    }

    public List<BlockInfo> blockSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        try {
            Object data = requestJsonByFcdsl(SN_2, VERSION_1, BLOCK_SEARCH, fcdsl, authType, sessionKey, requestMethod);
            
            if(data == null) {
                System.out.println("Received null response from server");
                return null;
            }
            return objectToList(data,BlockInfo.class);
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
        Object data = requestByIds(requestMethod, SN_18, VERSION_1, BALANCE_BY_IDS, authType, ids);
        return objectToMap(data,String.class,Long.class);
    }

    public List<Cash> cashValid(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_18, VERSION_1, CASH_VALID, fcdsl,authType, sessionKey, requestMethod);
        return objectToList(data,Cash.class);
    }

    public Map<String, Cash> cashByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, CASH_BY_IDS, authType, ids);
        return objectToMap(data,String.class,Cash.class);
    }

    public List<Cash> cashSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, CASH_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,Cash.class);
    }
    public List<Utxo> getUtxo(String id, double amount, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FID).addNewValues(id);
        Fcdsl.setSingleOtherMap(fcdsl, AMOUNT, String.valueOf(amount));

        Object data = requestJsonByFcdsl(SN_18, VERSION_1, GET_UTXO, fcdsl,authType, sessionKey, requestMethod);
        return objectToList(data,Utxo.class);
    }
    public Map<String, Cid> fidByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, FID_BY_IDS, authType, ids);
        return objectToMap(data,String.class, Cid.class);
    }
    public List<Cid> fidSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, FID_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, Cid.class);
    }
    public Map<String, OpReturn> opReturnByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, OP_RETURN_BY_IDS, authType, ids);
        return objectToMap(data,String.class,OpReturn.class);
    }

    public List<OpReturn> opReturnSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, OP_RETURN_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,OpReturn.class);
    }

    public Map<String, Multisign> multisignByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, MULTISIGN_BY_IDS, authType, ids);
        return objectToMap(data,String.class, Multisign.class);
    }
    public List<Multisign> multisignSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, MULTISIGN_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, Multisign.class);
    }

    public Map<String, TxInfo> txByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, TX_BY_IDS, authType, ids);
        return objectToMap(data,String.class,TxInfo.class);
    }

    public List<TxInfo> txSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, TX_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,TxInfo.class);
    }

    public List<FidTxMask>  txByFid(String fid, int size, String[] last, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl =txByFidQuery(fid, size, last);
        Object data = requestJsonByFcdsl( SN_2, VERSION_1, TX_BY_FID,fcdsl, authType,sessionKey, requestMethod);
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
            data = requestByFcdslOther(SN_2, VERSION_1, CHAIN_INFO, params, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(SN_2, VERSION_1, CHAIN_INFO,params,AuthType.FREE);
        }

        return objectToClass(data,FchChainInfo.class);
    }

    public Map<Long,Long> blockTimeHistory(Long startTime, Long endTime, Integer count, RequestMethod requestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);

        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data =requestByFcdslOther(SN_2, VERSION_1, BLOCK_TIME_HISTORY, params, authType, requestMethod);
        }else {
            data =requestJsonByUrlParams(SN_2, VERSION_1, BLOCK_TIME_HISTORY,params,AuthType.FREE);
        }

        return ObjectUtils.objectToMap(data,Long.class,Long.class);
    }

    public Map<Long,String> difficultyHistory(Long startTime, Long endTime, Integer count, RequestMethod requestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data = requestByFcdslOther(SN_2, VERSION_1, DIFFICULTY_HISTORY, params, authType, requestMethod);
        }else {
            data =requestJsonByUrlParams(SN_2, VERSION_1, DIFFICULTY_HISTORY,params,AuthType.FREE);
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
            data =requestByFcdslOther(SN_2, VERSION_1, HASH_RATE_HISTORY, params, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(SN_2, VERSION_1, HASH_RATE_HISTORY,params,AuthType.FREE);
        }
        return ObjectUtils.objectToMap(data,Long.class,String.class);
    }

    //Identity APIs
    public Map<String, Cid> cidByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_3, VERSION_1, CID_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class, Cid.class);
    }
    public Cid cidInfoById(String id) {
        Map<String, Cid> map = cidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, id);
        try {
            return map.get(id);
        }catch (Exception e){
            log.error("Failed to get Cid info: {}",e.getMessage());
            return null;
        }
    }
    public Cid getFidCid(String id) {
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(ID,id);
        Object data = requestJsonByUrlParams(SN_3, VERSION_1, GET_FID_CID,paramMap,AuthType.FREE);
        return objectToClass(data, Cid.class);
    }

    public Map<String, String> getFidCidMap(List<String> fidList) {
        fidList.remove(null);
        Map<String, Cid> cidInfoMap = this.cidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, fidList.toArray(new String[0]));
        if (cidInfoMap == null) return null;
        Map<String, String> fidCidMap = new HashMap<>();
        for (String fid:cidInfoMap.keySet()) {
            Cid cid = cidInfoMap.get(fid);
            if(cid !=null)fidCidMap.put(fid, cid.getCid());
        }
        return fidCidMap;
    }

    //TODO untested
    public List <Cid> searchCidList(BufferedReader br, boolean choose){
        String part = Inputer.inputString(br,"Input the FID, CID, used CIDs or a part of any one of them:");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(ID,FieldNames.USED_CIDS).addNewValue(part);
        List<Cid> result = cidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null || result.isEmpty())return null;
        return Cid.showCidList("Chose CIDs",result,20,choose,br);
    }

    public List<Cid> cidSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, VERSION_1, CID_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, Cid.class);
    }
    public List<CidHist> cidHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "3");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, VERSION_1, CID_HISTORY,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public Map<String, String[]> fidCidSeek(String searchStr, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(ID,CID).addNewValue(searchStr);
        Object data = requestJsonByFcdsl(SN_3, VERSION_1, FID_CID_SEEK,fcdsl, authType,sessionKey, requestMethod);
        return objectToMap(data,String.class,String[].class);
    }

    public Map<String, String[]> fidCidSeek(String fid_or_cid){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(ID,CID).addNewValue(fid_or_cid);
        Map<String,String> map = new HashMap<>();
        map.put(PART,fid_or_cid);
        Object data = requestJsonByUrlParams(SN_3, VERSION_1, FID_CID_SEEK,map, AuthType.FREE);
        return objectToMap(data,String.class,String[].class);
    }

    public Map<String, Nobody> nobodyByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_3, VERSION_1, NOBODY_BY_IDS, authType, ids);
        return objectToMap(data,String.class,Nobody.class);
    }

    public List<Nobody> nobodySearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, VERSION_1, NOBODY_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,Nobody.class);
    }

    public List<CidHist> homepageHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "9");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, VERSION_1, HOMEPAGE_HISTORY,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public List<CidHist> noticeFeeHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "10");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, VERSION_1, NOTICE_FEE_HISTORY,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public List<CidHist> reputationHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, VERSION_1, REPUTATION_HISTORY,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public Map<String, String> avatars(String[] fids, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(fids);
        Object data = requestJsonByFcdsl(SN_3, VERSION_1, AVATARS,fcdsl, authType,sessionKey, requestMethod);
        return objectToMap(data,String.class,String.class);
    }

    public byte[] getAvatar(String fid){
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(FID,fid);
        Object data = requestBytes(SN_3, VERSION_1, GET_AVATAR, ApipClientEvent.RequestBodyType.NONE,null,paramMap, AuthType.FREE,null, RequestMethod.GET);
        return (byte[])data;
    }


    // Construct
    public Map<String, Protocol> protocolByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_4, VERSION_1, PROTOCOL_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Protocol.class);
    }

    public Protocol protocolById(String id){
        Map<String, Protocol> map = protocolByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Protocol> protocolSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_4, VERSION_1, PROTOCOL_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Protocol.class);
    }


    public List<ProtocolHistory> protocolOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_4, VERSION_1, PROTOCOL_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProtocolHistory.class);
    }


    public List<ProtocolHistory> protocolRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_4, VERSION_1, PROTOCOL_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProtocolHistory.class);
    }

    public Map<String, Code> codeByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_5, VERSION_1, CODE_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Code.class);
    }

    public Code codeById(String id){
        Map<String, Code> map = codeByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Code> codeSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_5, VERSION_1, CODE_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Code.class);
    }

    public List<CodeHistory> codeOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_5, VERSION_1, CODE_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,CodeHistory.class);
    }

    public List<CodeHistory> codeRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_5, VERSION_1, CODE_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,CodeHistory.class);
    }

    public Map<String, Service> serviceByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_6, VERSION_1, SERVICE_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Service.class);
    }

    public List<Service> serviceSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_6, VERSION_1, SERVICE_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Service.class);
    }

    public List<ServiceHistory> serviceOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_6, VERSION_1, SERVICE_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ServiceHistory.class);
    }

    public List<ServiceHistory> serviceRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_6, VERSION_1, SERVICE_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ServiceHistory.class);
    }

    public List<Service> getServiceListByType(String type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewMatch().addNewFields(FieldNames.TYPES).addNewValue(type);
        fcdsl.addNewExcept().addNewTerms().addNewFields(ACTIVE).addNewValues(FALSE);
        serviceList = serviceSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        return serviceList;
    }

    public List<Service> getServiceListByOwnerAndType(String owner, @Nullable Service.ServiceType type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(owner);
        fcdsl.addNewExcept().addNewTerms().addNewFields(CLOSED).addNewValues(TRUE);
        if(type!=null)fcdsl.addNewFilter().addNewMatch().addNewFields(FieldNames.TYPES).setValue(type.name());
        serviceList = serviceSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        return serviceList;
    }

    public Service serviceById(String id){
        Map<String, Service> map = serviceByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public Map<String, App> appByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_7, VERSION_1, APP_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,App.class);
    }

    public App appById(String id){
        Map<String, App> map = appByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<App> appSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_7, VERSION_1, APP_SEARCH, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToList(data,App.class);
    }


    public List<AppHistory> appOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_7, VERSION_1, APP_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,AppHistory.class);
    }


    public List<AppHistory> appRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_7, VERSION_1, APP_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,AppHistory.class);
    }
//Organize
    public Map<String, Group> groupByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_8, VERSION_1, GROUP_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class, Group.class);
    }
    public List<Group> groupSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_8, VERSION_1, GROUP_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data, Group.class);
    }

    public List<GroupHistory> groupOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_8, VERSION_1, GROUP_OP_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,GroupHistory.class);
    }
    public Map<String, String[]> groupMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_8, VERSION_1, GROUP_MEMBERS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,String[].class);
    }

    public List<Group> myGroups(String fid, Long sinceHeight, Integer size, @NotNull final List<String> last, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        if(sinceHeight!=null)
            fcdsl.getQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(String.valueOf(sinceHeight));
        if(size!=null)fcdsl.addSize(size);
        if(!last.isEmpty())fcdsl.addAfter(last);
        Object data = requestJsonByFcdsl(SN_8, VERSION_1, MY_GROUPS, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        List<String> newLast = this.getFcClientEvent().getResponseBody().getLast();
        last.clear();
        last.addAll(newLast);
        return objectToList(data, Group.class);
    }

    public Map<String, Team> teamByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9, VERSION_1, TEAM_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Team.class);
    }
    public List<Team> teamSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, VERSION_1, TEAM_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Team.class);
    }

    public List<TeamHistory> teamOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, VERSION_1, TEAM_OP_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }

    public List<TeamHistory> teamRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, VERSION_1, TEAM_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }
    public Map<String, String[]> teamMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9, VERSION_1, TEAM_MEMBERS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,String[].class);
    }

    public Map<String, String[]> teamExMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9, VERSION_1, TEAM_EX_MEMBERS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,String[].class);
    }
    public Map<String,Team> teamOtherPersons(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9, VERSION_1, TEAM_OTHER_PERSONS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Team.class);
    }
    public List<Team> myTeams(String fid, Long sinceHeight, Integer size, @NotNull final List<String> last, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        if(sinceHeight!=null)
            fcdsl.getQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(String.valueOf(sinceHeight));
        if(size!=null)fcdsl.addSize(size);
        if(!last.isEmpty())fcdsl.addAfter(last);
        
        Object data = requestJsonByFcdsl(SN_9, VERSION_1, MY_TEAMS, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        List<String> newLast = this.getFcClientEvent().getResponseBody().getLast();
        last.clear();
        last.addAll(newLast);
        return objectToList(data,Team.class);
    }

    public Map<String, Box> boxByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_10, VERSION_1, BOX_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Box.class);
    }
    public List<Box> boxSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_10, VERSION_1, BOX_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Box.class);
    }

    public List<BoxHistory> boxHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_10, VERSION_1, BOX_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,BoxHistory.class);
    }

    public Map<String, Contact> contactByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_11, VERSION_1, CONTACT_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Contact.class);
    }
    public List<Contact> contactSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_11, VERSION_1, CONTACT_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Contact.class);
    }

    public List<Contact> contactDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_11, VERSION_1, CONTACTS_DELETED, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Contact.class);
    }

    public List<Contact> freshContactSinceHeight(String myFid, Long lastHeight, Integer size, final List<String> last, Boolean active) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIndex(CONTACT);
        String heightStr = String.valueOf(lastHeight);
        fcdsl.addNewQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(heightStr);

        fcdsl.getQuery().addNewTerms().addNewFields(OWNER).addNewValues(myFid);
        if(active!=null) {
            fcdsl.getQuery().addNewMatch().addNewFields(ACTIVE).addNewValue(String.valueOf(active));
        }
        fcdsl.addSize(size);

        fcdsl.addSort(LAST_HEIGHT, DESC).addSort(ID,ASC);

        if(last!=null && !last.isEmpty())fcdsl.addAfter(last);

        ReplyBody result = this.general(fcdsl,RequestMethod.POST, AuthType.FC_SIGN_BODY);

        Object data = result.getData();
        return objectToList(data, Contact.class);
    }

    public Map<String, Secret> secretByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_12, VERSION_1, SECRET_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Secret.class);
    }
    public List<Secret> secretSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_12, VERSION_1, SECRET_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Secret.class);
    }

    public List<Secret> secretDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_12, VERSION_1, SECRETS_DELETED, fcdsl, authType, sessionKey, requestMethod);
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
        fcdsl.setIndex(index);
        String heightStr = String.valueOf(lastHeight);
        fcdsl.addNewQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(heightStr);

        fcdsl.getQuery().addNewTerms().addNewFields(termField).addNewValues(myFid);
        if(active!=null) {
            fcdsl.getQuery().addNewMatch().addNewFields(ACTIVE).addNewValue(String.valueOf(active));
        }
        fcdsl.addSize(size);

        fcdsl.addSort(sortField, ASC).addSort(idField,ASC);

        if(last!=null && !last.isEmpty())fcdsl.addAfter(last);

        ReplyBody result = this.general(fcdsl,RequestMethod.POST, AuthType.FC_SIGN_BODY);

        Object data = result.getData();
        return objectToList(data, tClass);
    }

    public Map<String, Mail> mailByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_13, VERSION_1, MAIL_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Mail.class);
    }
    public List<Mail> mailSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_13, VERSION_1, MAIL_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }

    public List<Mail> mailDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_13, VERSION_1, MAILS_DELETED, fcdsl, authType, sessionKey, requestMethod);
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
        Object data = requestJsonByFcdsl(SN_13, VERSION_1, MAIL_THREAD, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }

    public List<Mail> freshMailSinceHeight(String myFid, long lastHeight, Integer defaultRequestSize, List<String> last, Boolean active) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIndex(MAIL);
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


        ReplyBody result = this.general(fcdsl,RequestMethod.POST, AuthType.FC_SIGN_BODY);

        Object data = result.getData();
        return objectToList(data, Mail.class);
    }

    public Map<String, Proof> proofByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_14, VERSION_1, PROOF_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Proof.class);
    }
    public List<Proof> proofSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_14, VERSION_1, PROOF_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Proof.class);
    }

    public List<ProofHistory> proofHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_14, VERSION_1, PROOF_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProofHistory.class);
    }


    public Map<String, Statement> statementByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_15, VERSION_1, STATEMENT_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Statement.class);
    }
    public List<Statement> statementSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_15, VERSION_1, STATEMENT_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Statement.class);
    }
    public List<Nid> nidSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_19, VERSION_1, NID_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Nid.class);
    }

    public Map<String, Token> tokenByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_16, VERSION_1, TOKEN_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Token.class);
    }
    public List<Token> tokenSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, VERSION_1, TOKEN_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Token.class);
    }

    public List<TokenHistory> tokenHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, VERSION_1, TOKEN_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TokenHistory.class);
    }

    public List<Group> myTokens(String fid, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.FID).addNewValues(fid);
        Object data = requestJsonByFcdsl(SN_16, VERSION_1, MY_TOKENS, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data, Group.class);
    }

    public Map<String, TokenHolder> tokenHoldersByIds(RequestMethod requestMethod, AuthType authType, String... tokenIds){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(Token_Id).addNewValues(tokenIds);
        Object data = requestJsonByFcdsl(SN_16, VERSION_1, TOKEN_HOLDERS_BY_IDS,fcdsl, authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMap(data,String.class,TokenHolder.class);
    }

    public List<TokenHolder> tokenHolderSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, VERSION_1, TOKEN_HOLDER_SEARCH,fcdsl,authType,sessionKey, requestMethod);
        return ObjectUtils.objectToList(data,TokenHolder.class);
    }
    public List<UnconfirmedInfo> unconfirmed(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_18, VERSION_1, ApipApiNames.UNCONFIRMED, authType, ids);
        return ObjectUtils.objectToList(data,UnconfirmedInfo.class);
    }

    public Map<String, List<Cash>> unconfirmedCaches(RequestMethod requestMethod, AuthType authType,String... ids){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(ids);
        Object data = requestJsonByFcdsl(SN_18, VERSION_1, UNCONFIRMED_CASHES,fcdsl, authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMapWithListValues(data, String.class, Cash.class);
    }

    public Double feeRate(RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_18, VERSION_1, FEE_RATE, null, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return (Double)data;
    }


    public Map<String, String> addresses(String addrOrPubkey, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.ADDR_OR_PUB_KEY, addrOrPubkey);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, ADDRESSES,fcdsl, authType,sessionKey, requestMethod);
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
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, ENCRYPT,fcdsl, authType,sessionKey, requestMethod);
        return objectToClass(data,String.class);
    }
    public boolean verify(String signature, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, SIGN, signature);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, VERIFY,fcdsl, authType,sessionKey, requestMethod);
        if(data==null) return false;
        return (boolean) data;
    }

    public String sha256(String text, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, text);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, SHA_256,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }
    public String sha256x2(String text, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, text);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, SHA_256_X_2,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }
    public String sha256Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, SHA_256_HEX,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String sha256x2Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, SHA_256_X_2_HEX,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String ripemd160Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, RIPEMD_160_HEX,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String KeccakSha3Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, KECCAK_SHA_3_HEX,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String hexToBase58(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, HEX_TO_BASE_58,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String checkSum4Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, CHECK_SUM_4_HEX,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String offLineTx(String fromFid, List<SendTo> sendToList, String msg, Long cd, String ver, RequestMethod requestMethod, AuthType authType){
        if(requestMethod.equals(RequestMethod.POST)) {
            Fcdsl fcdsl = new Fcdsl();
            RawTxInfo rawTxInfo = new RawTxInfo();
            rawTxInfo.setSender(fromFid);
            rawTxInfo.setOpReturn(msg);
            rawTxInfo.setOutputs(sendToList);
            rawTxInfo.setCd(cd);
            rawTxInfo.setVer(ver);
            Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.DATA_FOR_OFF_LINE_TX, JsonUtils.toJson(rawTxInfo));
            Object data = requestJsonByFcdsl(SN_18, VERSION_1, OFF_LINE_TX, fcdsl, authType, sessionKey, requestMethod);
            return (String) data;
        }

        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(VER,ver);
        paramMap.put("fromFid",fromFid);
        List<String> toList = new ArrayList<>();
        List<String> amountList = new ArrayList<>();

        if(sendToList!=null && !sendToList.isEmpty()) {
            for (SendTo sendTo : sendToList) {
                toList.add(sendTo.getFid());
                amountList.add(String.valueOf(sendTo.getAmount()));
            }
            paramMap.put("toFids", String.join(",", toList));
            paramMap.put("amounts", String.join(",", amountList));
        }
        if(msg!=null)paramMap.put(MESSAGE,msg);
        if(cd!=0)paramMap.put(CD,String.valueOf(cd));

        Object data = requestJsonByUrlParams(SN_18, VERSION_1, OFF_LINE_TX, paramMap, authType);
        return (String) data;
    }

    public String circulating(){
        Object data = requestBase(CIRCULATING, ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String totalSupply(){
        Object data = requestBase(TOTAL_SUPPLY, ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String richlist(){
        Object data = requestBase(RICHLIST, ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String freecashInfo(){
        Object data = requestBase(FREECASH_INFO, ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
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
//    webhookRequestBody.setMethod(method);
//    webhookRequestBody.setUserId(apiAccount.getUserId());
//    String hookUserId = WebhookRequestBody.makeHookUserId(apiAccount.getProviderId(), apiAccount.getUserId(), ApiNames.NewCashByFids);
//    webhookRequestBody.setHookUserId(hookUserId);
        return switch (method){
            case NEW_CASH_BY_FIDS -> newCashListByIds(webhookRequestBody);
            case NEW_OP_RETURN_BY_FIDS -> newOpReturnListByIds(webhookRequestBody);
            default -> null;
        };
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
        switch (method) {
            case NEW_CASH_BY_FIDS -> dataMap = newCashListByIds(webhookRequestBody);
            case NEW_OP_RETURN_BY_FIDS -> dataMap=newOpReturnListByIds(webhookRequestBody);
        }
        if(dataMap==null)return null;
        return dataMap.get(HOOK_USER_ID);
    }

    public Map<String, String> newCashListByIds(WebhookManager.WebhookRequestBody webhookRequestBody){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.WEBHOOK_REQUEST_BODY, JsonUtils.toJson(webhookRequestBody));
        Object data = requestJsonByFcdsl(SN_20, VERSION_1, ApipApiNames.NEW_CASH_BY_FIDS, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToMap(data,String.class,String.class);
    }

    public Map<String, String> newOpReturnListByIds(WebhookManager.WebhookRequestBody webhookRequestBody){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.WEBHOOK_REQUEST_BODY, JsonUtils.toJson(webhookRequestBody));
        Object data = requestJsonByFcdsl(SN_20, VERSION_1, NEW_OP_RETURN_BY_FIDS, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToMap(data,String.class,String.class);
    }
    public Cid searchCidOrFid(BufferedReader br) {
        String choose = chooseFid(br);
        if (choose == null) return null;
        return cidInfoById(choose);
    }

    public String chooseFid(BufferedReader br) {
        while (true) {
            String input = Inputer.inputString(br, "Input CID, FID or a part of them:");
            Map<String, String[]> result = fidCidSeek(input, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        fcdsl.addIndex(indexName);
        if(searchFieldName!=null)fcdsl.addNewQuery().addNewTerms().addNewFields(searchFieldName).addNewValues(searchValue);
        if(sinceFieldName!=null)fcdsl.getQuery().addNewRange().addNewFields(sinceFieldName).addGt(String.valueOf(sinceHeight));
        for(Sort sort: sortList)fcdsl.addSort(sort.getField(),sort.getOrder());
        if(last !=null)fcdsl.addAfter(last).addSize(size);
    
        ReplyBody result = apipClient.general(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        Map<String, List<Cash>> result = unconfirmedCaches(RequestMethod.POST, AuthType.FC_SIGN_BODY,fid);
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
        fcdsl.setIndex(index);
        fcdsl.addIds(ids);

        // Make request
        ReplyBody result = general(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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

    public Map<String, Essay> essayByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_21, VERSION_1, ESSAY_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Essay.class);
    }

    public Essay essayById(String id){
        Map<String, Essay> map = essayByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Essay> essaySearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_21, VERSION_1, ESSAY_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Essay.class);
    }

    public List<EssayHistory> essayOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_21, VERSION_1, ESSAY_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,EssayHistory.class);
    }

    public List<EssayHistory> essayRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_21, VERSION_1, ESSAY_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,EssayHistory.class);
    }

    public Map<String, Report> reportByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_22, VERSION_1, REPORT_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Report.class);
    }

    public Report reportById(String id){
        Map<String, Report> map = reportByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Report> reportSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_22, VERSION_1, REPORT_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Report.class);
    }

    public List<ReportHistory> reportOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_22, VERSION_1, REPORT_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ReportHistory.class);
    }

    public List<ReportHistory> reportRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_22, VERSION_1, REPORT_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ReportHistory.class);
    }

    public Map<String, Paper> paperByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_23, VERSION_1, PAPER_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Paper.class);
    }

    public Paper paperById(String id){
        Map<String, Paper> map = paperByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Paper> paperSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_23, VERSION_1, PAPER_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Paper.class);
    }

    public List<PaperHistory> paperOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_23, VERSION_1, PAPER_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,PaperHistory.class);
    }

    public List<PaperHistory> paperRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_23, VERSION_1, PAPER_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,PaperHistory.class);
    }

    public Map<String, Book> bookByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_24, VERSION_1, BOOK_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Book.class);
    }

    public Book bookById(String id){
        Map<String, Book> map = bookByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Book> bookSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_24, VERSION_1, BOOK_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Book.class);
    }

    public List<BookHistory> bookOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_24, VERSION_1, BOOK_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,BookHistory.class);
    }

    public List<BookHistory> bookRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_24, VERSION_1, BOOK_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,BookHistory.class);
    }
}
