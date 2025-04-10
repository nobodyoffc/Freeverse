package clients;

import appTools.Shower;
import fcData.FcSession;
import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import constants.*;
import fcData.ReplyBody;
import fcData.Signature;
import com.google.gson.Gson;
import configure.ApiAccount;
import configure.ApiProvider;
import crypto.*;
import fch.Inputer;
import fch.fchData.SendTo;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import feip.feipData.serviceParams.Params;
import org.jetbrains.annotations.NotNull;
import server.ApipApiNames;
import utils.*;
import utils.http.AuthType;
import utils.http.RequestMethod;
import utils.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.FreeApi;
import appTools.Settings;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static constants.UpStrings.SIGN;
import static fcData.AlgorithmId.Constants.BTC_ECDSA_SIGNMSG_NO1_NRC7;
import static server.ApipApiNames.*;
import static constants.FieldNames.LAST_TIME;
import static constants.Strings.DOT_JSON;
import static constants.Strings.URL_HEAD;
import static constants.UpStrings.BALANCE;
import static fcData.AlgorithmId.FC_AesCbc256_No1_NrC7;
import static utils.ObjectUtils.listToMap;
import static utils.ObjectUtils.objectToClass;

public class Client {
    protected static final Logger log = LoggerFactory.getLogger(Client.class);
    public static final String OFF_LINE_TX_VER = "2";
    public static final int WAIT_CONFIRMATION_SECONDS = 10*60;
    protected ApiProvider apiProvider;
    protected ApiAccount apiAccount;
    protected String urlHead;
    protected String via;
    protected ApipClientEvent apipClientEvent;
    protected byte[] symKey;
    protected byte[] sessionKey;
    protected FcSession serverSession;
    protected ApipClient apipClient;
    protected DiskClient diskClient;
    protected boolean isAllowFreeRequest;
    protected Service.ServiceType serviceType;
    protected Gson gson = new Gson();
    protected boolean sessionFreshen=false;
    protected Long bestHeight;
    public Client() {}
    public Client(ApiProvider apiProvider,ApiAccount apiAccount,byte[] symKey) {
        this.apiAccount = apiAccount;
        this.sessionKey = apiAccount.getSessionKey();
        this.apiProvider = apiProvider;
        this.symKey = symKey;
        this.urlHead = apiAccount.getApiUrl();
        this.via = apiAccount.getVia();
        this.serviceType = apiProvider.getType();
    }
    public Client(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        this.symKey = symKey;
        this.apipClient = apipClient;

        this.apiAccount = apiAccount;
        this.serverSession = apiAccount.getSession();
        this.sessionKey = apiAccount.getSessionKey();
        this.urlHead = apiAccount.getApiUrl();
        this.via = apiAccount.getVia();

        this.apiProvider = apiProvider;
        this.serviceType = apiProvider.getType();
    }

    public static ReplyBody getService(String urlHead, String apiVersion, Class<? extends Params> paramsClass){
        ApiUrl apiUrl = new ApiUrl(urlHead,null, apiVersion, ApipApiNames.GET_SERVICE, null,false,null);
        ApipClientEvent clientEvent = Client.get(apiUrl.getUrl());
        if(clientEvent.checkResponse()!=0){
            System.out.println("Failed to get the service from "+apiUrl.getUrl());
            return null;
        }
        ReplyBody responseBody = clientEvent.getResponseBody();
        Service service = new Gson().fromJson((String) responseBody.getData(), Service.class);
        Params.getParamsFromService(service, paramsClass);
        responseBody.setData(service);
        return responseBody;
    }

    public static ApipClientEvent get(String url){
        ApipClientEvent apipClientEvent = new ApipClientEvent();
        ApiUrl apiUrl = new ApiUrl();
        apiUrl.setUrl(url);
        apipClientEvent.setApiUrl(apiUrl);
        apipClientEvent.get();
        return apipClientEvent;
    }

    public static Map<String, Long> loadLastTime(String fid,String oid) {
        String fileName = FileUtils.makeFileName(fid, oid, LAST_TIME, DOT_JSON);
        try {
            Map<String, Long> lastTimeMap1 = JsonUtils.readMapFromJsonFile(null, fileName, String.class, Long.class);
            if (lastTimeMap1 == null) {
                log.debug("Failed to read " + fileName + ".");
                return lastTimeMap1;
            }
            // Clear the existing map and put all entries from lastTimeMap1
            return lastTimeMap1;
        } catch (IOException e) {
            log.debug("Failed to read " + fileName + ".");
        }
        return null;
    }

