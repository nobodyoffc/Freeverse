package clients.apipClient;

import apip.apipData.*;
import appTools.Menu;
import clients.FcClientEvent;
import clients.FeipClient;
import constants.*;
import crypto.Decryptor;
import crypto.EncryptType;
import fcData.AlgorithmId;
import fcData.FcReplier;
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
import javaTools.http.HttpRequestMethod;
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
//        this.signInUrlTailPath= ApiUrl.makeUrlTailPath(null,ApiNames.Version2);
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

    public Map<String, String> totals(HttpRequestMethod httpRequestMethod, AuthType authType) {
        //Request
        requestJsonByFcdsl(null, Version2, Totals,null,authType,sessionKey,httpRequestMethod);
        //Check result
        Object data = checkResult();
        return ObjectTools.objectToMap(data,String.class,String.class);
    }

    public FcReplier general(Fcdsl fcdsl,HttpRequestMethod httpRequestMethod, AuthType authType) {
        //Request
        requestJsonByFcdsl(SN_1, Version2, General,fcdsl,authType,sessionKey,httpRequestMethod);

        //Check result
        checkResult();

        return fcClientEvent.getResponseBody();
    }

    public String broadcastTx(String txHex, HttpRequestMethod httpRequestMethod, AuthType authType){
        Map<String, String> otherMap = new HashMap<>() ;
        otherMap.put(RAW_TX,txHex);
        Object data = requestByFcdslOther(SN_18, Version2, BroadcastTx, otherMap, authType, httpRequestMethod);
        return data==null ? null:(String)data;
    }

    public String decodeTx(String txHex, HttpRequestMethod httpRequestMethod, AuthType authType){
        Map<String, String> otherMap = new HashMap<>() ;
        otherMap.put(RAW_TX,txHex);
        Object data = requestByFcdslOther(SN_18, Version2, DecodeTx, otherMap, authType, httpRequestMethod);
        return data==null ? null: JsonTools.toNiceJson(data);
    }

    public List<Cash> getCashesFree(String fid, int size, List<String> after) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
        fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
        fcdsl.addSort(CD,ASC).addSort(CASH_ID,ASC);
        if(size>0)fcdsl.addSize(size);
        if(after!=null)fcdsl.addAfter(after);
        Object data = requestJsonByFcdsl(SN_2, Version2, CashSearch, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, HttpRequestMethod.POST);
        return ObjectTools.objectToList(data,Cash.class);
    }

    public List<Cash> getCashes(String urlHead, String id, double amount) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        String urlTail = ApiNames.makeUrlTailPath(SN_18,Version2) + ApiNames.GetCashes;
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
//        Object data = requestJsonByFcdsl(SN_18, Version2, CashValid, fcdsl, authType, sessionKey, httpRequestMethod);
//        if(data==null)return null;
//        return objectToList(data,Cash.class);
//    }

    public Map<String, BlockInfo>blockByHeights(HttpRequestMethod httpRequestMethod, AuthType authType, String... heights){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(Strings.HEIGHT).addNewValues(heights);

        Object data = requestJsonByFcdsl(SN_2, Version2, BlockByHeights, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToMap(data,String.class,BlockInfo.class);
    }

    public String getPubKey(String fid, HttpRequestMethod httpRequestMethod, AuthType authType) {
        Object data = requestByIds(httpRequestMethod,SN_2,Version2, FidByIds, authType, fid);
        try {
            return data == null ? null : objectToMap(data, String.class, Address.class).get(fid).getPubKey();
        }catch (Exception e){
            return null;
        }
    }

    public Map<String, BlockInfo> blockByIds(HttpRequestMethod httpRequestMethod,AuthType authType,String... ids){
        Object data = requestByIds(httpRequestMethod, SN_2, Version2, BlockByIds, authType, ids);
        return objectToMap(data,String.class,BlockInfo.class);
    }

    public List<BlockInfo> blockSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version2, BlockSearch,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,BlockInfo.class);
    }

    public List<Cash> cashValid(String fid, Double amount, Long cd, HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Map<String,String> paramMap = new HashMap<>();
        if(amount!=null)paramMap.put(AMOUNT, String.valueOf(amount));
        if(fid!=null)paramMap.put(FID,fid);
        if(cd!=null)paramMap.put(CD, String.valueOf(cd));
        fcdsl.addOther(paramMap);
        return cashValid(fcdsl,httpRequestMethod,authType);
    }

    public List<Cash> cashValid(Fcdsl fcdsl,HttpRequestMethod httpRequestMethod,AuthType authType){
        Object data = requestJsonByFcdsl(SN_18, Version2, CashValid, fcdsl,authType, sessionKey,httpRequestMethod);
        return objectToList(data,Cash.class);
    }

    public Map<String, Cash> cashByIds(HttpRequestMethod httpRequestMethod,AuthType authType,String... ids){
        Object data = requestByIds(httpRequestMethod, SN_2, Version2, CashByIds, authType, ids);
        return objectToMap(data,String.class,Cash.class);
    }

    public List<Cash> cashSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version2, CashSearch,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,Cash.class);
    }
    public List<Utxo> getUtxo(String id, double amount,HttpRequestMethod httpRequestMethod,AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FID).addNewValues(id);
        Fcdsl.setSingleOtherMap(fcdsl, AMOUNT, String.valueOf(amount));

        Object data = requestJsonByFcdsl(SN_18, Version2, GetUtxo, fcdsl,authType, sessionKey,httpRequestMethod);
        return objectToList(data,Utxo.class);
    }
    public Map<String, Address> fidByIds(HttpRequestMethod httpRequestMethod,AuthType authType,String... ids){
        Object data = requestByIds(httpRequestMethod, SN_2, Version2, FidByIds, authType, ids);
        return objectToMap(data,String.class,Address.class);
    }
    public List<Address> fidSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version2, FidSearch,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,Address.class);
    }
    public Map<String, OpReturn> opReturnByIds(HttpRequestMethod httpRequestMethod,AuthType authType,String... ids){
        Object data = requestByIds(httpRequestMethod, SN_2, Version2, OpReturnByIds, authType, ids);
        return objectToMap(data,String.class,OpReturn.class);
    }

    public List<OpReturn> opReturnSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version2, OpReturnSearch,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,OpReturn.class);
    }

    public Map<String, P2SH> p2shByIds(HttpRequestMethod httpRequestMethod,AuthType authType,String... ids){
        Object data = requestByIds(httpRequestMethod, SN_2, Version2, P2shByIds, authType, ids);
        return objectToMap(data,String.class,P2SH.class);
    }
    public List<P2SH> p2shSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version2, P2shSearch,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,P2SH.class);
    }

    public Map<String, TxInfo> txByIds(HttpRequestMethod httpRequestMethod,AuthType authType,String... ids){
        Object data = requestByIds(httpRequestMethod, SN_2, Version2, TxByIds, authType, ids);
        return objectToMap(data,String.class,TxInfo.class);
    }

    public List<TxInfo> txSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, Version2, TxSearch,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,TxInfo.class);
    }

    public List<TxInfo>  txByFid(String fid, int size,String[] last,HttpRequestMethod httpRequestMethod,AuthType authType){
        Fcdsl fcdsl =txByFidQuery(fid, size, last);
        Object data = requestJsonByFcdsl( SN_2, Version2, TxByFid,fcdsl, authType,sessionKey,httpRequestMethod);
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

    public FchChainInfo chainInfo(Long height, HttpRequestMethod httpRequestMethod, AuthType authType) {
        Map<String,String> params=null;
        if(height!=null){
            params = new HashMap<>();
            params.put(FieldNames.HEIGHT, String.valueOf(height));
        }
        Object data;
        if(httpRequestMethod.equals(HttpRequestMethod.POST)) {
            data = requestByFcdslOther(SN_2, Version2, ChainInfo, params, authType, httpRequestMethod);
        }else {
            data = requestJsonByUrlParams(SN_2, Version2, ChainInfo,params,AuthType.FREE);
        }

        return objectToClass(data,FchChainInfo.class);
    }

    public Map<Long,Long> blockTimeHistory(Long startTime, Long endTime, Integer count, HttpRequestMethod httpRequestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);

        Object data;
        if(httpRequestMethod.equals(HttpRequestMethod.POST)) {
            data =requestByFcdslOther(SN_2, Version2, BlockTimeHistory, params, authType, httpRequestMethod);
        }else {
            data =requestJsonByUrlParams(SN_2, Version2, BlockTimeHistory,params,AuthType.FREE);
        }

        return ObjectTools.objectToMap(data,Long.class,Long.class);
    }

    public Map<Long,String> difficultyHistory(Long startTime, Long endTime, Integer count, HttpRequestMethod httpRequestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);
        Object data;
        if(httpRequestMethod.equals(HttpRequestMethod.POST)) {
            data = requestByFcdslOther(SN_2, Version2, DifficultyHistory, params, authType, httpRequestMethod);
        }else {
            data =requestJsonByUrlParams(SN_2, Version2, DifficultyHistory,params,AuthType.FREE);
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

    public Map<Long,String> hashRateHistory(Long startTime, Long endTime, Integer count, HttpRequestMethod httpRequestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);
        Object data;
        if(httpRequestMethod.equals(HttpRequestMethod.POST)) {
            data =requestByFcdslOther(SN_2, Version2, HashRateHistory, params, authType, httpRequestMethod);
        }else {
            data = requestJsonByUrlParams(SN_2, Version2, HashRateHistory,params,AuthType.FREE);
        }
        return ObjectTools.objectToMap(data,Long.class,String.class);
    }

    //Identity APIs
    public Map<String,CidInfo> cidInfoByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(httpRequestMethod,SN_3,Version2, CidInfoByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,CidInfo.class);
    }
    public CidInfo cidInfoById(String id) {
        Map<String,CidInfo> map = cidInfoByIds(HttpRequestMethod.POST, AuthType.FC_SIGN_BODY, id);
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }
    public CidInfo getFidCid(String id) {
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(ID,id);
        Object data = requestJsonByUrlParams(SN_3,Version2,GetFidCid,paramMap,AuthType.FREE);
        return objectToClass(data,CidInfo.class);
    }
    public List<CidInfo> cidInfoSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, Version2, CidInfoSearch,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,CidInfo.class);
    }
    public List<CidHist> cidHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "3");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, Version2, CidHistory,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,CidHist.class);
    }

    public Map<String, String[]> fidCidSeek(String searchStr, HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FID,CID).addNewValue(searchStr);
        Object data = requestJsonByFcdsl(SN_3, Version2, FidCidSeek,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToMap(data,String.class,String[].class);
    }

    public Map<String, String[]> fidCidSeek(String fid_or_cid){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FID,CID).addNewValue(fid_or_cid);
        Map<String,String> map = new HashMap<>();
        map.put(PART,fid_or_cid);
        Object data = requestJsonByUrlParams(SN_3, Version2, FidCidSeek,map, AuthType.FREE);
        return objectToMap(data,String.class,String[].class);
    }

    public Map<String, Nobody> nobodyByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod, SN_3, Version2, NobodyByIds, authType, ids);
        return objectToMap(data,String.class,Nobody.class);
    }

    public List<Nobody> nobodySearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, Version2, NobodySearch,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,Nobody.class);
    }

    public List<CidHist> homepageHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "9");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, Version2, HomepageHistory,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,CidHist.class);
    }

    public List<CidHist> noticeFeeHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "10");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, Version2, NoticeFeeHistory,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,CidHist.class);
    }

    public List<CidHist> reputationHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, Version2,ReputationHistory,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data,CidHist.class);
    }

    public Map<String, String> avatars(String[] fids, HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(fids);
        Object data = requestJsonByFcdsl(SN_3, Version2,Avatars,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToMap(data,String.class,String.class);
    }

    public byte[] getAvatar(String fid){
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(FID,fid);
        Object data = requestBytes(SN_3, Version2,GetAvatar, FcClientEvent.RequestBodyType.NONE,null,paramMap, AuthType.FREE,null,HttpRequestMethod.GET);
        return (byte[])data;
    }


    // Construct
    public Map<String, Protocol> protocolByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(httpRequestMethod,SN_4,Version2, ProtocolByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Protocol.class);
    }

    public Protocol protocolById(String id){
        Map<String, Protocol> map = protocolByIds(HttpRequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Protocol> protocolSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_4, Version2, ProtocolSearch, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, HttpRequestMethod.POST);
        if(data==null)return null;
        return objectToList(data,Protocol.class);
    }


    public List<ProtocolHistory> protocolOpHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_4, Version2, ProtocolOpHistory, fcdsl,authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,ProtocolHistory.class);
    }


    public List<ProtocolHistory> protocolRateHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_4, Version2, ProtocolRateHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,ProtocolHistory.class);
    }

    public Map<String, Code> codeByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(httpRequestMethod,SN_5,Version2, CodeByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Code.class);
    }

    public Code codeById(String id){
        Map<String, Code> map = codeByIds(HttpRequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Code> codeSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_5, Version2, CodeSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Code.class);
    }


    public List<CodeHistory> codeOpHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_5, Version2, CodeOpHistory, fcdsl,authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,CodeHistory.class);
    }

    public List<CodeHistory> codeRateHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_5, Version2, CodeRateHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,CodeHistory.class);
    }

    public Map<String, Service> serviceByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(httpRequestMethod,SN_6,Version2, ServiceByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Service.class);
    }

    public List<Service> serviceSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_6, Version2, ServiceSearch, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, HttpRequestMethod.POST);
        if(data==null)return null;
        return objectToList(data,Service.class);
    }

    public List<ServiceHistory> serviceOpHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_6, Version2, ServiceOpHistory, fcdsl,authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,ServiceHistory.class);
    }

    public List<ServiceHistory> serviceRateHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_6, Version2, ServiceRateHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,ServiceHistory.class);
    }

    public List<Service> getServiceListByType(String type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewMatch().addNewFields(FieldNames.TYPES).addNewValue(type);
        fcdsl.addNewExcept().addNewTerms().addNewFields(ACTIVE).addNewValues(FALSE);
        serviceList = serviceSearch(fcdsl, HttpRequestMethod.POST, AuthType.FC_SIGN_BODY);
        return serviceList;
    }

    public List<Service> getServiceListByOwnerAndType(String owner, @Nullable ServiceType type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(owner);
        fcdsl.addNewExcept().addNewTerms().addNewFields(CLOSED).addNewValues(TRUE);
        if(type!=null)fcdsl.addNewFilter().addNewMatch().addNewFields(FieldNames.TYPES).setValue(type.name());
        serviceList = serviceSearch(fcdsl, HttpRequestMethod.POST, AuthType.FC_SIGN_BODY);
        return serviceList;
    }

    public Service serviceById(String id){
        Map<String, Service> map = serviceByIds(HttpRequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public Map<String, App> appByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(httpRequestMethod,SN_7,Version2, AppByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,App.class);
    }

    public App appById(String id){
        Map<String, App> map = appByIds(HttpRequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<App> appSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_7, Version2, AppSearch, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, HttpRequestMethod.POST);
        if(data==null)return null;
        return objectToList(data,App.class);
    }


    public List<AppHistory> appOpHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_7, Version2, AppOpHistory, fcdsl,authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,AppHistory.class);
    }


    public List<AppHistory> appRateHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_7, Version2, AppRateHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,AppHistory.class);
    }
