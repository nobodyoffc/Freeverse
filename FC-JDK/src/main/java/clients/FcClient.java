package clients;

import config.Configure;
import core.crypto.*;
import data.apipData.RequestBody;
import data.apipData.SignInMode;
import data.fcData.*;
import data.feipData.ServiceType;
import server.ApipApi;
import ui.Shower;
import data.apipData.Fcdsl;
import constants.*;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import core.fch.Inputer;
import data.feipData.Service;
import utils.*;
import utils.http.AuthType;
import utils.http.RequestMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.FreeApi;
import config.Settings;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static constants.FieldNames.MODE;
import static server.ApipApi.*;
import static constants.FieldNames.LAST_TIME;
import static constants.Strings.DOT_JSON;
import static constants.Strings.URL_HEAD;
import static constants.UpStrings.BALANCE;
import static utils.ObjectUtils.listToMap;

public abstract class FcClient {
    protected static final Logger log = LoggerFactory.getLogger(FcClient.class);
    public static final int WAIT_CONFIRMATION_SECONDS = 10*60;
    private static final String API_VER = "apiVer";
    protected ApiProvider apiProvider;
    protected ApiAccount apiAccount;
    protected String urlHead;
    protected String via;
    protected ApipClientEvent apipClientEvent;
    protected byte[] symkey;
    protected byte[] sessionKey;
    protected FcSession serverSession;
    protected ApipClient apipClient;
    protected DiskClient diskClient;
    protected boolean isAllowFreeRequest;
    protected ServiceType serviceType;
    protected Gson gson = new Gson();
    protected boolean sessionFreshen=false;
    protected Long bestHeight;
    protected String tryKey;
    public FcClient() {}
    public FcClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symkey) {
        this.apiAccount = apiAccount;
        this.sessionKey = apiAccount.getSessionKey();
        this.apiProvider = apiProvider;
        this.symkey = symkey;
        this.urlHead = apiAccount.getApiUrl();
        this.via = apiAccount.getVia();
        this.serviceType = apiProvider.fetchServiceType();
    }
    public FcClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symkey, ApipClient apipClient) {
        this.symkey = symkey;
        this.apipClient = apipClient;

        this.apiAccount = apiAccount;
        this.serverSession = apiAccount.getSession();
        this.sessionKey = apiAccount.getSessionKey();
        this.urlHead = apiAccount.getApiUrl();
        this.via = apiAccount.getVia();

        this.apiProvider = apiProvider;
        this.serviceType = apiProvider.fetchServiceType();
    }

    public static ReplyBody getService(String urlHead, String apiVersion){
        ApiUrl apiUrl = new ApiUrl(urlHead,null, apiVersion, ApipApi.GET_SERVICE.getName(), null,false,null);
        ApipClientEvent clientEvent = FcClient.get(apiUrl.getUrl());
        if(clientEvent.checkResponse()!=0){
            System.out.println("Failed to get the service from "+apiUrl.getUrl());
            return null;
        }
        ReplyBody responseBody = clientEvent.getResponseBody();
        Service service = new Gson().fromJson((String) responseBody.getData(), Service.class);
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
//                log.debug("Failed to read " + fileName + ".");
                return lastTimeMap1;
            }
            // Clear the existing map and put all entries from lastTimeMap1
            return lastTimeMap1;
        } catch (IOException e) {
//            log.debug("Failed to read " + fileName + ".");
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
        String urlTail = ApiUrl.makeUrlTail(sn, apiName,ver);
        if(paramMap==null)paramMap = new HashMap<>();
        paramMap.put(API_VER,ver);
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
        String urlTail = ApiUrl.makeUrlTail(sn, apiName,ver);
        requestBase(urlTail, ApipClientEvent.RequestBodyType.FCDSL, fcdsl, null, null, null, null, ApipClientEvent.ResponseBodyType.FILE, responseFileName, responseFilePath, authType, authKey, method);
        ReplyBody responseBody = apipClientEvent.getResponseBody();
        if(responseBody !=null)return responseBody.getData();
        return null;
    }
    public Object requestJsonByFcdsl(String ver, String apiName, @Nullable Fcdsl fcdsl, AuthType authType, @Nullable byte[] authKey, RequestMethod method){
        return requestJsonByFcdsl(null, ver, apiName, fcdsl, authType, authKey, method);
    }
    public Object requestJsonByFcdsl(String sn, String ver, String apiName, @Nullable Fcdsl fcdsl, AuthType authType, @Nullable byte[] authKey, RequestMethod method){
        String urlTail = ApiUrl.makeUrlTail(sn, apiName,ver);

        if(authType==null || authKey==null)
            authType = AuthType.FREE;

        return requestBase(urlTail, ApipClientEvent.RequestBodyType.FCDSL, fcdsl, null, null, null, null, ApipClientEvent.ResponseBodyType.FC_REPLY, null, null, authType, authKey, method
        );
    }

    public Object requestFileByFcdsl(String sn, String ver, String apiName, @Nullable Fcdsl fcdsl, String responseFileName, @Nullable String responseFilePath, @Nullable byte[] authKey, RequestMethod method){
        String urlTail = ApiUrl.makeUrlTail(sn, apiName,ver);
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
        String urlTail = ApiUrl.makeUrlTail(sn, apiName,ver);
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

        apipClientEvent = new ApipClientEvent(urlHead,urlTail, requestBodyType,fcdsl,requestBodyStr,requestBodyBytes,paramMap,requestFileName, responseBodyType,responseFileName,responseFilePath,authType, authKey, via);

        byte[] myPrikey = null;
        String itsPubkey = null;

        if(authType.equals(AuthType.SYMKEY_ENCRYPT) && authKey==null){
            authType = AuthType.ASY_TWO_WAY_ENCRYPT;
            apipClientEvent.setAuthType(authType);
            if(tryKey==null) addNewSessionKey();
        }

        if( authType.equals(AuthType.ASY_TWO_WAY_ENCRYPT)){
            itsPubkey = getDealersPubkey();

            if(itsPubkey==null){
                System.out.println("Failed to get the pubkey of the dealer.");
                return null;
            }

            myPrikey = Decryptor.decryptPrikey(apiAccount.getUserPrikeyCipher(),symkey);
        }

        try {
            switch (httpMethod) {
                case GET -> apipClientEvent.get(authKey);
                case POST -> apipClientEvent.post(authKey, myPrikey, itsPubkey);
                default -> apipClientEvent.setCode(CodeMessage.Code1022NoSuchMethod);
            }
        }catch (Exception e){
            log.debug("Failed to request. Error:{}",e.getMessage());
            return null;
        }
        return checkResult();
    }

    private void addNewSessionKey() {
        RequestBody requestBody = RequestBody.fromJson(new String(apipClientEvent.getRequestBodyBytes()),RequestBody.class);
        String newSessionKey = FcSession.genKey(32);
        requestBody.setSymkey(newSessionKey);
        apipClientEvent.setRequestBody(requestBody);
        apipClientEvent.setRequestBodyStr(requestBody.toJson());
        apipClientEvent.setRequestBodyBytes(apipClientEvent.requestBodyStr.getBytes());
        this.tryKey = newSessionKey;
    }

    @org.jetbrains.annotations.Nullable
    private String getDealersPubkey() {
        String itsPubkey;
        itsPubkey = apiProvider.getDealerPubkey();
        String dealer = null;
        if(itsPubkey==null){
            ReplyBody replyBody = getService(this.urlHead, VER_1);
            if(replyBody!=null && replyBody.getCode()==0){
                Service service = (Service) replyBody.getData();
                dealer = service.getDealer();
                itsPubkey = service.getDealerPubkey();
            }
            if(dealer!=null)apiProvider.setDealer(dealer);
            if(itsPubkey!=null)apiProvider.setDealerPubkey(itsPubkey);
        }
        return itsPubkey;
    }

    public Object request(String sn, String ver, String apiName, ApipClientEvent.RequestBodyType requestBodyType, @Nullable Fcdsl fcdsl, @Nullable String requestBodyStr, @Nullable byte[] requestBodyBytes, @Nullable Map<String,String> paramMap, String requestFileName, ApipClientEvent.ResponseBodyType responseBodyType, String responseFileName, String responseFilePath, AuthType authType, @Nullable byte[] authKey, RequestMethod httpMethod){
        String urlTail = ApiUrl.makeUrlTail(sn, apiName,ver);
       return requestBase(urlTail, requestBodyType, fcdsl, requestBodyStr, requestBodyBytes, paramMap, requestFileName, responseBodyType, responseFileName, responseFilePath, authType, authKey, httpMethod);
    }

    public Object requestBytes(String sn, String ver, String apiName, ApipClientEvent.RequestBodyType requestBodyType, @Nullable Fcdsl fcdsl, @Nullable Map<String,String> paramMap, AuthType authType, @Nullable byte[] authKey, RequestMethod httpMethod){
        String urlTail = ApiUrl.makeUrlTail(sn, apiName,ver);
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

                if(apipClient==null && this.serviceType.equals(ServiceType.APIP)){
                    apipClient = (ApipClient) this;
                }

                Double paid = apiAccount.buyApi(symkey, apipClient, null);

                if (paid == null) {
                    if (apipClientEvent.getResponseBody().getCode().equals(CodeMessage.Code1026InsufficientFchOnChain)) {
                        System.out.println("Send some FCH to " + apiAccount.getUserId() + "...");
                        for(int i=0;i<10;i++) {
                            waitSeconds(WAIT_CONFIRMATION_SECONDS);
                            paid = apiAccount.buyApi(symkey, apipClient, null);
                            if (paid!=null) break;
                            System.out.println("Waiting...");
                        }
                    }
                }
                System.out.println("Checking the balance...");

                while(true){
                    waitSeconds(10);
                    Object result = ping(VER_1, RequestMethod.POST, AuthType.ASY_TWO_WAY_ENCRYPT, ServiceType.APIP);
                    if(result!=null) {
                        System.out.println("OK! " + result + " KB/requests are available.");
                        break;
                    }
                }
            }

            return null;
        }
