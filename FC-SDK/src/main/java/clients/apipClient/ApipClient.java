package clients.apipClient;

import apip.apipData.*;
import appTools.Menu;
import clients.FcClientEvent;
import clients.FeipClient;
import constants.*;
import crypto.Decryptor;
import crypto.EncryptType;
import fcData.AlgorithmId;
import fcData.FcReplierHttp;
import fch.DataForOffLineTx;
import fch.Inputer;
import fch.fchData.*;
import feip.feipData.*;
import clients.Client;
import configure.ApiAccount;
import configure.ApiProvider;
import configure.ServiceType;
import feip.feipData.Nobody;
import javaTools.JsonTools;
import javaTools.ObjectTools;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static apip.apipData.FcQuery.PART;
import static constants.ApiNames.*;
import static constants.FieldNames.*;
import static constants.FieldNames.FID;
import static constants.FieldNames.SIGN;
import static constants.OpNames.RATE;
import static constants.Strings.*;
import static constants.Values.FALSE;
import static constants.Values.TRUE;
import static crypto.KeyTools.priKeyToFid;
import static javaTools.ObjectTools.objectToList;
import static javaTools.ObjectTools.objectToMap;
import static javaTools.ObjectTools.objectToClass;

public class ApipClient extends Client {

    public ApipClient() {
    }
    public ApipClient(ApiProvider apiProvider,ApiAccount apiAccount,byte[] symKey){
        super(apiProvider,apiAccount,symKey);
    }
    public void checkMaster(String priKeyCipher,byte[] symKey,BufferedReader br) {
        byte[] priKey = new Decryptor().decryptJsonBySymKey(priKeyCipher,symKey).getData();
        if (priKey == null) {
            log.error("Failed to decrypt priKey.");
        }

        String fid = priKeyToFid(priKey);
        CidInfo cidInfo = cidInfoById(fid);
        if (cidInfo == null) {
            System.out.println("This fid was never seen on chain. Send some fch to it.");
            Menu.anyKeyToContinue(br);
            return;
        }
        if (cidInfo.getMaster() != null) {
            System.out.println("The master of " + fid + " is " + cidInfo.getMaster());
            return;
        }
        if (Inputer.askIfYes(br, "Assign the master for " + fid + "?"))
            FeipClient.setMaster(fid, priKeyCipher, bestHeight, symKey, apipClient, br);
    }

    //OpenAPIs: Ping(Client),GetService(Client),SignIn,SignInEccAPI,Totals

    public Map<String, String> totals(RequestMethod requestMethod, AuthType authType) {
        //Request
        Object data =requestJsonByFcdsl(null, Version1, Totals,null,authType,sessionKey, requestMethod);
        return ObjectTools.objectToMap(data,String.class,String.class);
    }

    public FcReplierHttp general(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType) {
        //Request
        requestJsonByFcdsl(SN_1, Version1, General,fcdsl,authType,sessionKey, requestMethod);

        //Check result
        checkResult();

        return fcClientEvent.getResponseBody();
    }

    public String broadcastTx(String txHex, RequestMethod requestMethod, AuthType authType){
        Map<String, String> otherMap = new HashMap<>() ;
        otherMap.put(RAW_TX,txHex);
        Object data = requestByFcdslOther(SN_18, Version1, BroadcastTx, otherMap, authType, requestMethod);
        return data==null ? null:(String)data;
    }

    public String decodeTx(String txHex, RequestMethod requestMethod, AuthType authType){
        Map<String, String> otherMap = new HashMap<>() ;
        otherMap.put(RAW_TX,txHex);
        Object data = requestByFcdslOther(SN_18, Version1, DecodeTx, otherMap, authType, requestMethod);
        return data==null ? null: JsonTools.toNiceJson(data);
    }

    public List<Cash> getCashesFree(String fid, int size, List<String> after) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
        fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
        fcdsl.addSort(CD,ASC).addSort(CASH_ID,ASC);
        if(size>0)fcdsl.addSize(size);
        if(after!=null)fcdsl.addAfter(after);
        Object data = requestJsonByFcdsl(SN_2, Version1, CashSearch, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        return ObjectTools.objectToList(data,Cash.class);
    }

