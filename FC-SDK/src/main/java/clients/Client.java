package clients;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import apip.apipData.Session;
import constants.*;
import fcData.FcReplier;
import fch.ParseTools;
import clients.apipClient.ApipClient;
import com.google.gson.Gson;
import configure.ApiAccount;
import configure.ApiProvider;
import configure.ServiceType;
import crypto.*;
import feip.feipData.Service;
import feip.feipData.serviceParams.Params;
import javaTools.BytesTools;
import javaTools.FileTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;
import javaTools.http.HttpTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.FreeApi;
import server.Settings;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static constants.ApiNames.*;
import static constants.Strings.URL_HEAD;
import static constants.UpStrings.BALANCE;
import static fcData.AlgorithmId.FC_Aes256Cbc_No1_NrC7;
import static fcData.AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;
import static javaTools.ObjectTools.listToMap;

public class Client {
    protected static final Logger log = LoggerFactory.getLogger(Client.class);
    protected ApiProvider apiProvider;
    protected ApiAccount apiAccount;
    protected String urlHead;
    protected String via;
    protected FcClientEvent fcClientEvent;
    protected byte[] symKey;
    protected byte[] sessionKey;
    protected ApipClient apipClient;
    protected boolean isAllowFreeRequest;
    protected ServiceType serviceType;
    protected Gson gson = new Gson();
    protected boolean sessionFreshen=false;
    protected long bestHeight;
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
        this.apiAccount = apiAccount;
        this.sessionKey = apiAccount.getSessionKey();
        this.apiProvider = apiProvider;
        this.serviceType = apiProvider.getType();
        this.symKey = symKey;
        this.apipClient = apipClient;
        this.urlHead = apiAccount.getApiUrl();
        this.via = apiAccount.getVia();
        this.serviceType = apiProvider.getType();
    }

    public static FcReplier getService(String urlHead, String apiVersion, Class<? extends Params> paramsClass){
        ApiUrl apiUrl = new ApiUrl(urlHead,null, apiVersion,ApiNames.GetService, null,false,null);
        FcClientEvent clientEvent = Client.get(apiUrl.getUrl());
        if(clientEvent.checkResponse()!=0){
            System.out.println("Failed to get the service from "+apiUrl.getUrl());
            return null;
        }
        FcReplier responseBody = clientEvent.getResponseBody();
        Service service = new Gson().fromJson((String) responseBody.getData(), Service.class);
        Params.getParamsFromService(service, paramsClass);
        responseBody.setData(service);
        return responseBody;
    }

    public static FcClientEvent get(String url){
        FcClientEvent fcClientEvent = new FcClientEvent();
        ApiUrl apiUrl = new ApiUrl();
        apiUrl.setUrl(url);
        fcClientEvent.setApiUrl(apiUrl);
        fcClientEvent.get();
        return fcClientEvent;
    }

    protected static byte[] decryptPriKey(String userPriKeyCipher, byte[] symKey) {
        CryptoDataByte cryptoResult = new Decryptor().decryptJsonBySymKey(userPriKeyCipher, symKey);
        if (cryptoResult.getCode() != 0) {
            cryptoResult.printCodeMessage();
            return null;
        }
        return cryptoResult.getData();
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
        return requestBase(urlTail, FcClientEvent.RequestBodyType.FCDSL, null, null, null, paramMap, null, FcClientEvent.ResponseBodyType.FC_REPLY, null, null, authType, null, HttpRequestMethod.GET
        );
    }
    public Object requestFile(String ver, String apiName, Fcdsl fcdsl, String responseFileName, @Nullable String responseFilePath, AuthType authType, byte[] authKey, HttpRequestMethod method){
        return requestFile(null,  ver, apiName,fcdsl,responseFileName, responseFilePath, authType, authKey, method);
    }
    public Object requestFile(String sn, String ver, String apiName, Fcdsl fcdsl, String responseFileName, @Nullable String responseFilePath, AuthType authType, byte[] authKey, HttpRequestMethod method){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        requestBase(urlTail, FcClientEvent.RequestBodyType.FCDSL, fcdsl, null, null, null, null, FcClientEvent.ResponseBodyType.FILE, responseFileName, responseFilePath, authType, authKey, method);
        FcReplier responseBody = fcClientEvent.getResponseBody();
        if(responseBody !=null)return responseBody.getData();
        return null;
    }
    public Object requestJsonByFcdsl(String ver, String apiName, @Nullable Fcdsl fcdsl, AuthType authType, @Nullable byte[] authKey, HttpRequestMethod method){
        return requestJsonByFcdsl(null, ver, apiName, fcdsl, authType, authKey, method);
    }
    public Object requestJsonByFcdsl(String sn, String ver, String apiName, @Nullable Fcdsl fcdsl, AuthType authType, @Nullable byte[] authKey, HttpRequestMethod method){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;

        if(authType==null || authKey==null)
            authType = AuthType.FREE;

        return requestBase(urlTail, FcClientEvent.RequestBodyType.FCDSL, fcdsl, null, null, null, null, FcClientEvent.ResponseBodyType.FC_REPLY, null, null, authType, authKey, method
        );
    }

    public Object requestFileByFcdsl(String sn, String ver, String apiName, @Nullable Fcdsl fcdsl, String responseFileName, @Nullable String responseFilePath, @Nullable byte[] authKey, HttpRequestMethod method){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        AuthType authType;
        if(authKey!=null)authType=AuthType.FC_SIGN_BODY;
        else authType = AuthType.FREE;

        return requestBase(urlTail, FcClientEvent.RequestBodyType.FCDSL, fcdsl, null, null, null, null, FcClientEvent.ResponseBodyType.FILE, responseFileName, responseFilePath, authType, authKey, method
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

        return requestBase(urlTail, FcClientEvent.RequestBodyType.FILE, null, null, null, paramMap, requestFileName, FcClientEvent.ResponseBodyType.FC_REPLY, null, null, authType, authKey, HttpRequestMethod.POST
        );
    }

    public Object requestBase(String urlTail, FcClientEvent.RequestBodyType requestBodyType, @Nullable Fcdsl fcdsl, @Nullable String requestBodyStr, @Nullable byte[] requestBodyBytes, @Nullable Map<String,String> paramMap, String requestFileName, FcClientEvent.ResponseBodyType responseBodyType, String responseFileName, String responseFilePath, AuthType authType, @Nullable byte[] authKey, HttpRequestMethod httpMethod){

        if(httpMethod.equals(HttpRequestMethod.GET)){
            requestBodyType= FcClientEvent.RequestBodyType.NONE;
            if(fcdsl!=null) {
                String urlParamsStr = Fcdsl.fcdslToUrlParams(fcdsl);
                paramMap = Fcdsl.urlParamsStrToMap(urlParamsStr);
            }
        }
        fcClientEvent = new FcClientEvent(urlHead,urlTail,requestBodyType,fcdsl,requestBodyStr,requestBodyBytes,paramMap,requestFileName, responseBodyType,responseFileName,responseFilePath,authType, authKey, via);

        switch (httpMethod){
            case GET -> fcClientEvent.get(authKey);
            case POST -> fcClientEvent.post(authKey);
            default -> fcClientEvent.setCode(ReplyCodeMessage.Code1022NoSuchMethod);
        }
        Object result = checkResult();

        String apiName = HttpTools.getApiNameFromUrl(urlTail);
        if(apiName==null||apiName.equals(SignIn) || apiName.equals(SignInEcc))return result;

        //If any request besides signIn or signInEcc got a session response, it means the client signed in again. So repeat the request.
        try {
            Session session = (Session) result;
            if (session == null || session.getSessionName() == null) return result;
            else return requestBase(urlTail, requestBodyType, fcdsl, requestBodyStr, requestBodyBytes, paramMap, requestFileName, responseBodyType, responseFileName, responseFilePath, authType, authKey, httpMethod);
        }catch (ClassCastException e){
            return result;
        }
    }

    public Object request(String sn, String ver, String apiName, FcClientEvent.RequestBodyType requestBodyType, @Nullable Fcdsl fcdsl, @Nullable String requestBodyStr, @Nullable byte[] requestBodyBytes, @Nullable Map<String,String> paramMap, String requestFileName, FcClientEvent.ResponseBodyType responseBodyType, String responseFileName, String responseFilePath, AuthType authType, @Nullable byte[] authKey, HttpRequestMethod httpMethod){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
       return requestBase(urlTail, requestBodyType, fcdsl, requestBodyStr, requestBodyBytes, paramMap, requestFileName, responseBodyType, responseFileName, responseFilePath, authType, authKey, httpMethod);
    }

    public Object requestBytes(String sn, String ver, String apiName, FcClientEvent.RequestBodyType requestBodyType, @Nullable Fcdsl fcdsl, @Nullable Map<String,String> paramMap, AuthType authType, @Nullable byte[] authKey, HttpRequestMethod httpMethod){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        return requestBase(urlTail, requestBodyType, fcdsl, null, null, paramMap, null, FcClientEvent.ResponseBodyType.BYTES, null, null, authType, authKey, httpMethod);
    }

    public Object checkResult(){

        if(fcClientEvent.getResponseBodyType().equals(FcClientEvent.ResponseBodyType.STRING))
            return fcClientEvent.getResponseBodyStr();

        if(fcClientEvent ==null || fcClientEvent.getCode()==null)return null;
        if(fcClientEvent.getCode()!= ReplyCodeMessage.Code0Success) {
            if (fcClientEvent.getResponseBody()== null) {
                log.debug("ResponseBody is null when requesting "+this.fcClientEvent.getApiUrl().getUrl());
                System.out.println(fcClientEvent.getMessage());
            } else {
                log.debug(fcClientEvent.getResponseBody().getCode() + ":" + fcClientEvent.getResponseBody().getMessage());
                System.out.println(fcClientEvent.getResponseBody().getMessage());
                if (fcClientEvent.getResponseBody().getData() != null)
                    log.debug(JsonTools.toJson(fcClientEvent.getResponseBody().getData()));
            }
            if (fcClientEvent.getCode() == ReplyCodeMessage.Code1004InsufficientBalance) {
                if(apipClient==null && this.serviceType.equals(ServiceType.APIP)){
                    apipClient = (ApipClient) this;
                }
                double paid = apiAccount.buyApi(symKey, apipClient);
                if(paid==0){
                    if(fcClientEvent.getResponseBody().getCode().equals(ReplyCodeMessage.Code1026InsufficientFchOnChain)){
                        System.out.println("Send some FCH to "+apiAccount.getUserId()+"...");
                        while(true) {
                            waitSeconds(30);
                            paid = apiAccount.buyApi(symKey, apipClient);
                            if (paid!=0)break;
                            System.out.println("Waiting...");
                        }
                    }
                    System.out.println("Failed to pay from "+ apiAccount.getUserId());
                    return null;
                }
                System.out.println("Check the balance...");
                Session session;
                while(true){
                    waitSeconds(5);
                    session = checkSignInEcc();
                    apiAccount.setSession(session);
                    apiAccount.setSessionKey(sessionKey);
                    if (session != null)return session;
                }
            }

            if (fcClientEvent.getCode() == ReplyCodeMessage.Code1002SessionNameMissed || fcClientEvent.getCode() == ReplyCodeMessage.Code1009SessionTimeExpired) {
                sessionFreshen=false;
                sessionKey = apiAccount.freshSessionKey(symKey, this.serviceType, null);
                if (sessionKey != null) sessionFreshen=true;
            }

            return null;
        }
        checkBalance(apiAccount, fcClientEvent, symKey,apipClient);
        switch (fcClientEvent.getResponseBodyType()){
            case BYTES -> {
                return fcClientEvent.getResponseBodyBytes();
            }
            case FC_REPLY -> {
                if(fcClientEvent.getResponseBody()!=null) {
                    if (fcClientEvent.getResponseBody().getData() == null && fcClientEvent.getCode() == 0)
                        return true;
                    if (fcClientEvent.getResponseBody().getBestHeight() != null) {
                        bestHeight = fcClientEvent.getResponseBody().getBestHeight();
                        if(apiAccount!=null)apiAccount.setBestHeight(bestHeight);
                    }

                }
                return fcClientEvent.getResponseBody().getData();
            }
            case FILE -> {
                return null;
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private Session checkSignInEcc() {
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0) return null;
        byte[] priKey = cryptoDataByte.getData();
        fcClientEvent = new FcClientEvent(apiAccount.getApiUrl(),null,Version1,ApiNames.SignInEcc);
        fcClientEvent.signInPost(apiAccount.getVia(), priKey, RequestBody.SignInMode.NORMAL);
        Object data = fcClientEvent.getResponseBody().getData();
        Session session;
        try{
            session=gson.fromJson(gson.toJson(data), Session.class);
            session = makeSessionFromSignInEccResult(symKey, decryptor, priKey, session);
        } catch (Exception ignore){return null;}
        return session;
    }

    private static void waitSeconds(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException ignore) {}
    }


    public static Long checkBalance(ApiAccount apiAccount, final FcClientEvent fcClientEvent, byte[] symKey, ApipClient apipClient) {
        if(fcClientEvent ==null)return null;
        if(fcClientEvent.getResponseBody()==null)return null;
        Long balance = null;
        if( fcClientEvent.getResponseBody().getBalance()!=null)
            balance = fcClientEvent.getResponseBody().getBalance();
        else if(fcClientEvent.getResponseHeaderMap()!=null&& fcClientEvent.getResponseHeaderMap().get(BALANCE)!=null)
                balance = Long.valueOf(fcClientEvent.getResponseHeaderMap().get(BALANCE));
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
            double topUp = apiAccount.buyApi(symKey,apipClient);
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

    private void setFreeApiState(Object data, ServiceType serviceType) {
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

    public Object ping(String version, HttpRequestMethod httpRequestMethod, AuthType authType, ServiceType serviceType) {
        String urlTail = "/"+version+"/"+Ping;
        Object data = requestBase(urlTail, FcClientEvent.RequestBodyType.FCDSL, null, null, null, null, null, FcClientEvent.ResponseBodyType.FC_REPLY, null, null, authType, sessionKey, httpRequestMethod);
        if(httpRequestMethod.equals(HttpRequestMethod.POST)) {
            return checkBalance(apiAccount, fcClientEvent, symKey, apipClient);
        }else {
            if(serviceType !=null)setFreeApiState(data, serviceType);
            return data;
        }
    }

    private void signIn(byte[] priKey, @Nullable RequestBody.SignInMode mode) {
        fcClientEvent = new FcClientEvent(apiAccount.getApiUrl(),null,Version1,ApiNames.SignIn);
        fcClientEvent.signInPost(apiAccount.getVia(), priKey, mode);
        int paymentsSize;
        if(apiAccount.getPayments()!=null) paymentsSize = apiAccount.getPayments().size();
        else paymentsSize=0;
        Object data = checkResult();
        if(data==null){
            if(fcClientEvent.getCode()==1004 && apiAccount.getPayments().size()>paymentsSize){
                fcClientEvent.signInPost(apiAccount.getVia(), priKey, mode);
                data = checkResult();
                if(data==null) return;
            } else return;
        }
        Session session = gson.fromJson(gson.toJson(data), Session.class);
        fcClientEvent.getResponseBody().setData(session);
    }

    private void signInEcc(byte[] priKey, @Nullable RequestBody.SignInMode mode) {
        fcClientEvent = new FcClientEvent(apiAccount.getApiUrl(),null,Version1,ApiNames.SignInEcc);
        fcClientEvent.signInPost(apiAccount.getVia(), priKey, mode);
        int paymentsSize;
        if(apiAccount.getPayments()!=null) paymentsSize= apiAccount.getPayments().size();
        else paymentsSize=0;

        Object data = checkResult();
        if(data==null){
            if(fcClientEvent.getCode()==1004 && apiAccount.getPayments().size()>paymentsSize){
                fcClientEvent.signInPost(apiAccount.getVia(), priKey, mode);
                data = checkResult();
                if(data==null) return;
            } else if(fcClientEvent.getCode()==1026){
                return;
            }
        }
        Session session = gson.fromJson(gson.toJson(data), Session.class);
        fcClientEvent.getResponseBody().setData(session);
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

    public FcClientEvent getFcClientEvent() {
        return fcClientEvent;
    }

    public void setFcClientEvent(FcClientEvent clientData) {
        this.fcClientEvent = clientData;
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

    public Session signIn(ApiAccount apiAccount, RequestBody.SignInMode mode, byte[] symKey) {

        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] priKey = cryptoDataByte.getData();

//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(apiAccount.getUserPriKeyCipher(),symKey);
        signIn(priKey, mode);
        if(fcClientEvent==null||fcClientEvent.getResponseBody()==null||fcClientEvent.getResponseBody().getData()==null)
            return null;
        Session session = (Session) fcClientEvent.getResponseBody().getData();
        if(session==null||session.getSessionKey()==null)return null;
        byte[] sessionKey = Hex.fromHex(session.getSessionKey());

        apiAccount.setSessionKey(sessionKey);

        String sessionName = Session.makeSessionName(session.getSessionKey());
        String fid = KeyTools.priKeyToFid(priKey);
        session.setFid(fid);
        session.setSessionName(sessionName);

        if(session.getSessionKeyCipher()==null) {
            Encryptor encryptor = new Encryptor();
            CryptoDataByte cryptoDataByte2 = encryptor.encryptBySymKey(sessionKey, symKey);
            if (cryptoDataByte2.getCode() != 0) return null;
            String sessionKeyCipher = cryptoDataByte2.toJson();
            session.setSessionKeyCipher(sessionKeyCipher);
        }

        apiAccount.setSession(session);
        apiAccount.setSessionKey(sessionKey);
        return session;
    }

    public Session signInEcc(ApiAccount apiAccount, RequestBody.SignInMode mode, byte[] symKey) {

        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] priKey = cryptoDataByte.getData();

        signInEcc(priKey, mode);

        if(fcClientEvent==null||fcClientEvent.getResponseBody()==null||fcClientEvent.getResponseBody().getData()==null)
            return null;
        Session session = (Session) fcClientEvent.getResponseBody().getData();
        if(session==null||session.getSessionKeyCipher()==null)return null;

        if(session.getSessionKey()==null) {
            session = makeSessionFromSignInEccResult(symKey, decryptor, priKey, session);
            if(session==null)return null;
        }
            apiAccount.setSession(session);
            apiAccount.setSessionKey(sessionKey);

        return session;
    }

    public Session makeSessionFromSignInEccResult(byte[] symKey, Decryptor decryptor, byte[] priKey, Session session) {
        String sessionKeyCipher1 = session.getSessionKeyCipher();
        String fid = KeyTools.priKeyToFid(priKey);
        CryptoDataByte cryptoDataByte1 =
                decryptor.decryptJsonByAsyOneWay(sessionKeyCipher1, priKey);
        if (cryptoDataByte1.getCode() != 0) return null;
        byte[] sessionKeyHexBytes = cryptoDataByte1.getData();
        if (sessionKeyHexBytes == null) return null;

        String sessionKeyHex = new String(sessionKeyHexBytes);
        sessionKey = Hex.fromHex(sessionKeyHex);

        Encryptor encryptor = new Encryptor(FC_Aes256Cbc_No1_NrC7);
        CryptoDataByte cryptoDataByte2 = encryptor.encryptBySymKey(sessionKey, symKey);
        if (cryptoDataByte2.getCode() != 0) return null;
        String newCipher = cryptoDataByte2.toJson();
        session.setSessionKeyCipher(newCipher);

        String sessionName = Session.makeSessionName(sessionKeyHex);
        Long expireTime = session.getExpireTime();

        session.setSessionKey(Hex.toHex(sessionKey));
        session.setSessionName(sessionName);
        session.setExpireTime(expireTime);
        session.setFid(fid);
        return session;
    }

    public boolean isSessionFreshen() {
        return sessionFreshen;
    }

    public void setSessionFreshen(boolean sessionFreshen) {
        this.sessionFreshen = sessionFreshen;
    }


    public void setClientEvent(FcClientEvent fcClientEvent) {
        this.fcClientEvent = fcClientEvent;
    }

//    public String getSignInUrlTailPath() {
//        return signInUrlTailPath;
//    }

//    public void setSignInUrlTailPath(String signInUrlTailPath) {
//        this.signInUrlTailPath = signInUrlTailPath;
//    }

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

    public Object requestByIds(HttpRequestMethod httpRequestMethod, String sn, String ver, String apiName, AuthType authType, String... ids) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(ids);
        return requestJsonByFcdsl(sn, ver, apiName, fcdsl, authType, sessionKey, httpRequestMethod);
    }

    public Object requestByFcdslOther(String sn, String ver, String apiName, Map<String, String> other, AuthType authType, HttpRequestMethod httpRequestMethod) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addOther(other);
        return requestJsonByFcdsl(sn, ver, apiName, fcdsl, authType, sessionKey, httpRequestMethod);
    }
}