//        checkBalance(apiAccount, apipClientEvent, symkey,apipClient);

        switch (apipClientEvent.getResponseBodyType()){
            case BYTES -> {
                return apipClientEvent.getResponseBodyBytes();
            }
            case FC_REPLY -> {
                if(apipClientEvent.getResponseBody()!=null) {
                    ReplyBody responseBody = apipClientEvent.getResponseBody();

                    if (responseBody.getBestHeight() != null) {
                        bestHeight = responseBody.getBestHeight();
                        if(apiAccount!=null)apiAccount.setBestHeight(bestHeight);
                    }

                    String confirmKey = responseBody.getSymkey();
//TODO test
                    if(confirmKey !=null){
                        saveNewSession(confirmKey);
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

    //TODO test
    private void saveNewSession(String tryKey) {
        FcSession fcSession = new FcSession(tryKey,apiProvider.getDealer(),apiProvider.getDealerPubkey());
        if(fcSession.getKeyBytes()==null)
            return;

        String keyCipher = Encryptor.encryptBySymkeyToJson(fcSession.getKeyBytes(), symkey);
        fcSession.setKeyCipher(keyCipher);
        fcSession.setBirthTime(System.currentTimeMillis());

        this.serverSession = fcSession;
        this.sessionKey = fcSession.getKeyBytes();

        apiAccount.setSessionKey(fcSession.getKeyBytes());
        apiAccount.setSession(fcSession);
        Configure.saveConfig();
        this.tryKey = null;
    }


    private static void waitSeconds(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException ignore) {}
    }


    public static Long checkBalance(ApiAccount apiAccount, final ApipClientEvent apipClientEvent, byte[] symkey, ApipClient apipClient) {
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
        Service service = apiAccount.getService();
        if(service==null) {
            System.out.println("The service is null in the API account.");
            return null;
        }
        else if(service.getPricePerKB()==null)
            priceStr=service.getPricePerRequest();
        else priceStr =service.getPricePerKB();
        Long price = FchUtils.coinStrToSatoshi(priceStr);
        if(price==null)price=0L;

        if(balance!=0 && balance < price * ApiAccount.minRequestTimes){
            double topUp = apiAccount.buyApi(symkey,apipClient, null);
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

    public static boolean checkSign(byte[] msgBytes, String sign, byte[] symkey) {
        if (sign == null || msgBytes == null) return false;
        byte[] signBytes = BytesUtils.bytesMerger(msgBytes, symkey);
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));
        return (sign.equals(doubleSha256Hash));
    }

    public static String getSessionName(byte[] sessionKey) {
        if (sessionKey == null) return null;
        return HexFormat.of().formatHex(Arrays.copyOf(sessionKey, 6));
    }

    private void setFreeApiState(Object data, ServiceType serviceType) {
        Map<String, FreeApi> freeApiMap = listToMap(Settings.freeApiListMap.get(serviceType),URL_HEAD);//listToMap(config.getFreeApipUrlList(),URL_HEAD);

        if(data ==null){
            if(freeApiMap.get(this.urlHead) != null){
                freeApiMap.get(this.urlHead).setActive(false);
            }
            return;
        }

        if(freeApiMap.get(this.urlHead)==null){
            FreeApi freeApi = new FreeApi(this.urlHead,true, this.serviceType);
            freeApiMap.put(this.urlHead,freeApi);
        }
        freeApiMap.get(this.urlHead).setActive(true);
    }

    public FcSession signIn(String ver, SignInMode mode) {
        String urlTail = ApiUrl.makeUrlTail(null,SIGN_IN.getName(),ver);//"/"+ ver +"/"+ PING;
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(MODE,mode.name());
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(paramMap);
        Object data = requestBase(urlTail, ApipClientEvent.RequestBodyType.FCDSL, fcdsl, null, null, null, null, ApipClientEvent.ResponseBodyType.FC_REPLY, null, null, AuthType.ASY_TWO_WAY_ENCRYPT, null, RequestMethod.POST);
        if(data==null)return null;
        serverSession = gson.fromJson(gson.toJson(data), FcSession.class);
        if(apipClientEvent.getResponseBody()!=null)
            apipClientEvent.getResponseBody().setData(serverSession);
        return serverSession;
    }

    public Object ping(String ver, RequestMethod requestMethod, AuthType authType, ServiceType serviceType) {
        String urlTail = ApiUrl.makeUrlTail(null,PING.getName(),ver);//"/"+ ver +"/"+ PING;
        Object data = requestBase(urlTail, ApipClientEvent.RequestBodyType.FCDSL, null, null, null, null, null, ApipClientEvent.ResponseBodyType.FC_REPLY, null, null, authType, sessionKey, requestMethod);
        if(requestMethod.equals(RequestMethod.POST)) {
            return checkBalance(apiAccount, apipClientEvent, symkey, apipClient);
        }else  {
            if(serviceType !=null && Settings.freeApiListMap!=null)setFreeApiState(data, serviceType);
            return data;
        }
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

    public byte[] getSymkey() {
        return symkey;
    }

    public void setSymkey(byte[] symkey) {
        this.symkey = symkey;
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

//    public FcSession signIn(SignInMode mode, BufferedReader br) {
//
//        if(apiAccount.getUserPrikeyCipher()==null)
//            return signInOffLine(mode, br);
//
//        FcSession rawSession =  signIn(VER_1,mode);
//
//        if(rawSession ==null)return null;
//
//        apiAccount.setSession(rawSession);
//        rawSession.makeKeyBytes();
//        sessionKey = rawSession.getKeyBytes();
//        apiAccount.setSessionKey(sessionKey);
//
//        return serverSession;
//    }

    public FcSession signInOffLine(SignInMode mode, BufferedReader br) {
        FcSession fcSession;

        Fcdsl fcdsl = new Fcdsl();
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put(MODE,mode.name());
        fcdsl.setOther(paramMap);

        String urlTail = ApiUrl.makeUrlTail(null,ApipApiNames.SING_IN,VER_1);
        apipClientEvent = new ApipClientEvent(urlHead,urlTail,fcdsl, via);

        RequestBody requestBody = new RequestBody(apipClientEvent.getApiUrl().getUrl(),via);
        requestBody.setFcdsl(fcdsl);

        Affair signInAffair = new Affair();
        signInAffair.setMeta(new Meta(Affair.NAME));
        signInAffair.setOp(Op.ENCRYPT);
        signInAffair.setOpType(EncryptType.AsyTwoWay.name());
        signInAffair.setPubkey(apiAccount.getUserPubkey());
        signInAffair.setPubkeyB(apiProvider.getDealerPubkey());
        signInAffair.setDataStr(requestBody.toJson());



        Shower.showTextAndQR(signInAffair.toJson(),"No prikey to sign in. Please scan and create sign in request with Freer:");

        while (true) {
            System.out.print("Input the encrypted request. ");
            String encryptedRequest = Inputer.inputStringMultiLine(br);
            try {
                if(encryptedRequest==null || encryptedRequest.equals("")){
                    if (Inputer.askIfYes(br, "Failed. Try again?")) continue;
                    else return null;
                }
                try {
                    CryptoDataByte.fromJson(encryptedRequest);//Signature.fromJson(encryptedRequest);
                }catch (Exception e){
                    if (Inputer.askIfYes(br, "Failed. Try again?")) continue;
                    else return null;
                }


                byte[] sessionCipherBytes = apipClientEvent.postBytes(encryptedRequest.getBytes());

                if(sessionCipherBytes == null){
                    if(apipClientEvent.getResponseBody().getCode()==CodeMessage.Code1004InsufficientBalance){
                        System.out.println(apipClientEvent.getResponseBody().getMessage());

                        Double paid = apiAccount.buyApi(symkey, apipClient, br);
                        if(paid!=null && paid>0){
                            FcSession rawSession = signInOffLine(mode, br);
                            if(rawSession==null){
                                System.out.println(apipClientEvent.getMessage());
                                return null;
                            }
                            return rawSession;
                        }
                        return null;
                    }

                    apipClientEvent.code = 1020;
                    apipClientEvent.message = "Failed to sign in.";
                    return null;
                }

                Affair decryptAffair = new Affair();
                decryptAffair.setMeta(new Meta(Affair.NAME));
                decryptAffair.setOp(Op.DECRYPT);
                decryptAffair.setDataStr(new String(sessionCipherBytes));
                decryptAffair.setFid(apiAccount.getUserId());

                Shower.showTextAndQR(decryptAffair.toJson(), "No prikey to decrypt the sessionKey. Please scan and decrypt it with Freer:");

                System.out.print("Input the request. ");
                String decryptedResponseBody = Inputer.inputStringMultiLine(br);

                ReplyBody replyBody = ReplyBody.fromJson(decryptedResponseBody,ReplyBody.class);
                if(replyBody==null) return null;
                if(replyBody.getCode()!=0){
                    System.out.println(replyBody.getMessage());
                    return null;
                }

                fcSession = ObjectUtils.objectToClass(replyBody.getData(), FcSession.class);

                try {
                    if(fcSession == null){
                        if(Inputer.askIfYes(br,"Failed. Try again?"))
                            continue;
                        return null;
                    }

                    return fcSession;

                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    if(Inputer.askIfYes(br,"Failed. Try again?"))
                        continue;
                    return null;
                }
            }catch (Exception e){
                System.out.println(e.getMessage());
                return null;
            }
        }
    }

    private FcSession inputSession(byte[] symkey, BufferedReader br) {
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
            fcSession.setKeyCipher(Encryptor.encryptBySymkeyToJson(keyBytes, symkey));
            fcSession.makeId();
            serverSession = fcSession;
            apiAccount.setSession(serverSession);
            return serverSession;
        }
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

    public ServiceType getApiType() {
        return serviceType;
    }

    public void setApiType(ServiceType serviceType) {
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

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }
}