    public static void saveLastTime(String fid,String oid,Map<String, Long> lastTimeMap) {
        String fileName = FileUtils.makeFileName(fid, oid, LAST_TIME, DOT_JSON);
        JsonUtils.writeMapToJsonFile(lastTimeMap, fileName);
    }

    public Object requestJsonByUrlParams(String ver, String apiName,
                                         @Nullable Map<String,String> paramMap, AuthType authType){
        return requestJsonByUrlParams(null, ver,apiName, paramMap,authType);
    }
    public Object requestJsonByUrlParams(String sn, String ver, String apiName,
                                         @Nullable Map<String,String> paramMap, AuthType authType){
        String urlTailPath = ApiUrl.makeUrlTailPath(sn, ver);
        if(urlTailPath==null)urlTailPath="";
        String urlTail = urlTailPath +apiName;
        if(authType==null) {
            if (isAllowFreeRequest || sessionKey == null) authType = AuthType.FREE;
            else authType = AuthType.FC_SIGN_URL;
        }
        return requestBase(urlTail, ApipClientEvent.RequestBodyType.FCDSL, null, null, null, paramMap, null, ApipClientEvent.ResponseBodyType.FC_REPLY, null, null, authType, null, RequestMethod.GET
        );
    }
    public Object requestFile(String ver, String apiName, Fcdsl fcdsl, String responseFileName, @Nullable String responseFilePath, AuthType authType, byte[] authKey, RequestMethod method){
        return requestFile(null,  ver, apiName,fcdsl,responseFileName, responseFilePath, authType, authKey, method);
    }
    public Object requestFile(String sn, String ver, String apiName, Fcdsl fcdsl, String responseFileName, @Nullable String responseFilePath, AuthType authType, byte[] authKey, RequestMethod method){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        requestBase(urlTail, ApipClientEvent.RequestBodyType.FCDSL, fcdsl, null, null, null, null, ApipClientEvent.ResponseBodyType.FILE, responseFileName, responseFilePath, authType, authKey, method);
        ReplyBody responseBody = apipClientEvent.getResponseBody();
        if(responseBody !=null)return responseBody.getData();
        return null;
    }
    public Object requestJsonByFcdsl(String ver, String apiName, @Nullable Fcdsl fcdsl, AuthType authType, @Nullable byte[] authKey, RequestMethod method){
        return requestJsonByFcdsl(null, ver, apiName, fcdsl, authType, authKey, method);
    }
    public Object requestJsonByFcdsl(String sn, String ver, String apiName, @Nullable Fcdsl fcdsl, AuthType authType, @Nullable byte[] authKey, RequestMethod method){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;

        if(authType==null || authKey==null)
            authType = AuthType.FREE;

        return requestBase(urlTail, ApipClientEvent.RequestBodyType.FCDSL, fcdsl, null, null, null, null, ApipClientEvent.ResponseBodyType.FC_REPLY, null, null, authType, authKey, method
        );
    }

    public Object requestFileByFcdsl(String sn, String ver, String apiName, @Nullable Fcdsl fcdsl, String responseFileName, @Nullable String responseFilePath, @Nullable byte[] authKey, RequestMethod method){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        AuthType authType;
        if(authKey!=null)authType=AuthType.FC_SIGN_BODY;
        else authType = AuthType.FREE;

        return requestBase(urlTail, ApipClientEvent.RequestBodyType.FCDSL, fcdsl, null, null, null, null, ApipClientEvent.ResponseBodyType.FILE, responseFileName, responseFilePath, authType, authKey, method
        );
    }
    public Object requestJsonByFile(String ver, String apiName,
                                    @Nullable Map<String,String>  paramMap,
                                    @Nullable byte[] authKey,
                                    String requestFileName){
        return requestJsonByFile(null,ver,apiName,paramMap,authKey,requestFileName);
    }
    public Object requestJsonByFile(String sn, String ver, String apiName,
                                    @Nullable Map<String,String>  paramMap,
                                    @Nullable byte[] authKey,
                                    String requestFileName){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        AuthType authType;
        if(authKey!=null)authType=AuthType.FC_SIGN_URL;
        else authType = AuthType.FREE;

        return requestBase(urlTail, ApipClientEvent.RequestBodyType.FILE, null, null, null, paramMap, requestFileName, ApipClientEvent.ResponseBodyType.FC_REPLY, null, null, authType, authKey, RequestMethod.POST
        );
    }