//Organize
    public Map<String, Group> groupByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_8,Version2, GroupByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Group.class);
    }
    public List<Group> groupSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_8, Version2, GroupSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Group.class);
    }

    public List<GroupHistory> groupOpHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_8, Version2, GroupOpHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,GroupHistory.class);
    }
    public Map<String, String[]> groupMembers(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_8,Version2, GroupMembers, authType, ids);
        return ObjectTools.objectToMap(data,String.class,String[].class);
    }

    public List<MyGroupData> myGroups(String fid,HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        Object data = requestJsonByFcdsl(SN_8, Version2, MyGroups, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,MyGroupData.class);
    }

    public Map<String, Team> teamByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_9,Version2, TeamByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Team.class);
    }
    public List<Team> teamSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, Version2, TeamSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Team.class);
    }

    public List<TeamHistory> teamOpHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, Version2, TeamOpHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }

    public List<TeamHistory> teamRateHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, Version2, TeamRateHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }
    public Map<String, String[]> teamMembers(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_9,Version2, TeamMembers, authType, ids);
        return ObjectTools.objectToMap(data,String.class,String[].class);
    }

    public Map<String, String[]> teamExMembers(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_9,Version2, TeamExMembers, authType, ids);
        return ObjectTools.objectToMap(data,String.class,String[].class);
    }
    public Map<String,TeamOtherPersonsData> teamOtherPersons(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_9,Version2, TeamOtherPersons, authType, ids);
        return ObjectTools.objectToMap(data,String.class,TeamOtherPersonsData.class);
    }
    public List<MyTeamData> myTeams(String fid,HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        Object data = requestJsonByFcdsl(SN_9, Version2, MyTeams, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,MyTeamData.class);
    }

    public Map<String, Box> boxByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_10,Version2, BoxByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Box.class);
    }
    public List<Box> boxSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_10, Version2, BoxSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Box.class);
    }

    public List<BoxHistory> boxHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_10, Version2, BoxHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,BoxHistory.class);
    }

    public Map<String, Contact> contactByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_11,Version2, ContactByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Contact.class);
    }
    public List<Contact> contactSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_11, Version2, ContactSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Contact.class);
    }

    public List<Contact> contactDeleted(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_11, Version2, ContactsDeleted, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Contact.class);
    }

    public Map<String, Secret> secretByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_12,Version2, SecretByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Secret.class);
    }
    public List<Secret> secretSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_12, Version2, SecretSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Secret.class);
    }

    public List<Secret> secretDeleted(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_12, Version2, SecretsDeleted, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Secret.class);
    }


    public Map<String, Mail> mailByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_13,Version2, MailByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Mail.class);
    }
    public List<Mail> mailSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_13, Version2, MailSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }

    public List<Mail> mailDeleted(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_13, Version2, MailsDeleted, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }
    public List<Mail> mailThread(String fidA, String fidB, Long startTime, Long endTime, HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(SENDER,RECIPIENT).addNewValues(fidA);
        if(startTime!=null||endTime!=null){
            Range range = fcdsl.getQuery().addNewRange();
            range.addNewFields(BIRTH_TIME);
            if(startTime!=null)range.addGte(String.valueOf(startTime/1000));
            if(endTime!=null)range.addLt(String.valueOf(endTime/1000));
        }
        fcdsl.addNewFilter().addNewTerms().addNewFields(SENDER,RECIPIENT).addNewValues(fidB);
        Object data = requestJsonByFcdsl(SN_13, Version2, MailThread, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }

    public Map<String, Proof> proofByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_14,Version2, ProofByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Proof.class);
    }
    public List<Proof> proofSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_14, Version2, ProofSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Proof.class);
    }

    public List<ProofHistory> proofHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_14, Version2, ProofHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,ProofHistory.class);
    }


    public Map<String, Statement> statementByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_15,Version2, StatementByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Statement.class);
    }
    public List<Statement> statementSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_15, Version2, StatementSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Statement.class);
    }
    public List<Nid> nidSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_19, Version2, NidSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Nid.class);
    }

    public Map<String, Token> tokenByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_16,Version2, TokenByIds, authType, ids);
        return ObjectTools.objectToMap(data,String.class,Token.class);
    }
    public List<Token> tokenSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, Version2, TokenSearch, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,Token.class);
    }

    public List<TokenHistory> tokenHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, Version2, TokenHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,TokenHistory.class);
    }

    public List<MyGroupData> myTokens(String fid,HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.FID).addNewValues(fid);
        Object data = requestJsonByFcdsl(SN_16, Version2, MyTokens, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,MyGroupData.class);
    }

    public Map<String, TokenHolder> tokenHoldersByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... tokenIds){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(Token_Id).addNewValues(tokenIds);
        Object data = requestJsonByFcdsl(SN_16,Version2, TokenHoldersByIds,fcdsl, authType,sessionKey,httpRequestMethod);
        return ObjectTools.objectToMap(data,String.class,TokenHolder.class);
    }

    public List<TokenHolder> tokenHolderSearch(Fcdsl fcdsl,HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, Version2, TokenHolderSearch,fcdsl,authType,sessionKey,httpRequestMethod);
        return ObjectTools.objectToList(data,TokenHolder.class);
    }
    public List<UnconfirmedInfo> unconfirmed(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_18,Version2, Unconfirmed, authType, ids);
        return ObjectTools.objectToList(data,UnconfirmedInfo.class);
    }

    public Double feeRate(HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_18, Version2, FeeRate, null, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return (Double)data;
    }


    public Map<String, String> addresses(String addrOrPubKey,HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.ADDR_OR_PUB_KEY, addrOrPubKey);
        Object data = requestJsonByFcdsl(SN_17,Version2, Addresses,fcdsl, authType,sessionKey,httpRequestMethod);
        return ObjectTools.objectToMap(data,String.class,String.class);
    }
    public String encrypt(EncryptType encryptType,String message,String key,String fid, HttpRequestMethod httpRequestMethod, AuthType authType){
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
        Object data = requestJsonByFcdsl(SN_17,Version2, Encrypt,fcdsl, authType,sessionKey,httpRequestMethod);
        return objectToClass(data,String.class);
    }
    public boolean verify(String signature,HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, SIGN, signature);
        Object data = requestJsonByFcdsl(SN_17,Version2, Verify,fcdsl, authType,sessionKey,httpRequestMethod);
        if(data==null) return false;
        return (boolean) data;
    }

    public String sha256(String text,HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, text);
        Object data = requestJsonByFcdsl(SN_17,Version2, Sha256,fcdsl, authType,sessionKey,httpRequestMethod);
        return (String) data;
    }
    public String sha256x2(String text,HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, text);
        Object data = requestJsonByFcdsl(SN_17,Version2, Sha256x2,fcdsl, authType,sessionKey,httpRequestMethod);
        return (String) data;
    }
    public String sha256Hex(String hex, HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version2, Sha256Hex,fcdsl, authType,sessionKey,httpRequestMethod);
        return (String) data;
    }

    public String sha256x2Hex(String hex, HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version2, Sha256x2Hex,fcdsl, authType,sessionKey,httpRequestMethod);
        return (String) data;
    }

    public String ripemd160Hex(String hex, HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version2, Ripemd160Hex,fcdsl, authType,sessionKey,httpRequestMethod);
        return (String) data;
    }

    public String KeccakSha3Hex(String hex, HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version2, KeccakSha3Hex,fcdsl, authType,sessionKey,httpRequestMethod);
        return (String) data;
    }

    public String hexToBase58(String hex,HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version2, HexToBase58,fcdsl, authType,sessionKey,httpRequestMethod);
        return (String) data;
    }

    public String checkSum4Hex(String hex, HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17,Version2, CheckSum4Hex,fcdsl, authType,sessionKey,httpRequestMethod);
        return (String) data;
    }

    public String offLineTx(String fromFid, List<SendTo> sendToList, String msg,Long cd,HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        DataForOffLineTx dataForOffLineTx = new DataForOffLineTx();
        dataForOffLineTx.setFromFid(fromFid);
        dataForOffLineTx.setMsg(msg);
        dataForOffLineTx.setSendToList(sendToList);
        dataForOffLineTx.setCd(cd);
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.DATA_FOR_OFF_LINE_TX, JsonTools.toJson(dataForOffLineTx));
        Object data = requestJsonByFcdsl(SN_18,Version2, OffLineTx,fcdsl, authType,sessionKey,httpRequestMethod);
        return (String) data;
    }

    public String circulating(){
        Object data = requestBase(Circulating, FcClientEvent.RequestBodyType.NONE, null, null, null, null, null, FcClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, HttpRequestMethod.GET);
        return (String) data;
    }

    public String totalSupply(){
        Object data = requestBase(TotalSupply, FcClientEvent.RequestBodyType.NONE, null, null, null, null, null, FcClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, HttpRequestMethod.GET);
        return (String) data;
    }

    public String richlist(){
        Object data = requestBase(Richlist, FcClientEvent.RequestBodyType.NONE, null, null, null, null, null, FcClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, HttpRequestMethod.GET);
        return (String) data;
    }

    public String freecashInfo(){
        Object data = requestBase(FreecashInfo, FcClientEvent.RequestBodyType.NONE, null, null, null, null, null, FcClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, HttpRequestMethod.GET);
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
    public Map<String, String> checkSubscription(String endpoint) {
    WebhookRequestBody webhookRequestBody = new WebhookRequestBody();
    webhookRequestBody.setEndpoint(endpoint);
    webhookRequestBody.setOp(CHECK);
    webhookRequestBody.setMethod(ApiNames.NewCashByFids);
    webhookRequestBody.setUserName(apiAccount.getUserId());
    String hookUserId = WebhookRequestBody.makeHookUserId(apiAccount.getProviderId(), apiAccount.getUserId(), ApiNames.NewCashByFids);
    webhookRequestBody.setHookUserId(hookUserId);

    newCashListByIds(webhookRequestBody);
    Object data = this.checkResult();
    return ObjectTools.objectToMap(data,String.class,String.class);//DataGetter.getStringMap(data);
}
    //
    public boolean subscribeWebhook(String endpoint) {
        WebhookRequestBody webhookRequestBody = new WebhookRequestBody();

        webhookRequestBody.setEndpoint(endpoint);
        webhookRequestBody.setMethod(ApiNames.NewCashByFids);
        webhookRequestBody.setUserName(apiAccount.getUserId());
        webhookRequestBody.setOp(SUBSCRIBE);
        newCashListByIds(webhookRequestBody);
        Object data1 =  checkResult();
        Map<String, String> dataMap1 = ObjectTools.objectToMap(data1,String.class,String.class);//DataGetter.getStringMap(data1);
        if(dataMap1==null) return false;
        String hookUserId = dataMap1.get(HOOK_USER_ID);
        if(hookUserId==null) return false;
        return true;
    }

    public List<Cash> newCashListByIds(WebhookRequestBody webhookRequestBody){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, constants.FieldNames.WEBHOOK_REQUEST_BODY, JsonTools.toJson(webhookRequestBody));
        Object data = requestJsonByFcdsl(SN_20, Version2, ApiNames.NewCashByFids, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, HttpRequestMethod.POST);
        if(data==null)return null;
        return objectToList(data,Cash.class);
    }
}
