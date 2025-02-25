package clients;

import fcData.FcSession;
import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import constants.*;
import fcData.ReplyBody;
import fch.ParseTools;
import com.google.gson.Gson;
import configure.ApiAccount;
import configure.ApiProvider;
import crypto.*;
import feip.feipData.Service;
import feip.feipData.serviceParams.Params;
import server.ApipApiNames;
import tools.BytesTools;
import tools.FileTools;
import tools.Hex;
import tools.JsonTools;
import tools.http.AuthType;
import tools.http.RequestMethod;
import tools.http.HttpTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.FreeApi;
import appTools.Settings;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static server.ApipApiNames.*;
import static constants.FieldNames.LAST_TIME;
import static constants.Strings.DOT_JSON;
import static constants.Strings.URL_HEAD;
import static constants.UpStrings.BALANCE;
import static fcData.AlgorithmId.FC_AesCbc256_No1_NrC7;
import static fcData.AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;
import static tools.ObjectTools.listToMap;

public class Client {
    protected static final Logger log = LoggerFactory.getLogger(Client.class);
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

    public static byte[] decryptPriKey(String userPriKeyCipher, byte[] symKey) {
        CryptoDataByte cryptoResult = new Decryptor().decryptJsonBySymKey(userPriKeyCipher, symKey);
        if (cryptoResult.getCode() != 0) {
            cryptoResult.printCodeMessage();
            return null;
        }
        return cryptoResult.getData();
    }

