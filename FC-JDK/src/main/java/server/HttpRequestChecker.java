package server;

import core.crypto.EncryptType;
import core.crypto.KeyTools;
import data.apipData.Fcdsl;
import data.apipData.RequestBody;
import config.Settings;
import com.google.gson.Gson;
import constants.CodeMessage;
import core.crypto.CryptoDataByte;
import data.fcData.FcSession;
import data.fcData.ReplyBody;
import data.fcData.Signature;
import data.feipData.Service;
import data.feipData.serviceParams.Params;
import handlers.*;
import handlers.Manager;
import handlers.SessionManager;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.Nullable;
import utils.Hex;
import utils.ObjectUtils;
import utils.http.AuthType;
import utils.http.HttpUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;
import static constants.Values.TRUE;
import static core.crypto.KeyTools.pubkeyToFchAddr;
import static utils.http.AuthType.*;
import static utils.http.HttpUtils.getApiNameFromUrl;
import static utils.http.HttpUtils.illegalUrl;

public class HttpRequestChecker {
    private String apiName;
    private RequestBody requestBody;
    private String fid;
    private String pubkey;
    private String via;
    private String sessionName;
    private String sessionKey;
    private Boolean isFreeRequest;
    private ReplyBody replyBody;
    private Integer nonce;
    private AuthType authType;
    private final Settings settings;
    private final Service service;
    private final Params params;
    private final AccountManager accountHandler;
    private final SessionManager sessionHandler;
    private final NonceManager nonceHandler;
    private final long windowTime;

    /*
        1. is signed?
        2. is body stream?
        3. is free?

        1. native free: getService
        2. set free: get
        3. none free: post
         */
    public HttpRequestChecker(Settings settings) {
        this(settings,null);
    }
    public HttpRequestChecker(Settings settings, ReplyBody replyBody) {
        this.settings = settings;
        if(replyBody ==null)
            this.replyBody =new ReplyBody(settings);
        else this.replyBody = replyBody;

        this.accountHandler = (AccountManager) settings.getManager(Manager.ManagerType.ACCOUNT);
        this.sessionHandler = (SessionManager) settings.getManager(Manager.ManagerType.SESSION);
        this.nonceHandler = (NonceManager)settings.getManager(Manager.ManagerType.NONCE);
        this.service = settings.getService();
        this.params = ObjectUtils.objectToClass(settings.getService().getParams(),Params.class) ;
        Object windowTimeRaw = settings.getSettingMap().get(Settings.WINDOW_TIME);
        this.windowTime = ((Number) windowTimeRaw).longValue();

        this.replyBody.setRequestChecker(this);
    }


    public boolean checkRequestHttp(HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        String sid = settings.getSid();
        this.authType = authType;

        String url = HttpUtils.getEntireUrl(request);
        if (illegalUrl(url)) {
            replyBody.replyHttp(CodeMessage.Code1016IllegalUrl, response);
            return false;
        }

        String apiNameFromUrl = getApiNameFromUrl(url);
        setApiName(apiNameFromUrl);

        boolean isForbidFreeApi = checkForbidFree();

        if (authType.equals(FREE)) {
            return checkFreeRequest(request, replyBody, url, response);
        }

        if(authType.equals(FC_SIGN_URL)|| authType.equals(FC_SIGN_BODY))
            return checkSignBodyRequest(request, response, authType, sid, url, isForbidFreeApi);

        return checkEncryptedRequest(request, response, url, isForbidFreeApi);
    }