    public Object requestBase(String urlTail, ApipClientEvent.RequestBodyType requestBodyType, @Nullable Fcdsl fcdsl, @Nullable String requestBodyStr, @Nullable byte[] requestBodyBytes, @Nullable Map<String,String> paramMap, String requestFileName, ApipClientEvent.ResponseBodyType responseBodyType, String responseFileName, String responseFilePath, AuthType authType, @Nullable byte[] authKey, RequestMethod httpMethod){

        if(httpMethod.equals(RequestMethod.GET)){
            requestBodyType= ApipClientEvent.RequestBodyType.NONE;
            if(fcdsl!=null) {
                String urlParamsStr = Fcdsl.fcdslToUrlParams(fcdsl);
                paramMap = Fcdsl.urlParamsStrToMap(urlParamsStr);
            }
        }

        apipClientEvent = new ApipClientEvent(urlHead,urlTail,requestBodyType,fcdsl,requestBodyStr,requestBodyBytes,paramMap,requestFileName, responseBodyType,responseFileName,responseFilePath,authType, authKey, via);
        try {
            switch (httpMethod) {
                case GET -> apipClientEvent.get(authKey);
                case POST -> apipClientEvent.post(authKey);
                default -> apipClientEvent.setCode(CodeMessage.Code1022NoSuchMethod);
            }
        }catch (Exception e){
            log.debug("Failed to request. Error:{}",e.getMessage());
            return null;
        }
        Object result = checkResult();

        String apiName = HttpUtils.getApiNameFromUrl(urlTail);
        if(apiName==null||apiName.equals(SIGN_IN) || apiName.equals(SIGN_IN_ECC))return result;
        //If any request besides signIn or signInEcc got a session response, it means the client signed in again. So repeat the request.
        try {
            serverSession = (FcSession) result;
            if (serverSession == null || serverSession.getId() == null) return result;
            else return requestBase(urlTail, requestBodyType, fcdsl, requestBodyStr, requestBodyBytes, paramMap, requestFileName, responseBodyType, responseFileName, responseFilePath, authType, authKey, httpMethod);
        }catch (ClassCastException e){
            return result;
        }
    }

    public Object request(String sn, String ver, String apiName, ApipClientEvent.RequestBodyType requestBodyType, @Nullable Fcdsl fcdsl, @Nullable String requestBodyStr, @Nullable byte[] requestBodyBytes, @Nullable Map<String,String> paramMap, String requestFileName, ApipClientEvent.ResponseBodyType responseBodyType, String responseFileName, String responseFilePath, AuthType authType, @Nullable byte[] authKey, RequestMethod httpMethod){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
       return requestBase(urlTail, requestBodyType, fcdsl, requestBodyStr, requestBodyBytes, paramMap, requestFileName, responseBodyType, responseFileName, responseFilePath, authType, authKey, httpMethod);
    }

    public Object requestBytes(String sn, String ver, String apiName, ApipClientEvent.RequestBodyType requestBodyType, @Nullable Fcdsl fcdsl, @Nullable Map<String,String> paramMap, AuthType authType, @Nullable byte[] authKey, RequestMethod httpMethod){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        return requestBase(urlTail, requestBodyType, fcdsl, null, null, paramMap, null, ApipClientEvent.ResponseBodyType.BYTES, null, null, authType, authKey, httpMethod);
    }