    public List<Cash> getCashes(String urlHead, String id, double amount) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        String urlTail = ApiNames.makeUrlTailPath(SN_18,Version1) + ApiNames.GetCashes;
        if (id != null) urlTail = urlTail + "?fid=" + id;
        if (amount != 0) {
            if(id==null){
                System.out.println(ReplyCodeMessage.Msg1021FidIsRequired);
                return null;
            }
            urlTail = urlTail + "&amount=" + amount;
        }
        apipClientData.addNewApipUrl(urlHead, urlTail);
        apipClientData.get();
        Object data = checkResult();
        if(data==null){
            System.out.println("Failed to get cashes from:"+urlHead);
            return null;
        }
        return ObjectTools.objectToList(data,Cash.class);//DataGetter.getCashList(data);
    }

//    public List<Cash> cashValidForPay(HttpRequestMethod httpRequestMethod, String fid, double amount, AuthType authType){
//        Fcdsl fcdsl = new Fcdsl();
//        Map<String,String> paramMap = new HashMap<>();
//
//
//        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(fid);
//        amount = NumberTools.roundDouble8(amount);
//        fcdsl.setOther(amount);
//
//        Object data = requestJsonByFcdsl(SN_18, Version1, CashValid, fcdsl, authType, sessionKey, httpRequestMethod);
//        if(data==null)return null;
//        return objectToList(data,Cash.class);
//    }

    public Map<String, BlockInfo>blockByHeights(RequestMethod requestMethod, AuthType authType, String... heights){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(Strings.HEIGHT).addNewValues(heights);

        Object data = requestJsonByFcdsl(SN_2, Version1, BlockByHeights, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToMap(data,String.class,BlockInfo.class);
    }

    public String getPubKey(String fid, RequestMethod requestMethod, AuthType authType) {
        Object data = requestByIds(requestMethod,SN_2,Version1, FidByIds, authType, fid);
        try {
            return data == null ? null : objectToMap(data, String.class, Address.class).get(fid).getPubKey();
        }catch (Exception e){
            return null;
        }
    }

    public Map<String, BlockInfo> blockByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, Version1, BlockByIds, authType, ids);
        return objectToMap(data,String.class,BlockInfo.class);
    }

    public List<BlockInfo> blockSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version1, BlockSearch,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,BlockInfo.class);
    }

    public List<Cash> cashValid(String fid, Double amount, Long cd, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Map<String,String> paramMap = new HashMap<>();
        if(amount!=null)paramMap.put(AMOUNT, String.valueOf(amount));
        if(fid!=null)paramMap.put(FID,fid);
        if(cd!=null)paramMap.put(CD, String.valueOf(cd));
        fcdsl.addOther(paramMap);
        return cashValid(fcdsl, requestMethod,authType);
    }

    public List<Cash> cashValid(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_18, Version1, CashValid, fcdsl,authType, sessionKey, requestMethod);
        return objectToList(data,Cash.class);
    }

    public Map<String, Cash> cashByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, Version1, CashByIds, authType, ids);
        return objectToMap(data,String.class,Cash.class);
    }

    public List<Cash> cashSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version1, CashSearch,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,Cash.class);
    }
    public List<Utxo> getUtxo(String id, double amount, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FID).addNewValues(id);
        Fcdsl.setSingleOtherMap(fcdsl, AMOUNT, String.valueOf(amount));

        Object data = requestJsonByFcdsl(SN_18, Version1, GetUtxo, fcdsl,authType, sessionKey, requestMethod);
        return objectToList(data,Utxo.class);
    }
    public Map<String, Address> fidByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, Version1, FidByIds, authType, ids);
        return objectToMap(data,String.class,Address.class);
    }
    public List<Address> fidSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version1, FidSearch,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,Address.class);
    }
    public Map<String, OpReturn> opReturnByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, Version1, OpReturnByIds, authType, ids);
        return objectToMap(data,String.class,OpReturn.class);
    }

    public List<OpReturn> opReturnSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version1, OpReturnSearch,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,OpReturn.class);
    }

    public Map<String, P2SH> p2shByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, Version1, P2shByIds, authType, ids);
        return objectToMap(data,String.class,P2SH.class);
    }
    public List<P2SH> p2shSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version1, P2shSearch,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,P2SH.class);
    }

    public Map<String, TxInfo> txByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, Version1, TxByIds, authType, ids);
        return objectToMap(data,String.class,TxInfo.class);
    }

    public List<TxInfo> txSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version1, TxSearch,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,TxInfo.class);
    }

    public List<TxInfo>  txByFid(String fid, int size, String[] last, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl =txByFidQuery(fid, size, last);
        Object data = requestJsonByFcdsl( SN_2, Version1, TxByFid,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,TxInfo.class);
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

    public FchChainInfo chainInfo(Long height, RequestMethod requestMethod, AuthType authType) {
        Map<String,String> params=null;
        if(height!=null){
            params = new HashMap<>();
            params.put(FieldNames.HEIGHT, String.valueOf(height));
        }
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data = requestByFcdslOther(SN_2, Version1, ChainInfo, params, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(SN_2, Version1, ChainInfo,params,AuthType.FREE);
        }

        return objectToClass(data,FchChainInfo.class);
    }

    public Map<Long,Long> blockTimeHistory(Long startTime, Long endTime, Integer count, RequestMethod requestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);

        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data =requestByFcdslOther(SN_2, Version1, BlockTimeHistory, params, authType, requestMethod);
        }else {
            data =requestJsonByUrlParams(SN_2, Version1, BlockTimeHistory,params,AuthType.FREE);
        }

        return ObjectTools.objectToMap(data,Long.class,Long.class);
    }

    public Map<Long,String> difficultyHistory(Long startTime, Long endTime, Integer count, RequestMethod requestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data = requestByFcdslOther(SN_2, Version1, DifficultyHistory, params, authType, requestMethod);
        }else {
            data =requestJsonByUrlParams(SN_2, Version1, DifficultyHistory,params,AuthType.FREE);
        }

        return ObjectTools.objectToMap(data,Long.class,String.class);
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
            data =requestByFcdslOther(SN_2, Version1, HashRateHistory, params, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(SN_2, Version1, HashRateHistory,params,AuthType.FREE);
        }
        return ObjectTools.objectToMap(data,Long.class,String.class);
    }

    //Identity APIs
    public Map<String,CidInfo> cidInfoByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_3,Version1, CidInfoByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,CidInfo.class);
    }
    public CidInfo cidInfoById(String id) {
        Map<String,CidInfo> map = cidInfoByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, id);
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }
    public CidInfo getFidCid(String id) {
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(ID,id);
        Object data = requestJsonByUrlParams(SN_3,Version1,GetFidCid,paramMap,AuthType.FREE);
        return objectToClass(data,CidInfo.class);
    }
    public List<CidInfo> cidInfoSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, Version1, CidInfoSearch,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidInfo.class);
    }
    public List<CidHist> cidHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "3");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, Version1, CidHistory,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public Map<String, String[]> fidCidSeek(String searchStr, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FID,CID).addNewValue(searchStr);
        Object data = requestJsonByFcdsl(SN_3, Version1, FidCidSeek,fcdsl, authType,sessionKey, requestMethod);
        return objectToMap(data,String.class,String[].class);
    }

    public Map<String, String[]> fidCidSeek(String fid_or_cid){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FID,CID).addNewValue(fid_or_cid);
        Map<String,String> map = new HashMap<>();
        map.put(PART,fid_or_cid);
        Object data = requestJsonByUrlParams(SN_3, Version1, FidCidSeek,map, AuthType.FREE);
        return objectToMap(data,String.class,String[].class);
    }

    public Map<String, Nobody> nobodyByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_3, Version1, NobodyByIds, authType, ids);
        return objectToMap(data,String.class,Nobody.class);
    }

    public List<Nobody> nobodySearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, Version1, NobodySearch,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,Nobody.class);
    }

    public List<CidHist> homepageHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "9");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, Version1, HomepageHistory,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public List<CidHist> noticeFeeHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "10");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, Version1, NoticeFeeHistory,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public List<CidHist> reputationHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, Version1,ReputationHistory,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public Map<String, String> avatars(String[] fids, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(fids);
        Object data = requestJsonByFcdsl(SN_3, Version1,Avatars,fcdsl, authType,sessionKey, requestMethod);
        return objectToMap(data,String.class,String.class);
    }

    public byte[] getAvatar(String fid){
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(FID,fid);
        Object data = requestBytes(SN_3, Version1,GetAvatar, FcClientEvent.RequestBodyType.NONE,null,paramMap, AuthType.FREE,null, RequestMethod.GET);
        return (byte[])data;
    }


    // Construct
    public Map<String, Protocol> protocolByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_4,Version1, ProtocolByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Protocol.class);
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
        Object data = requestJsonByFcdsl(SN_4, Version1, ProtocolSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Protocol.class);
    }


    public List<ProtocolHistory> protocolOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_4, Version1, ProtocolOpHistory, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProtocolHistory.class);
    }


    public List<ProtocolHistory> protocolRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_4, Version1, ProtocolRateHistory, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProtocolHistory.class);
    }

    public Map<String, Code> codeByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_5,Version1, CodeByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Code.class);
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
        Object data = requestJsonByFcdsl(SN_5, Version1, CodeSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Code.class);
    }


    public List<CodeHistory> codeOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_5, Version1, CodeOpHistory, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,CodeHistory.class);
    }

    public List<CodeHistory> codeRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_5, Version1, CodeRateHistory, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,CodeHistory.class);
    }

    public Map<String, Service> serviceByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_6,Version1, ServiceByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Service.class);
    }

    public List<Service> serviceSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_6, Version1, ServiceSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Service.class);
    }

    public List<ServiceHistory> serviceOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_6, Version1, ServiceOpHistory, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ServiceHistory.class);
    }

    public List<ServiceHistory> serviceRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_6, Version1, ServiceRateHistory, fcdsl, authType, sessionKey, requestMethod);
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

    public List<Service> getServiceListByOwnerAndType(String owner, @Nullable ServiceType type) {
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
        Object data = requestByIds(requestMethod,SN_7,Version1, AppByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,App.class);
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
        Object data = requestJsonByFcdsl(SN_7, Version1, AppSearch, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToList(data,App.class);
    }


    public List<AppHistory> appOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_7, Version1, AppOpHistory, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,AppHistory.class);
    }


    public List<AppHistory> appRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_7, Version1, AppRateHistory, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,AppHistory.class);
    }