    private boolean checkSignBodyRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, String sid, String url, boolean isForbidFreeApi) {
        SignInfo signInfo = null;

        setFreeRequest(false);
        if (authType.equals(FC_SIGN_URL)) {
            signInfo = parseUrlSignInfo(request);
            try {
                Fcdsl fcdsl = Fcdsl.urlParamsToFcdsl(url);
                RequestBody requestBody = new RequestBody();
                requestBody.setFcdsl(fcdsl);
                setRequestBody(requestBody);
            } catch (Exception e) {
                replyBody.replyOtherErrorHttp(e.getMessage(), response);
                return false;
            }
        } else if (authType.equals(FC_SIGN_BODY)) {
            signInfo = parseBodySignInfo(request);
            setRequestBody(signInfo.requestBody);
        }

        initializeRequestBodyAndNonce(signInfo);
        cleanupFcdslAfter();

        if (signInfo == null) {
            if (isForbidFreeApi) {
                replyBody.replyOtherErrorHttp("Failed to check sign.", response);
                return false;
            } else return true;
        }
        if (signInfo.code != 0) {
            if (isForbidFreeApi) {
                replyBody.replyHttp(signInfo.code, response);
                return false;
            } else return true;
        }
        FcSession fcSession = sessionHandler.getSessionByName(signInfo.sessionName);
        if (fcSession == null) {
            if(isForbidFreeApi){
                replyBody.replyHttp(CodeMessage.Code1009SessionTimeExpired, response);
                return false;
            }
            return true;
        }

        String fid = fcSession.getUserId();
        String sessionKey = fcSession.getKey();

        setSessionName(signInfo.sessionName);
        setSessionKey(sessionKey);
        setFid(fid);

        if(!validateBalanceAndNonce(fid, sid, signInfo.nonce, response)) return false;

        byte[] bytesSigned;
        if (authType.equals(FC_SIGN_URL)) bytesSigned = url.getBytes();
        else bytesSigned = signInfo.requestBodyBytes;

        if (!Signature.verifySha256SymSign(bytesSigned, Hex.fromHex(sessionKey),signInfo.sign)) {
            replyBody.replyHttp(CodeMessage.Code1008BadSign, null, response);
            return false;
        }

        if(!validateUrlAndTime(request, signInfo.url, url, signInfo.time, response, authType.equals(SYMKEY_ENCRYPT))) return false;

        if (signInfo.via != null) setVia(signInfo.via);

        if (authType.equals(FC_SIGN_URL)) {
            Fcdsl fcdsl = Fcdsl.urlParamsToFcdsl(url);
            if (fcdsl != null) {
                RequestBody requestBody = new RequestBody();
                requestBody.setFcdsl(fcdsl);
                setRequestBody(requestBody);
            }
        }

        return true;
    }

    private  boolean checkFreeRequest(HttpServletRequest request, ReplyBody replier, String url, HttpServletResponse response) {
        byte[] requestBodyBytes;
        setFreeRequest(true);

        try{
            Fcdsl fcdsl = Fcdsl.urlParamsToFcdsl(url);
            if (fcdsl != null) {
                RequestBody requestBody = new RequestBody();
                requestBody.setFcdsl(fcdsl);
                setRequestBody(requestBody);
            }else if(request.getInputStream()!=null){
                requestBodyBytes = request.getInputStream().readAllBytes();
                if (requestBodyBytes != null && requestBodyBytes.length>0) {
                    RequestBody requestBody = getRequestBody(requestBodyBytes, replier, response);
                    setRequestBody(requestBody);
                }
            }
        }catch (Exception ignore){}

        if(requestBody==null)
            setRequestBody(new RequestBody());

        if(requestBody.getFcdsl()==null)
            requestBody.setFcdsl(new Fcdsl());
        return true;
    }

    private boolean checkEncryptedRequest(HttpServletRequest request, HttpServletResponse response, String url, boolean isForbidFreeApi) {
        byte[] requestBodyBytes;
        try {
            requestBodyBytes = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            replyBody.replyHttp(CodeMessage.Code1020OtherError, "Failed to read request body.", response);
            return false;
        }

        if (requestBodyBytes == null || requestBodyBytes.length == 0) {
            replyBody.replyHttp(CodeMessage.Code1003BodyMissed, response);
            return false;
        }

        // Parse request body as JSON string to CryptoDataByte
        String requestBodyJson = new String(requestBodyBytes);
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(requestBodyJson);
        } catch (Exception e) {
            replyBody.replyHttp(CodeMessage.Code1013BadRequest, "Failed to parse request body as CryptoDataByte.", response);
            return false;
        }

        if (cryptoDataByte.getCipher()==null) {
            replyBody.replyHttp(CodeMessage.Code1013BadRequest, "Invalid CryptoDataByte format.", response);
            return false;
        }

        // Get the appropriate decryption key based on type
        byte[] decryptionKey = getDecryptionKey(cryptoDataByte, isForbidFreeApi, response);
        if (decryptionKey == null) {
            return !isForbidFreeApi;
        }

        // Decrypt the CryptoDataByte to get RequestBody
        core.crypto.Decryptor decryptor = new core.crypto.Decryptor();
        CryptoDataByte decryptedData = decryptor.decrypt(requestBodyJson, decryptionKey);

        if (decryptedData.getCode() != CodeMessage.Code0Success) {
            replyBody.replyHttp(CodeMessage.Code1029FailedToDecrypt, decryptedData.getMessage(), response);
            return false;
        }
        switch (decryptedData.getType()){
            case AsyTwoWay -> this.authType = ASY_TWO_WAY_ENCRYPT;
            case Symkey -> this.authType = SYMKEY_ENCRYPT;
        }


        // Parse decrypted data to RequestBody
        RequestBody requestBody = parseDecryptedRequestBody(decryptedData, response);
        if (requestBody == null) return false;

        setRequestBody(requestBody);

        if (requestBody.getFcdsl() == null) {
            requestBody.setFcdsl(new Fcdsl());
        }

        // Set nonce for reply
        if (requestBody.getNonce() != null) {
            replyBody.setNonce(requestBody.getNonce());
            this.nonce = requestBody.getNonce();
        }

        // Perform additional checks
        String sid = settings.getSid();
        if(!validateBalanceAndNonce(fid, sid, requestBody.getNonce(), response)) return false;

        if(!validateUrlAndTime(request, requestBody.getUrl(), url, requestBody.getTime(), response, true)) return false;

        if (requestBody.getVia() != null) {
            setVia(requestBody.getVia());
        }

        checkNewSessionKey(requestBody);

        return true;
    }

    /**
     * Initialize request body and nonce from SignInfo
     */
    private void initializeRequestBodyAndNonce(SignInfo signInfo) {
        if (requestBody == null) {
            setRequestBody(new RequestBody());
            if(signInfo!=null) {
                replyBody.setNonce(signInfo.nonce);
                this.nonce = signInfo.nonce;
            }
        } else {
            replyBody.setNonce(requestBody.getNonce());
            this.nonce = requestBody.getNonce();
        }

        if (requestBody.getFcdsl() == null)
            requestBody.setFcdsl(new Fcdsl());
    }

    /**
     * Clean up empty 'after' field in FCDSL
     */
    private void cleanupFcdslAfter() {
        if(requestBody != null && requestBody.getFcdsl() != null && requestBody.getFcdsl().getAfter() != null) {
            if(requestBody.getFcdsl().getAfter().isEmpty())
                requestBody.getFcdsl().setAfter(null);
        }
    }

    /**
     * Validate balance and nonce
     * @return true if validation passes, false otherwise
     */
    private boolean validateBalanceAndNonce(String fid, String sid, Integer nonce, HttpServletResponse response) {
        if (fid != null && accountHandler != null && accountHandler.isBadBalance(fid)) {
            String data = "Send at lest " + params.getMinPayment() + " F to " + service.getDealer() + " to buy the service #" + sid + ".";
            replyBody.replyHttp(CodeMessage.Code1004InsufficientBalance, data, response);
            return false;
        }

        if (nonce != null && nonceHandler.isBadNonce(nonce)) {
            replyBody.replyHttp(CodeMessage.Code1007UsedNonce, response);
            return false;
        }

        return true;
    }

    /**
     * Validate URL and time
     * @return true if validation passes, false otherwise
     */
    private boolean validateUrlAndTime(HttpServletRequest request, String signedUrl, String requestUrl, Long time, HttpServletResponse response, boolean checkUrl) {
        if (checkUrl && signedUrl != null && isBadUrl(signedUrl, requestUrl)) {
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("requestedURL", request.getRequestURL().toString());
            dataMap.put("signedURL", signedUrl);
            replyBody.replyHttp(CodeMessage.Code1005UrlUnequal, dataMap, response);
            return false;
        }

        if (time != null && NonceManager.isBadTime(time, windowTime)) {
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put(WINDOW_TIME, String.valueOf(windowTime));
            replyBody.replyHttp(CodeMessage.Code1006RequestTimeExpired, dataMap, response);
            return false;
        }

        return true;
    }

    /**
     * Get decryption key based on encryption type
     * @return decryption key bytes, or null if failed
     */
    private byte[] getDecryptionKey(CryptoDataByte cryptoDataByte, boolean isForbidFreeApi, HttpServletResponse response) {
        core.crypto.EncryptType encryptType = cryptoDataByte.getType();

        if (encryptType == null) {
            replyBody.replyHttp(CodeMessage.Code1020OtherError, "Encryption type is missing.", response);
            return null;
        }

        if (encryptType == core.crypto.EncryptType.Symkey) {
            byte[] keyName = cryptoDataByte.getKeyName();
            if (keyName == null) {
                replyBody.replyHttp(CodeMessage.Code1020OtherError, "KeyName is missing for Symkey encryption.", response);
                return null;
            }

            String sessionName = utils.Hex.toHex(keyName);
            FcSession fcSession = sessionHandler.getSessionByName(sessionName);
            if (fcSession == null) {
                if (isForbidFreeApi) {
                    replyBody.replyHttp(CodeMessage.Code1009SessionTimeExpired, response);
                }
                return null;
            }

            setSessionName(sessionName);
            setSessionKey(fcSession.getKey());
            setFid(fcSession.getUserId());
            return fcSession.getKeyBytes();
        } else if (encryptType == core.crypto.EncryptType.AsyOneWay || encryptType == core.crypto.EncryptType.AsyTwoWay) {
            String dealerFid = service.getDealer();
            if (dealerFid == null) {
                replyBody.replyHttp(CodeMessage.Code1020OtherError, "Dealer FID is not configured.", response);
                return null;
            }

            if (settings == null) {
                replyBody.replyHttp(CodeMessage.Code1020OtherError, "Settings is not available.", response);
                return null;
            }

            byte[] decryptionKey = settings.decryptPrikey();
            if (decryptionKey == null) {
                replyBody.replyHttp(CodeMessage.Code1020OtherError, "Failed to get dealer's private key.", response);
                return null;
            }

            if(encryptType.equals(EncryptType.AsyTwoWay)) {
                byte[] pubkeyA = cryptoDataByte.getPubkeyA();
                if(pubkeyA != null) {
                    setPubkey(Hex.toHex(pubkeyA));
                    setFid(KeyTools.pubkeyToFchAddr(pubkeyA));
                } else {
                    replyBody.replyHttp(CodeMessage.Code1020OtherError, "Missing requester pubkey in the cipher.", response);
                    return null;
                }
            }
            return decryptionKey;
        } else {
            replyBody.replyHttp(CodeMessage.Code1020OtherError, "Unsupported encryption type: " + encryptType, response);
            return null;
        }
    }

    /**
     * Parse decrypted data to RequestBody
     * @return RequestBody or null if parsing failed
     */
    private RequestBody parseDecryptedRequestBody(CryptoDataByte decryptedData, HttpServletResponse response) {
        byte[] decryptedBytes = decryptedData.getData();
        if (decryptedBytes == null) {
            replyBody.replyHttp(CodeMessage.Code1020OtherError, "Decrypted data is null.", response);
            return null;
        }

        String decryptedJson = new String(decryptedBytes);
        RequestBody requestBody;
        try {
            requestBody = new Gson().fromJson(decryptedJson, RequestBody.class);
        } catch (Exception e) {
            replyBody.replyHttp(CodeMessage.Code1013BadRequest, "Failed to parse decrypted data as RequestBody.", response);
            return null;
        }

        if (requestBody == null) {
            replyBody.replyHttp(CodeMessage.Code1013BadRequest, "Invalid RequestBody format.", response);
            return null;
        }

        return requestBody;
    }

    private void checkNewSessionKey(RequestBody requestBody) {
        String newSessionKey = requestBody.getSymkey();

        if(newSessionKey!=null) {
            SessionManager sessionHandler = (SessionManager) settings.getManager(Manager.ManagerType.SESSION);
            sessionHandler.updateSession(newSessionKey,fid,pubkey);
            if(requestBody.getSymkey()!=null){
                replyBody.setSymkey(newSessionKey);
            }
        }
    }

    private  SignInfo parseUrlSignInfo(HttpServletRequest request) {
        int code=0;
        String nonceStr = request.getParameter(NONCE);
        if(nonceStr==null){
            code = CodeMessage.Code1018NonceMissed;
            return new SignInfo(code, null, 0,0,null,null, null, null, null);
        }
        int nonce = Integer.parseInt(nonceStr);

        String timeStr = request.getParameter(TIME);
        if(timeStr==null) {
            code = CodeMessage.Code1019TimeMissed;
            return new SignInfo(code, null, 0,0,null,null, null, null, null);
        }
        long time = Long.parseLong(timeStr);

        String via = request.getParameter(VIA);

        String sign = request.getHeader(CodeMessage.SignInHeader);
        if(sign==null) {
            code = CodeMessage.Code1000SignMissed;
            return new SignInfo(code, null, 0,0,null,null, null, null, null);
        }

        String sessionName = request.getHeader(CodeMessage.SessionNameInHeader);
        if(sessionName==null) {
            code = CodeMessage.Code1002SessionNameMissed;
            return new SignInfo(code, null, 0,0,null,null, null, null, null);
        }

        return new SignInfo(code, null, nonce,time,via,sign, sessionName, null, null);
    }

    private  SignInfo parseBodySignInfo(HttpServletRequest request) {
        RequestBody requestBody = null;
        byte[] requestBodyBytes = null;
        try {
            requestBodyBytes = request.getInputStream().readAllBytes();
        } catch (IOException ignore) {
        }
        if (requestBodyBytes != null) {
            String requestDataJson = new String(requestBodyBytes);
            try {
                requestBody = new Gson().fromJson(requestDataJson, RequestBody.class);
            }catch(Exception ignore){}
        }

        boolean forbidFreeRequest = String.valueOf(settings.getSettingMap().get(Settings.FORBID_FREE_API)).equalsIgnoreCase(TRUE);

        if(requestBody == null)return new SignInfo(CodeMessage.Code1003BodyMissed, null, 0,0,null,null, null, null, null);
        if(requestBody.getNonce()==null&& forbidFreeRequest)return new SignInfo(CodeMessage.Code1018NonceMissed, null, 0,0,null,null, null, null,null);
        if(requestBody.getTime()==null&& forbidFreeRequest)return new SignInfo(CodeMessage.Code1019TimeMissed, null, 0,0,null,null, null, null,null);
        if(requestBody.getUrl()==null&& forbidFreeRequest)return new SignInfo(CodeMessage.Code1024UrlMissed, null, 0,0,null,null, null, null,null);
        if(request.getHeader(SIGN)==null && forbidFreeRequest)
            return new SignInfo(CodeMessage.Code1000SignMissed, null, 0,0,null,null, null, null, null);
        if(request.getHeader(SESSION_NAME)==null && forbidFreeRequest)
            return new SignInfo(CodeMessage.Code1002SessionNameMissed, null, 0,0,null,null, null, null, null);
        return new SignInfo(CodeMessage.Code0Success, requestBody.getUrl(),requestBody.getNonce(), requestBody.getTime(), requestBody.getVia(), request.getHeader(SIGN),request.getHeader(SESSION_NAME), requestBodyBytes, requestBody);
    }


    private  Boolean checkForbidFree()   {
        try {
            boolean isForbidFreeApi = (boolean) settings.getSettingMap().get(Settings.FORBID_FREE_API);
            setFreeRequest(isForbidFreeApi);
            return isForbidFreeApi;
        }catch (Exception ignore){
            return false;
        }
    }

    @Nullable
    public  Map<String, String> checkOtherRequestHttp(HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        boolean isOk = checkRequestHttp(request, response, authType);
        if (!isOk) {
            return null;
        }
        this.authType = authType;
        if (requestBody == null) {
            replyBody.replyHttp(CodeMessage.Code1003BodyMissed, response);
            return null;
        }
        Fcdsl fcdsl = requestBody.getFcdsl();
        if (fcdsl == null) {
            replyBody.replyOtherErrorHttp("The FCDSL is missed.", response);
            return null;
        }
        replyBody.setNonce(requestBody.getNonce());
        //Check API
        Map<String, String> other = requestBody.getFcdsl().getOther();
        if (other == null) {
            replyBody.replyOtherErrorHttp("The other parameter is missed. Check it.", response);
            return null;
        }
        return other;
    }

    private String promoteSignInRequest(ReplyBody replier, String urlHead) {
        String data = "A Sign is required in request header.";
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[4];
        secureRandom.nextBytes(bytes);
        int nonce = ByteBuffer.wrap(bytes).getInt();
        if(nonce<0)nonce=(-nonce);
        long timestamp = System.currentTimeMillis();
        if(urlHead!=null) {
            data = """
                    A signature are requested:
                    \tRequest header:
                    \t\tFid = <Freecash cid of the requester>
                    \t\tSign = <The signature of request body signed by the private key of the FID.>
                    \tRequest body:{"url":"%s","nonce":"%d","time":"%d"}"""
                    .formatted(urlHead+ ApipApi.VER_1 + ApipApi.SIGN_IN,nonce,timestamp);
        }
        return replier.reply(CodeMessage.Code1000SignMissed, null,data);
    }

    private String promoteDataRequest(ReplyBody replier, String urlHead) {
        String data = "A Sign is required in request header.";
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[4];
        secureRandom.nextBytes(bytes);
        int nonce = ByteBuffer.wrap(bytes).getInt();
        if(nonce<0)nonce=(-nonce);
        long timestamp = System.currentTimeMillis();
        if(urlHead!=null) {
            data = """
                    A signature are requested:
                    \tRequest header:
                    \t\tSessionName = <The fcSession name which is the hex of the first 6 bytes of the sessionKey>
                    \t\tSign = <The value of double sha256 of the request body bytes adding the sessionKey bytes.>
                    \tRequest body:
                    \t\t{
                    \t\t\t"url":%s,
                    \t\t\t"nonce":%d
                    \t\t\t"time":%d
                    \t\t\t<your request parameters...>
                    \t\t}
                    """
                    .formatted(urlHead+ ApipApi.VER_1 + ApipApi.SIGN_IN,nonce,timestamp);
        }
        return replier.reply(CodeMessage.Code1000SignMissed, null,data);
    }

    private String promoteUrlSignRequest(ReplyBody replier, String urlHead) {
        String data = "A Sign is required in request header.";
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[4];
        secureRandom.nextBytes(bytes);
        int nonce = ByteBuffer.wrap(bytes).getInt();
        if(nonce<0)nonce=(-nonce);
        long timestamp = System.currentTimeMillis();
        if(urlHead!=null) {
            data = """
                    A signature are requested:
                    \tRequest header:
                    \t\tSessionName = <The fcSession name which is the hex of the first 6 bytes of the sessionKey>
                    \t\tSign = <The value of double sha256 of the whole requested URL bytes adding the sessionKey bytes.>
                    \tRequest URL should be:
                    \t\t\t%s/"nonce=%s&time=%s<your more parameters>
                    """
                    .formatted(urlHead+ ApipApi.VER_1 + ApipApi.SIGN_IN,nonce,timestamp);
        }
        return replier.reply(CodeMessage.Code1000SignMissed, null,data);
    }

    private RequestBody getRequestBody(byte[] requestBodyBytes, ReplyBody replier, HttpServletResponse response) {
        String requestDataJson = new String(requestBodyBytes);
        RequestBody connectRequestBody;
        try {
            connectRequestBody = new Gson().fromJson(requestDataJson, RequestBody.class);
        }catch(Exception e){
            replier.replyHttp(CodeMessage.Code1013BadRequest, "Parsing request body wrong.",response);
            return null;
        }
        return connectRequestBody;
    }

    public boolean isBadUrl(String signedUrl, String requestUrl){
        return !requestUrl.equals(signedUrl);
    }

    public RequestBody getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(RequestBody requestBody) {
        this.requestBody = requestBody;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public Boolean getFreeRequest() {
        return isFreeRequest;
    }

    public void setFreeRequest(Boolean freeRequest) {
        isFreeRequest = freeRequest;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    private record SignInfo(int code, String url, int nonce, long time, String via, String sign, String sessionName,
                            byte[] requestBodyBytes, RequestBody requestBody){
    }

    public ReplyBody getReplyBody() {
        return replyBody;
    }

    public void setReplyBody(ReplyBody replyBody) {
        this.replyBody = replyBody;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }
}