    public Object checkResult(){
        if(apipClientEvent.getResponseBodyType().equals(ApipClientEvent.ResponseBodyType.STRING))
            return apipClientEvent.getResponseBodyStr();

        if(apipClientEvent ==null || apipClientEvent.getCode()==null)return null;

        if(apipClientEvent.getCode()!= CodeMessage.Code0Success) {
            if (apipClientEvent.getResponseBody()== null) {
                log.debug("ResponseBody is null when requesting "+this.apipClientEvent.getApiUrl().getUrl());
            } else {
                log.debug(apipClientEvent.getResponseBody().getCode() + ":" + apipClientEvent.getResponseBody().getMessage());
                if (apipClientEvent.getResponseBody().getData() != null)
                    log.debug(JsonUtils.toJson(apipClientEvent.getResponseBody().getData()));
            }

            if (apipClientEvent.getCode() == CodeMessage.Code1004InsufficientBalance) {

                if(apipClient==null && this.serviceType.equals(Service.ServiceType.APIP)){
                    apipClient = (ApipClient) this;
                }

                Double paid = apiAccount.buyApi(symKey, apipClient, null);

                if (paid == null) {
                    if (apipClientEvent.getResponseBody().getCode().equals(CodeMessage.Code1026InsufficientFchOnChain)) {
                        System.out.println("Send some FCH to " + apiAccount.getUserId() + "...");
                        for(int i=0;i<10;i++) {
                            waitSeconds(WAIT_CONFIRMATION_SECONDS);
                            paid = apiAccount.buyApi(symKey, apipClient, null);
                            if (paid!=null) break;
                            System.out.println("Waiting...");
                        }
                    }
                }
                System.out.println("Checking the balance...");

                while(true){
                    waitSeconds(10);
                    serverSession = checkSignInEcc();
                    apiAccount.setSession(serverSession);
                    apiAccount.setSessionKey(sessionKey);
                    if (serverSession != null)return serverSession;
                }
            }

            if (apipClientEvent.getCode() == CodeMessage.Code1002SessionNameMissed || apipClientEvent.getCode() == CodeMessage.Code1009SessionTimeExpired) {
                sessionFreshen=false;
                sessionKey = apiAccount.freshSessionKey(symKey, this.serviceType, RequestBody.SignInMode.NORMAL, null);
                if (sessionKey != null) sessionFreshen=true;
            }

            return null;
        }
        checkBalance(apiAccount, apipClientEvent, symKey,apipClient);
        switch (apipClientEvent.getResponseBodyType()){
            case BYTES -> {
                return apipClientEvent.getResponseBodyBytes();
            }
            case FC_REPLY -> {
                if(apipClientEvent.getResponseBody()!=null) {
                    if (apipClientEvent.getResponseBody().getData() == null && apipClientEvent.getCode() == 0)
                        return true;
                    if (apipClientEvent.getResponseBody().getBestHeight() != null) {
                        bestHeight = apipClientEvent.getResponseBody().getBestHeight();
                        if(apiAccount!=null)apiAccount.setBestHeight(bestHeight);
                    }

                }
                return apipClientEvent.getResponseBody().getData();
            }
            case FILE -> {
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    @org.jetbrains.annotations.Nullable
    private FcSession checkSignInEcc() {
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0) return null;
        byte[] priKey = cryptoDataByte.getData();
        apipClientEvent = new ApipClientEvent(apiAccount.getApiUrl(),null, VERSION_1, ApipApiNames.SIGN_IN_ECC);
        apipClientEvent.signInPost(apiAccount.getVia(), priKey, RequestBody.SignInMode.NORMAL);
        Object data = apipClientEvent.getResponseBody().getData();
        try{
            serverSession =gson.fromJson(gson.toJson(data), FcSession.class);
            serverSession = makeSessionFromSignInEccResult(symKey, decryptor, priKey, serverSession);
        } catch (Exception ignore){return null;}
        return serverSession;
    }

    private static void waitSeconds(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException ignore) {}
    }


    public static Long checkBalance(ApiAccount apiAccount, final ApipClientEvent apipClientEvent, byte[] symKey, ApipClient apipClient) {
        if(apipClientEvent ==null)return null;
        if(apipClientEvent.getResponseBody()==null)return null;
        Long balance = null;
        if( apipClientEvent.getResponseBody().getBalance()!=null)
            balance = apipClientEvent.getResponseBody().getBalance();
        else if(apipClientEvent.getResponseHeaderMap()!=null&& apipClientEvent.getResponseHeaderMap().get(BALANCE)!=null)
                balance = Long.valueOf(apipClientEvent.getResponseHeaderMap().get(BALANCE));
        if(balance==null)return null;
        apiAccount.setBalance(balance);

        String priceStr;
        if(apiAccount.getServiceParams()==null) {
            System.out.println("The service parameters is null in the API account.");
            return null;
        }
        else if(apiAccount.getServiceParams().getPricePerKBytes()==null)
            priceStr=apiAccount.getApipParams().getPricePerRequest();
        else priceStr =apiAccount.getApipParams().getPricePerKBytes();
        long price = FchUtils.coinStrToSatoshi(priceStr);

        if(balance!=0 && balance < price * ApiAccount.minRequestTimes){
            double topUp = apiAccount.buyApi(symKey,apipClient, null);
            if(topUp==0){
                log.debug("Failed to buy APIP service.");
                return null;
            }
            apiAccount.setBalance(balance + utils.FchUtils.coinToSatoshi(topUp));
        }else {

            return balance/price;
        }
        return null;
    }

    public static String getSessionKeySign(byte[] sessionKeyBytes, byte[] dataBytes) {
        return HexFormat.of().formatHex(Hash.sha256x2(BytesUtils.bytesMerger(dataBytes, sessionKeyBytes)));
    }

    public static boolean checkSign(String msg, String sign, String symKey) {
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        return checkSign(msgBytes, sign, HexFormat.of().parseHex(symKey));
    }

    public static boolean checkSign(byte[] msgBytes, String sign, byte[] symKey) {
        if (sign == null || msgBytes == null) return false;
        byte[] signBytes = BytesUtils.bytesMerger(msgBytes, symKey);
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));
        return (sign.equals(doubleSha256Hash));
    }

    public static String getSessionName(byte[] sessionKey) {
        if (sessionKey == null) return null;
        return HexFormat.of().formatHex(Arrays.copyOf(sessionKey, 6));
    }