    public static String encryptBySymKey(byte[] data, byte[]symKey) {
        Encryptor encryptor = new Encryptor(FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(data,symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.toJson();
    }

    public static String encryptFile(String fileName, String pubKeyHex) {

        byte[] pubKey = Hex.fromHex(pubKeyHex);
        Encryptor encryptor = new Encryptor(FC_EccK1AesCbc256_No1_NrC7);
        String tempFileName = FileTools.getTempFileName();
        CryptoDataByte result1 = encryptor.encryptFileByAsyOneWay(fileName, tempFileName, pubKey);
        if(result1.getCode()!=0)return null;
        String cipherFileName;
        try {
            cipherFileName = Hash.sha256x2(new File(tempFileName));
            Files.move(Paths.get(tempFileName),Paths.get(cipherFileName));
        } catch (IOException e) {
            return null;
        }
        return cipherFileName;
    }

    @org.jetbrains.annotations.Nullable
    public static String decryptFile(String path, String gotFile,byte[]symKey,String priKeyCipher) {
        CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymKey(priKeyCipher,symKey);
        if(cryptoDataByte.getCode()!=0){
            log.debug("Failed to decrypt the user priKey.");
            log.debug(cryptoDataByte.getMessage());
            return null;
        }
        byte[] priKey = cryptoDataByte.getData();
        CryptoDataByte cryptoDataByte1 = new Decryptor().decryptFileToDidByAsyOneWay(path, gotFile, path, priKey);
        if(cryptoDataByte1.getCode()!=0){
            log.debug("Failed to decrypt file "+ Path.of(path, gotFile));
            return null;
        }
        BytesTools.clearByteArray(priKey);
        return Hex.toHex(cryptoDataByte1.getDid());
    }

    public static Map<String, Long> loadLastTime(String fid,String oid) {
        String fileName = FileTools.makeFileName(fid, oid, LAST_TIME, DOT_JSON);
        try {
            Map<String, Long> lastTimeMap1 = JsonTools.readMapFromJsonFile(null, fileName, String.class, Long.class);
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
        String fileName = FileTools.makeFileName(fid, oid, LAST_TIME, DOT_JSON);
        JsonTools.writeMapToJsonFile(lastTimeMap, fileName);
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

        String apiName = HttpTools.getApiNameFromUrl(urlTail);
        if(apiName==null||apiName.equals(SIGN_IN) || apiName.equals(SIGN_IN_ECC))return result;
        //If any request besides signIn or signInEcc got a session response, it means the client signed in again. So repeat the request.
        try {
            serverSession = (FcSession) result;
            if (serverSession == null || serverSession.getName() == null) return result;
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
//                System.out.println(fcClientEvent.getMessage());
            } else {
                log.debug(apipClientEvent.getResponseBody().getCode() + ":" + apipClientEvent.getResponseBody().getMessage());
//                System.out.println(fcClientEvent.getResponseBody().getMessage());
                if (apipClientEvent.getResponseBody().getData() != null)
                    log.debug(JsonTools.toJson(apipClientEvent.getResponseBody().getData()));
            }

            if (apipClientEvent.getCode() == CodeMessage.Code1004InsufficientBalance) {
                if(apipClient==null && this.serviceType.equals(Service.ServiceType.APIP)){
                    apipClient = (ApipClient) this;
                }
                double paid = apiAccount.buyApi(symKey, apipClient, null);
                if(paid==0){
                    if(apipClientEvent.getResponseBody().getCode().equals(CodeMessage.Code1026InsufficientFchOnChain)){
                        System.out.println("Send some FCH to "+apiAccount.getUserId()+"...");
                        while(true) {
                            waitSeconds(30);
                            paid = apiAccount.buyApi(symKey, apipClient, null);
                            if (paid!=0)break;
                            System.out.println("Waiting...");
                        }
                    }
                    System.out.println("Failed to pay from "+ apiAccount.getUserId());
                    return null;
                }
                System.out.println("Checking the balance...");
                while(true){
                    waitSeconds(5);
                    serverSession = checkSignInEcc();
                    apiAccount.setSession(serverSession);
                    apiAccount.setSessionKey(sessionKey);
                    if (serverSession != null)return serverSession;
                }
            }

            if (apipClientEvent.getCode() == CodeMessage.Code1002SessionNameMissed || apipClientEvent.getCode() == CodeMessage.Code1009SessionTimeExpired) {
                sessionFreshen=false;
                sessionKey = apiAccount.freshSessionKey(symKey, this.serviceType, RequestBody.SignInMode.NORMAL);
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
        long price = ParseTools.coinStrToSatoshi(priceStr);

        if(balance!=0 && balance < price * ApiAccount.minRequestTimes){
            double topUp = apiAccount.buyApi(symKey,apipClient, null);
            if(topUp==0){
                log.debug("Failed to buy APIP service.");
                return null;
            }
            apiAccount.setBalance(balance + ParseTools.coinToSatoshi(topUp));
        }else {

            return balance/price;
        }
        return null;
    }

    public static String getSessionKeySign(byte[] sessionKeyBytes, byte[] dataBytes) {
        return HexFormat.of().formatHex(Hash.sha256x2(BytesTools.bytesMerger(dataBytes, sessionKeyBytes)));
    }

    public static boolean checkSign(String msg, String sign, String symKey) {
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        return checkSign(msgBytes, sign, HexFormat.of().parseHex(symKey));
    }

    public static boolean checkSign(byte[] msgBytes, String sign, byte[] symKey) {
        if (sign == null || msgBytes == null) return false;
        byte[] signBytes = BytesTools.bytesMerger(msgBytes, symKey);
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
        serverSession.setId(fid);
        serverSession.setName(sessionName);

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

    public FcSession signInEcc(ApiAccount apiAccount, RequestBody.SignInMode mode, byte[] symKey) {

        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] priKey = cryptoDataByte.getData();

        signInEcc(priKey, mode);

        if(apipClientEvent ==null|| apipClientEvent.getResponseBody()==null|| apipClientEvent.getResponseBody().getData()==null)
            return null;
         serverSession = (FcSession) apipClientEvent.getResponseBody().getData();
        if(serverSession ==null|| serverSession.getKeyCipher()==null)return null;

        serverSession = makeSessionFromSignInEccResult(symKey, decryptor, priKey, serverSession);
        if(serverSession ==null)return null;
        apiAccount.setSession(serverSession);
        apiAccount.setSessionKey(sessionKey);

        return serverSession;
    }

    public FcSession makeSessionFromSignInEccResult(byte[] symKey, Decryptor decryptor, byte[] priKey, FcSession fcSession) {
        String sessionKeyCipher1 = fcSession.getKeyCipher();
        String fid = KeyTools.priKeyToFid(priKey);
        CryptoDataByte cryptoDataByte1 =
                decryptor.decryptJsonByAsyOneWay(sessionKeyCipher1, priKey);
        if (cryptoDataByte1.getCode() != 0) return null;
        sessionKey = cryptoDataByte1.getData();
//        byte[] sessionKeyHexBytes = cryptoDataByte1.getData();
//        if (sessionKeyHexBytes == null) return null;
//
//        String sessionKeyHex = new String(sessionKeyHexBytes);
//        sessionKey = Hex.fromHex(sessionKeyHex);

        Encryptor encryptor = new Encryptor(FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte2 = encryptor.encryptBySymKey(sessionKey, symKey);
        if (cryptoDataByte2.getCode() != 0) return null;
        String newCipher = cryptoDataByte2.toJson();
        fcSession.setKeyCipher(newCipher);

        String sessionName = FcSession.makeSessionName(sessionKey);

        fcSession.setKey(Hex.toHex(sessionKey));
        fcSession.setName(sessionName);
        fcSession.setId(fid);
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