//Organize
    public Map<String, Group> groupByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_8,Version1, GroupByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Group.class);
    }
    public List<Group> groupSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_8, Version1, GroupSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Group.class);
    }

    public List<GroupHistory> groupOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_8, Version1, GroupOpHistory, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,GroupHistory.class);
    }
    public Map<String, String[]> groupMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_8,Version1, GroupMembers, authType, ids);
        return ObjectTools.objectToMap(data,String.class,String[].class);
    }

    public List<MyGroupData> myGroups(String fid, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        Object data = requestJsonByFcdsl(SN_8, Version1, MyGroups, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,MyGroupData.class);
    }

    public Map<String, Team> teamByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9,Version1, TeamByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Team.class);
    }
    public List<Team> teamSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, Version1, TeamSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Team.class);
    }

    public List<TeamHistory> teamOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, Version1, TeamOpHistory, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }

    public List<TeamHistory> teamRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, Version1, TeamRateHistory, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }
    public Map<String, String[]> teamMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9,Version1, TeamMembers, authType, ids);
        return ObjectTools.objectToMap(data,String.class,String[].class);
    }

    public Map<String, String[]> teamExMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9,Version1, TeamExMembers, authType, ids);
        return ObjectTools.objectToMap(data,String.class,String[].class);
    }
    public Map<String,TeamOtherPersonsData> teamOtherPersons(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9,Version1, TeamOtherPersons, authType, ids);
        return ObjectTools.objectToMap(data,String.class,TeamOtherPersonsData.class);
    }
    public List<MyTeamData> myTeams(String fid, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        Object data = requestJsonByFcdsl(SN_9, Version1, MyTeams, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,MyTeamData.class);
    }

    public Map<String, Box> boxByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_10,Version1, BoxByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Box.class);
    }
    public List<Box> boxSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_10, Version1, BoxSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Box.class);
    }

    public List<BoxHistory> boxHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_10, Version1, BoxHistory, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,BoxHistory.class);
    }

    public Map<String, Contact> contactByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_11,Version1, ContactByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Contact.class);
    }
    public List<Contact> contactSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_11, Version1, ContactSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Contact.class);
    }

    public List<Contact> contactDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_11, Version1, ContactsDeleted, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Contact.class);
    }

    public Map<String, Secret> secretByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_12,Version1, SecretByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Secret.class);
    }
    public List<Secret> secretSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_12, Version1, SecretSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Secret.class);
    }

    public List<Secret> secretDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_12, Version1, SecretsDeleted, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Secret.class);
    }


    public Map<String, Mail> mailByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_13,Version1, MailByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Mail.class);
    }
    public List<Mail> mailSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_13, Version1, MailSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }

    public List<Mail> mailDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_13, Version1, MailsDeleted, fcdsl, authType, sessionKey, requestMethod);
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
        Object data = requestJsonByFcdsl(SN_13, Version1, MailThread, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }

    public Map<String, Proof> proofByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_14,Version1, ProofByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Proof.class);
    }
    public List<Proof> proofSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_14, Version1, ProofSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Proof.class);
    }

    public List<ProofHistory> proofHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_14, Version1, ProofHistory, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProofHistory.class);
    }


    public Map<String, Statement> statementByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_15,Version1, StatementByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Statement.class);
    }
    public List<Statement> statementSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_15, Version1, StatementSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Statement.class);
    }
    public List<Nid> nidSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_19, Version1, NidSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Nid.class);
    }

    public Map<String, Token> tokenByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_16,Version1, TokenByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Token.class);
    }
    public List<Token> tokenSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, Version1, TokenSearch, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Token.class);
    }

    public List<TokenHistory> tokenHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, Version1, TokenHistory, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TokenHistory.class);
    }

    public List<MyGroupData> myTokens(String fid, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.FID).addNewValues(fid);
        Object data = requestJsonByFcdsl(SN_16, Version1, MyTokens, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,MyGroupData.class);
    }

    public Map<String, TokenHolder> tokenHoldersByIds(RequestMethod requestMethod, AuthType authType, String... tokenIds){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(Token_Id).addNewValues(tokenIds);
        Object data = requestJsonByFcdsl(SN_16,Version1, TokenHoldersByIds,fcdsl, authType,sessionKey, requestMethod);
        return ObjectTools.objectToMap(data,String.class,TokenHolder.class);
    }

    public List<TokenHolder> tokenHolderSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, Version1, TokenHolderSearch,fcdsl,authType,sessionKey, requestMethod);
        return ObjectTools.objectToList(data,TokenHolder.class);
    }
    public List<UnconfirmedInfo> unconfirmed(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_18,Version1, Unconfirmed, authType, ids);
        return ObjectTools.objectToList(data,UnconfirmedInfo.class);
    }

    public Double feeRate(RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_18, Version1, FeeRate, null, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return (Double)data;
    }


    public Map<String, String> addresses(String addrOrPubKey, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.ADDR_OR_PUB_KEY, addrOrPubKey);
        Object data = requestJsonByFcdsl(SN_17,Version1, Addresses,fcdsl, authType,sessionKey, requestMethod);
        return ObjectTools.objectToMap(data,String.class,String.class);
    }
    public String encrypt(EncryptType encryptType, String message, String key, String fid, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        EncryptIn encryptIn = new EncryptIn();
        encryptIn.setType(encryptType);
        encryptIn.setMsg(message);
        switch (encryptType){
            case SymKey -> {
                encryptIn.setSymKey(key);
                encryptIn.setAlg(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
            }
            case Password -> {
                encryptIn.setPassword(key);
                encryptIn.setAlg(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
            }
            case AsyOneWay -> {
                if(key!=null) {
                    encryptIn.setPubKey(key);
                }else if(fid!=null){
                    encryptIn.setFid(fid);
                }
                encryptIn.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            }
            default -> {
                return null;
            }
        }
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.ENCRYPT_INPUT, JsonTools.toJson(encryptIn));
        Object data = requestJsonByFcdsl(SN_17,Version1, Encrypt,fcdsl, authType,sessionKey, requestMethod);
        return objectToClass(data,String.class);
    }
    public boolean verify(String signature, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, SIGN, signature);
        Object data = requestJsonByFcdsl(SN_17,Version1, Verify,fcdsl, authType,sessionKey, requestMethod);
        if(data==null) return false;
        return (boolean) data;
    }

    public String sha256(String text, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, text);
        Object data = requestJsonByFcdsl(SN_17,Version1, Sha256,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }
    public String sha256x2(String text, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, text);
        Object data = requestJsonByFcdsl(SN_17,Version1, Sha256x2,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }
    public String sha256Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version1, Sha256Hex,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String sha256x2Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version1, Sha256x2Hex,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String ripemd160Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version1, Ripemd160Hex,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String KeccakSha3Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version1, KeccakSha3Hex,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String hexToBase58(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version1, HexToBase58,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String checkSum4Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version1, CheckSum4Hex,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String offLineTx(String fromFid, List<SendTo> sendToList, String msg, Long cd, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        DataForOffLineTx dataForOffLineTx = new DataForOffLineTx();
        dataForOffLineTx.setFromFid(fromFid);
        dataForOffLineTx.setMsg(msg);
        dataForOffLineTx.setSendToList(sendToList);
        dataForOffLineTx.setCd(cd);
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.DATA_FOR_OFF_LINE_TX, JsonTools.toJson(dataForOffLineTx));
        Object data = requestJsonByFcdsl(SN_18,Version1, OffLineTx,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String circulating(){
        Object data = requestBase(Circulating, FcClientEvent.RequestBodyType.NONE, null, null, null, null, null, FcClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String totalSupply(){
        Object data = requestBase(TotalSupply, FcClientEvent.RequestBodyType.NONE, null, null, null, null, null, FcClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String richlist(){
        Object data = requestBase(Richlist, FcClientEvent.RequestBodyType.NONE, null, null, null, null, null, FcClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String freecashInfo(){
        Object data = requestBase(FreecashInfo, FcClientEvent.RequestBodyType.NONE, null, null, null, null, null, FcClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
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
    public void setClientData(ApipClientEvent clientData) {
        this.fcClientEvent = clientData;
    }

//    public Fcdsl getFcdsl() {
//        return fcdsl;
//    }
//
//    public void setFcdsl(Fcdsl fcdsl) {
//        this.fcdsl = fcdsl;
//    }
    //Webhook APIs
    public Map<String, String> checkSubscription(String method, String endpoint) {
        WebhookRequestBody webhookRequestBody = new WebhookRequestBody();
        webhookRequestBody.setEndpoint(endpoint);
        webhookRequestBody.setOp(CHECK);
//    webhookRequestBody.setMethod(method);
//    webhookRequestBody.setUserId(apiAccount.getUserId());
//    String hookUserId = WebhookRequestBody.makeHookUserId(apiAccount.getProviderId(), apiAccount.getUserId(), ApiNames.NewCashByFids);
//    webhookRequestBody.setHookUserId(hookUserId);
        return switch (method){
            case NewCashByFids -> newCashListByIds(webhookRequestBody);
            case NewOpReturnByFids -> newOpReturnListByIds(webhookRequestBody);
            default -> null;
        };
    }
    //
    public String subscribeWebhook(String method, Object data, String endpoint) {
        WebhookRequestBody webhookRequestBody = new WebhookRequestBody();
        webhookRequestBody.setEndpoint(endpoint);
        webhookRequestBody.setMethod(method);
//        webhookRequestBody.setUserId(apiAccount.getUserId());
        webhookRequestBody.setOp(SUBSCRIBE);
        webhookRequestBody.setData(data);
        Map<String, String> dataMap=null;
        switch (method) {
            case NewCashByFids -> dataMap = newCashListByIds(webhookRequestBody);
            case NewOpReturnByFids -> dataMap=newOpReturnListByIds(webhookRequestBody);
        }
        if(dataMap==null)return null;
        return dataMap.get(HOOK_USER_ID);
    }

    public Map<String, String> newCashListByIds(WebhookRequestBody webhookRequestBody){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.WEBHOOK_REQUEST_BODY, JsonTools.toJson(webhookRequestBody));
        Object data = requestJsonByFcdsl(SN_20, Version1, ApiNames.NewCashByFids, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToMap(data,String.class,String.class);
    }

    public Map<String, String> newOpReturnListByIds(WebhookRequestBody webhookRequestBody){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.WEBHOOK_REQUEST_BODY, JsonTools.toJson(webhookRequestBody));
        Object data = requestJsonByFcdsl(SN_20, Version1, NewOpReturnByFids, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToMap(data,String.class,String.class);
    }
}