//    public boolean pingFree(ApiType apiType) {
//        Object data = ping(Version1,HttpRequestMethod.GET,AuthType.FREE, null);
////        requestBase(Ping, FcClientEvent.RequestBodyType.NONE, null, null, null, null, null, FcClientEvent.ResponseBodyType.FC_REPLY, null, null, AuthType.FREE, null, HttpRequestMethod.GET);
////        Object data = checkResult();
//        setFreeApiState(data,apiType);
//        return (boolean) data;
//    }

    private void setFreeApiState(Object data, Service.ServiceType serviceType) {
        Map<String, FreeApi> freeApiMap = listToMap(Settings.freeApiListMap.get(serviceType),URL_HEAD);//listToMap(config.getFreeApipUrlList(),URL_HEAD);

        if(data ==null){
            if(freeApiMap !=null && freeApiMap.get(this.urlHead)!=null ){
                freeApiMap.get(this.urlHead).setActive(false);
            }
            return;
        }
        if(freeApiMap==null)freeApiMap = new HashMap<>();
        if(freeApiMap.get(this.urlHead)==null){
            FreeApi freeApi = new FreeApi(this.urlHead,true, this.serviceType);
            freeApiMap.put(this.urlHead,freeApi);
        }
        freeApiMap.get(this.urlHead).setActive(true);
    }

    public Object ping(String version, RequestMethod requestMethod, AuthType authType, Service.ServiceType serviceType) {
        String urlTail = "/"+version+"/"+ PING;
        Object data = requestBase(urlTail, ApipClientEvent.RequestBodyType.FCDSL, null, null, null, null, null, ApipClientEvent.ResponseBodyType.FC_REPLY, null, null, authType, sessionKey, requestMethod);
        if(requestMethod.equals(RequestMethod.POST)) {
            return checkBalance(apiAccount, apipClientEvent, symKey, apipClient);
        }else  {
            if(serviceType !=null && Settings.freeApiListMap!=null)setFreeApiState(data, serviceType);
            return data;
        }
    }

    private void signIn(byte[] priKey, @Nullable RequestBody.SignInMode mode) {
        apipClientEvent = new ApipClientEvent(apiAccount.getApiUrl(),null, VERSION_1, ApipApiNames.SIGN_IN);
        apipClientEvent.signInPost(apiAccount.getVia(), priKey, mode);
        int paymentsSize;
        if(apiAccount.getPayments()!=null) paymentsSize = apiAccount.getPayments().size();
        else paymentsSize=0;
        Object data = checkResult();
        if(data==null){
            if(apipClientEvent.getCode()==1004 && apiAccount.getPayments().size()>paymentsSize){
                apipClientEvent.signInPost(apiAccount.getVia(), priKey, mode);
                data = checkResult();
                if(data==null) return;
            } else return;
        }
        serverSession = gson.fromJson(gson.toJson(data), FcSession.class);
        apipClientEvent.getResponseBody().setData(serverSession);
    }

    public void signInEcc(byte[] priKey, @Nullable RequestBody.SignInMode mode) {
        apipClientEvent = new ApipClientEvent(apiAccount.getApiUrl(),null, VERSION_1, ApipApiNames.SIGN_IN_ECC);
        apipClientEvent.signInPost(apiAccount.getVia(), priKey, mode);
        int paymentsSize;
        if(apiAccount.getPayments()!=null)
            paymentsSize= apiAccount.getPayments().size();
        else paymentsSize=0;

        Object data = checkResult();
        if(data==null){
            if(apipClientEvent.getCode()==1004 && apiAccount.getPayments().size()>paymentsSize){
                apipClientEvent.signInPost(apiAccount.getVia(), priKey, mode);
                data = checkResult();
                if(data==null) return;
            } else if(apipClientEvent.getCode()==1026){
                return;
            }
        }
         serverSession = gson.fromJson(gson.toJson(data), FcSession.class);
        apipClientEvent.getResponseBody().setData(serverSession);
    }

    public ApiProvider getApiProvider() {
        return apiProvider;
    }

    public void setApiProvider(ApiProvider apiProvider) {
        this.apiProvider = apiProvider;
    }

    public ApiAccount getApiAccount() {
        return apiAccount;
    }

    public void setApiAccount(ApiAccount apiAccount) {
        this.apiAccount = apiAccount;
    }

    public ApipClientEvent getFcClientEvent() {
        return apipClientEvent;
    }

    public void setFcClientEvent(ApipClientEvent clientData) {
        this.apipClientEvent = clientData;
    }

    public byte[] getSymKey() {
        return symKey;
    }

    public void setSymKey(byte[] symKey) {
        this.symKey = symKey;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(byte[] sessionKey) {
        this.sessionKey = sessionKey;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public void setApipClient(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    public FcSession signIn(ApiAccount apiAccount, RequestBody.SignInMode mode, byte[] symKey) {

        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] priKey = cryptoDataByte.getData();

//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(apiAccount.getUserPriKeyCipher(),symKey);
        signIn(priKey, mode);
        if(apipClientEvent ==null|| apipClientEvent.getResponseBody()==null|| apipClientEvent.getResponseBody().getData()==null)
            return null;
         serverSession = (FcSession) apipClientEvent.getResponseBody().getData();
        if(serverSession ==null|| serverSession.getKey()==null)return null;
        byte[] sessionKey = Hex.fromHex(serverSession.getKey());

        apiAccount.setSessionKey(sessionKey);

        String sessionName = FcSession.makeSessionName(Hex.fromHex(serverSession.getKey()));
        String fid = KeyTools.priKeyToFid(priKey);
        serverSession.setUserId(fid);
        serverSession.setId(sessionName);

        if(serverSession.getKeyCipher()==null) {
            Encryptor encryptor = new Encryptor();
            CryptoDataByte cryptoDataByte2 = encryptor.encryptBySymKey(sessionKey, symKey);
            if (cryptoDataByte2.getCode() != 0) return null;
            String sessionKeyCipher = cryptoDataByte2.toJson();
            serverSession.setKeyCipher(sessionKeyCipher);
        }

        apiAccount.setSession(serverSession);
        apiAccount.setSessionKey(sessionKey);
        return serverSession;
    }

    public FcSession signInEcc(ApiAccount apiAccount, RequestBody.SignInMode mode, byte[] symKey, BufferedReader br) {

        Decryptor decryptor = new Decryptor();
        if(apiAccount.getUserPriKeyCipher()==null)
            return signInEccOffLine(apiAccount, mode, symKey, br);

        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] priKey = cryptoDataByte.getData();

        signInEcc(priKey, mode);

        if(apipClientEvent ==null|| apipClientEvent.getResponseBody()==null|| apipClientEvent.getResponseBody().getData()==null)
            return null;
        FcSession rawSession = (FcSession) apipClientEvent.getResponseBody().getData();
        if(rawSession ==null|| rawSession.getKeyCipher()==null)return null;

        serverSession = makeSessionFromSignInEccResult(symKey, decryptor, priKey, rawSession);
        if(serverSession ==null)return null;
        apiAccount.setSession(serverSession);
        apiAccount.setSessionKey(sessionKey);

        return serverSession;
    }

    private FcSession signInEccOffLine(ApiAccount apiAccount, RequestBody.SignInMode mode, byte[] symKey, BufferedReader br) {
        apipClientEvent = new ApipClientEvent(apiAccount.getApiUrl(),null, VERSION_1, ApipApiNames.SIGN_IN_ECC);
        apipClientEvent.makeSignInRequest(via, mode);
        String myFid = apiAccount.getUserId();

        serverSession = inputSession(symKey, br);
        if(serverSession!=null)return serverSession;
        Shower.showTextAndQR(apipClientEvent.getRequestBodyStr(),"No priKey to sign in. Please sign below request with the priKey with the algorithm "+BTC_ECDSA_SIGNMSG_NO1_NRC7+":");

        while (true) {
            System.out.print("Input the signature. ");
            String sign = Inputer.inputStringMultiLine(br);
            Signature signature;
            try {
                if(sign==null || sign.equals("")){
                    if (Inputer.askIfYes(br, "Failed. Try again?")) continue;
                    else return null;
                }
                signature = Signature.parseSignature(sign.trim());//Signature.fromJson(sign);
                if(signature==null ) {
                    if (Inputer.askIfYes(br, "Failed. Try again?")) continue;
                        else return null;
                }
                if(!myFid.equals(signature.getFid())) {
                    if (Inputer.askIfYes(br, "The signer "+signature.getFid()+" is not "+myFid+". Try again?")) continue;
                    else return null;
                }
                if(!signature.verify()) {
                    if (Inputer.askIfYes(br, "Failed to be verified. Try again?")) continue;
                    else return null;
                }
                if(! apipClientEvent.getRequestBodyStr().equals(signature.getMsg())){
                    if(Inputer.askIfYes(br,"The signed message is not the original request. Try again?"))continue;
                    else return null;
                }

                apipClientEvent.requestHeaderMap.put(UpStrings.FID, myFid);
                apipClientEvent.requestHeaderMap.put(SIGN, signature.getSign());

                apipClientEvent.post();

                ReplyBody responseBody = apipClientEvent.responseBody;
                if(responseBody !=null){
                    apipClientEvent.code = responseBody.getCode();
                    apipClientEvent.message = responseBody.getMessage();

                    if(apipClientEvent.getResponseBody().getCode()==CodeMessage.Code1004InsufficientBalance){
                        System.out.println(responseBody.getMessage());

                        Double paid = apiAccount.buyApi(symKey, apipClient, br);
                        if(paid!=null && paid>0){
                            ApipClient apipClient1 = (ApipClient)apiAccount.getClient();
                            FcSession rawSession;

                            rawSession = trySignIn(apiAccount, symKey, br, apipClient1);

                            return tryConvertSessionKey(symKey, br, apipClient1, rawSession);
                        }
                        return null;
                    }
                }else{
                    apipClientEvent.code = 1020;
                    apipClientEvent.message = "Failed to sign in.";
                }
                    if(apipClientEvent.getResponseBody()==null|| apipClientEvent.getResponseBody().getData()==null)
                        return null;
                Object data = apipClientEvent.getResponseBody().getData();

                FcSession rawSession = objectToClass(data,FcSession.class);

                if(rawSession ==null|| rawSession.getKeyCipher()==null){
                    System.out.println("Got wrong session from the server.");
                        return null;
                }

                return convertSession(rawSession, symKey, br);
            }catch (Exception e){
                    return null;
            }
        }
    }

    private FcSession inputSession(byte[] symKey, BufferedReader br) {
        while (true){
            String input = Inputer.inputString(br, "Input the session key hex:");
            if(!Hex.isHexString(input)){
                if(Inputer.askIfYes(br,"It's not hex. Give up importing?"))return null;
                else continue;
            }

            FcSession fcSession = new FcSession();
            fcSession.setKey(input);
            byte[] keyBytes = Hex.fromHex(input);
            fcSession.setKeyBytes(keyBytes);
            fcSession.setKeyCipher(Encryptor.encryptBySymKeyToJson(keyBytes, symKey));
            fcSession.makeId();
            serverSession = fcSession;
            apiAccount.setSession(serverSession);
            return serverSession;
        }
    }

    private FcSession tryConvertSessionKey(byte[] symKey, BufferedReader br, ApipClient apipClient1, FcSession rawSession) {
        while(true) {
            convertSession(rawSession, symKey, br);
            Object result = apipClient1.ping(VERSION_1, RequestMethod.POST, AuthType.FC_SIGN_BODY, Service.ServiceType.APIP);
            if (result != null) return serverSession;
            System.out.println("Failed. Try again.");
        }
    }

    @NotNull
    private static FcSession trySignIn(ApiAccount apiAccount, byte[] symKey, BufferedReader br, ApipClient apipClient1) {
        FcSession rawSession;
        while(true) {
            rawSession = apipClient1.signInEcc(apiAccount, RequestBody.SignInMode.NORMAL, symKey, br);
            if (rawSession != null) break;
            System.out.println("Failed. Wait a moment.");
            waitSeconds(60);
        }
        return rawSession;
    }

    private FcSession convertSession(FcSession rawSession, byte[] symKey, BufferedReader br) {
        String sessionKeyCipher1 = rawSession.getKeyCipher();

        Shower.showTextAndQR(sessionKeyCipher1,"Got the cipher of the new sessionKey. Please decrypt with your priKey:");

        while(true) {
            String input = Inputer.inputString(br, "Input the decrypted session key:");
            if(!Hex.isHexString(input)){
                System.out.println("The sessionKey should be a hex.");
                continue;
            }

            rawSession.setKey(input);
            String newKeyCipher = Encryptor.encryptBySymKeyToJson(Hex.fromHex(input), symKey);
            rawSession.setKeyCipher(newKeyCipher);
            serverSession = rawSession;
            apiAccount.setSession(serverSession);
            return serverSession;
        }
    }

    private void prepareOffLinePayment(ApiAccount apiAccount) {

        try {
            ApipParams params = (ApipParams) apiAccount.getApipParams();
            ApipClient apipClient1 = (ApipClient) this;

            List<SendTo> sendToList = new ArrayList<>();
            SendTo sendTo = new SendTo();
            sendTo.setFid(params.getDealer());
            double amount = Double.parseDouble(params.getMinPayment());
            sendTo.setAmount(amount);
            sendToList.add(sendTo);


            Double result = apiAccount.buyApi(symKey, apipClient1, null);

        }catch (Exception e){
            System.out.println();
        }
    }


    public FcSession makeSessionFromSignInEccResult(byte[] symKey, Decryptor decryptor, byte[] priKey, FcSession fcSession) {
        String sessionKeyCipher1 = fcSession.getKeyCipher();
        String fid = KeyTools.priKeyToFid(priKey);
        CryptoDataByte cryptoDataByte1 =
                decryptor.decryptJsonByAsyOneWay(sessionKeyCipher1, priKey);
        if (cryptoDataByte1.getCode() != 0) return null;
        sessionKey = cryptoDataByte1.getData();

        Encryptor encryptor = new Encryptor(FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte2 = encryptor.encryptBySymKey(sessionKey, symKey);
        if (cryptoDataByte2.getCode() != 0) return null;
        String newCipher = cryptoDataByte2.toJson();
        fcSession.setKeyCipher(newCipher);

        String sessionName = FcSession.makeSessionName(sessionKey);

        fcSession.setKey(Hex.toHex(sessionKey));
        fcSession.setId(sessionName);
        fcSession.setUserId(fid);
        return fcSession;
    }

    public void close(){
    }

    public boolean isSessionFreshen() {
        return sessionFreshen;
    }

    public void setSessionFreshen(boolean sessionFreshen) {
        this.sessionFreshen = sessionFreshen;
    }


    public void setClientEvent(ApipClientEvent apipClientEvent) {
        this.apipClientEvent = apipClientEvent;
    }

//    public String getSignInUrlTailPath() {
//        return signInUrlTailPath;
//    }

//    public void setSignInUrlTailPath(String signInUrlTailPath) {
//        this.signInUrlTailPath = signInUrlTailPath;
//    }

    public Service.ServiceType getApiType() {
        return serviceType;
    }

    public void setApiType(Service.ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(long bestHeight) {
        this.bestHeight = bestHeight;
    }

    public boolean isAllowFreeRequest() {
        return isAllowFreeRequest;
    }

    public void setAllowFreeRequest(boolean allowFreeRequest) {
        isAllowFreeRequest = allowFreeRequest;
    }

    public String getUrlHead() {
        return urlHead;
    }

    public void setUrlHead(String urlHead) {
        this.urlHead = urlHead;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public Object requestByIds(RequestMethod requestMethod, String sn, String ver, String apiName, AuthType authType, String... ids) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(ids);
        return requestJsonByFcdsl(sn, ver, apiName, fcdsl, authType, sessionKey, requestMethod);
    }

    public Object requestByFcdslOther(String sn, String ver, String apiName, Map<String, String> other, AuthType authType, RequestMethod requestMethod) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addOther(other);
        return requestJsonByFcdsl(sn, ver, apiName, fcdsl, authType, sessionKey, requestMethod);
    }

    public FcSession getServerSession() {
        return serverSession;
    }

    public void setServerSession(FcSession serverSession) {
        this.serverSession = serverSession;
    }

    public DiskClient getDiskClient() {
        return diskClient;
    }

    public void setDiskClient(DiskClient diskClient) {
        this.diskClient = diskClient;
    }

    public Service.ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(Service.ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }
}
