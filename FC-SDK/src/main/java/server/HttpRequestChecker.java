package server;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import appTools.Settings;
import com.google.gson.Gson;
import constants.CodeMessage;
import fcData.FcSession;
import fcData.ReplyBody;
import fcData.Signature;
import feip.feipData.serviceParams.Params;
import handlers.AccountHandler;
import handlers.Handler;
import handlers.NonceHandler;
import handlers.SessionHandler;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.Nullable;
import tools.Hex;
import tools.ObjectTools;
import tools.http.AuthType;
import tools.http.HttpTools;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;

import static appTools.Settings.log;
import static constants.Strings.*;
import static crypto.KeyTools.pubKeyToFchAddr;
import static tools.http.AuthType.*;
import static tools.http.HttpTools.getApiNameFromUrl;
import static tools.http.HttpTools.illegalUrl;

public class HttpRequestChecker {
    private String apiName;
    private RequestBody requestBody;
    private String fid;
    private String pubKey;
    private String via;
    private String sessionName;
    private String sessionKey;
    private Boolean isFreeRequest;
    private ReplyBody replyBody;
    private Integer nonce;
    private final Settings settings;
    private final Params params;
    private final AccountHandler accountHandler;
    private final SessionHandler sessionHandler;
    private final NonceHandler  nonceHandler;
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

        this.accountHandler = (AccountHandler) settings.getHandler(Handler.HandlerType.ACCOUNT);
        this.sessionHandler = (SessionHandler) settings.getHandler(Handler.HandlerType.SESSION);
        this.nonceHandler = (NonceHandler)settings.getHandler(Handler.HandlerType.NONCE);
        this.params = ObjectTools.objectToClass(settings.getService().getParams(),Params.class) ;
        Object windowTimeRaw = settings.getSettingMap().get(Settings.WINDOW_TIME);
        this.windowTime = ((Number) windowTimeRaw).longValue();

        this.replyBody.setRequestChecker(this);
    }

    public boolean checkSignInRequestHttp(HttpServletRequest request, HttpServletResponse response){
        String url = HttpTools.getEntireUrl(request);
        String sid = settings.getSid();
        if (illegalUrl(url)) {
            replyBody.replyHttp(CodeMessage.Code1016IllegalUrl, response);
            return false;
        }
        String apiName = getApiNameFromUrl(url);
        setApiName(apiName);

        String fid = request.getHeader(CodeMessage.FidInHeader);
        if (fid == null) {
            String data = "A FID is required in request header.";
            replyBody.replyHttp(CodeMessage.Code1015FidMissed, data, response);
            return false;
        }
        setFid(fid);

        String sign = request.getHeader(CodeMessage.SignInHeader);

        if (params == null || params.getUrlHead() == null) {
            replyBody.replyHttp(CodeMessage.Code1020OtherError, "Failed to get parameters from redis.", response);
            return false;
        }

        if (sign == null || "".equals(sign)) {
            String replyJson = promoteSignInRequest(replyBody, params.getUrlHead());
            try {
                response.getWriter().write(replyJson);
            } catch (IOException e) {
                log.error("Failed to response.");
            }
            return false;
        }

        byte[] requestBodyBytes;
        try {
            requestBodyBytes = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            replyBody.replyHttp(CodeMessage.Code1020OtherError, "Getting request body bytes wrong.", response);
            return false;
        }
        if (requestBodyBytes == null) {
            replyBody.replyHttp(CodeMessage.Code1003BodyMissed, null);
            return false;
        }

        requestBody = getRequestBody(requestBodyBytes, replyBody, response);
        if (requestBody == null) return false;

        if (nonceHandler.isBadNonce(requestBody.getNonce())) {
            replyBody.replyHttp(CodeMessage.Code1007UsedNonce, null);
            return false;
        }
        replyBody.setNonce(this.requestBody.getNonce());

        String pubKey;
        try {
            pubKey = checkAsySignAndGetPubKey(fid, sign, requestBodyBytes);
        } catch (SignatureException e) {
            replyBody.replyHttp(CodeMessage.Code1008BadSign, null);
            return false;
        }
        if (null == pubKey) {
            replyBody.replyHttp(CodeMessage.Code1008BadSign, null);
            return false;
        }
        setPubKey(pubKey);

        if (accountHandler.isBadBalance(fid)) {
            String data = "Send at lest " + params.getMinPayment() + " F to " + params.getDealer() + " to buy the service #" + sid + ".";
            replyBody.replyHttp(CodeMessage.Code1004InsufficientBalance, data, response);
            return false;
        }

        if (isBadUrl(requestBody.getUrl(), url)) {
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("requestedURL", request.getRequestURL().toString());
            dataMap.put("signedURL", requestBody.getUrl());
            replyBody.replyHttp(CodeMessage.Code1005UrlUnequal, dataMap, response);
            return false;
        }

        if (NonceHandler.isBadTime(requestBody.getTime(), windowTime)) {
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put(WINDOW_TIME, String.valueOf(windowTime));
            replyBody.replyHttp(CodeMessage.Code1006RequestTimeExpired, dataMap, response);
            return false;
        }
        if(requestBody.getVia()!=null) setVia(requestBody.getVia());

        setRequestBody(requestBody);
        return true;
    }


    private String checkAsySignAndGetPubKey(String fid, String sign, byte[] requestBodyBytes) throws SignatureException {
        String message = new String(requestBodyBytes);

        sign = sign.replace("\\u003d", "=");

        String signPubKey = ECKey.signedMessageToKey(message, sign).getPublicKeyAsHex();

        String signFid = pubKeyToFchAddr(signPubKey);

        if(signFid.equals(fid))return signPubKey;
        return null;
    }
    public boolean checkRequestHttp(HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        String sid = settings.getSid();

        String url = HttpTools.getEntireUrl(request);
        if (illegalUrl(url)) {
            replyBody.replyHttp(CodeMessage.Code1016IllegalUrl, response);
            return false;
        }

        String apiNameFromUrl = getApiNameFromUrl(url);
        setApiName(apiNameFromUrl);

        boolean isForbidFreeApi = checkForbidFree();

        SignInfo signInfo = null;

        if (authType.equals(FREE)) {
            return checkFreeRequest(request, replyBody, url, response);
        }

        setFreeRequest(false);
        if (authType.equals(FC_SIGN_URL)) {
            signInfo = parseUrlSignInfo(request);
            try {
                Fcdsl fcdsl = Fcdsl.urlParamsToFcdsl(url);
                RequestBody requestBody = new RequestBody();
                requestBody.setFcdsl(fcdsl);
                setRequestBody(requestBody);
            } catch (Exception ignore) {
            }
        } else if (authType.equals(FC_SIGN_BODY)) {
            signInfo = parseBodySignInfo(request);
            setRequestBody(signInfo.requestBody);
        }

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

        if (signInfo == null) {
            if (isForbidFreeApi) {
                replyBody.replyOtherErrorHttp("Failed to check sign.", response);
                return false;
            } else return true;
        }
        if (signInfo.code != 0) {
            if (isForbidFreeApi) {
                replyBody.replyHttp(signInfo.code, null);
                return false;
            } else return true;
        }
        FcSession fcSession = sessionHandler.getSessionByName(signInfo.sessionName);
        if (fcSession == null) {
            replyBody.replyHttp(CodeMessage.Code1009SessionTimeExpired, null);
            return false;
        }

        String fid = fcSession.getId();
        String sessionKey = fcSession.getKey();

        setSessionName(signInfo.sessionName);
        setSessionKey(sessionKey);
        setFid(fid);

        if(accountHandler.isBadBalance(fid)){
            String data = "Send at lest " + params.getMinPayment() + " F to " + params.getDealer() + " to buy the service #" + sid + ".";
            replyBody.replyHttp(CodeMessage.Code1004InsufficientBalance, data, response);
            return false;
        }

        if (nonceHandler.isBadNonce(signInfo.nonce)) {
            replyBody.replyHttp(CodeMessage.Code1007UsedNonce, null, response);
            return false;
        }

        byte[] bytesSigned;
        if (authType.equals(FC_SIGN_URL)) bytesSigned = url.getBytes();
        else bytesSigned = signInfo.requestBodyBytes;

        if (!Signature.verifySha256SymSign(bytesSigned, Hex.fromHex(sessionKey),signInfo.sign)) {
            replyBody.replyHttp(CodeMessage.Code1008BadSign, null, response);
            return false;
        }

        if (authType.equals(FC_SIGN_BODY)) {
            if (isBadUrl(signInfo.url, url)) {
                Map<String, String> dataMap = new HashMap<>();
                dataMap.put("requestedURL", request.getRequestURL().toString());
                dataMap.put("signedURL", signInfo.url);
                replyBody.replyHttp(CodeMessage.Code1005UrlUnequal, dataMap, response);
                return false;
            }
        }

        if (NonceHandler.isBadTime(signInfo.time, windowTime)) {
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put(WINDOW_TIME, String.valueOf(windowTime));
            replyBody.replyHttp(CodeMessage.Code1006RequestTimeExpired, dataMap, response);
            return false;
        }
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
            }
        }catch (Exception ignore){}

        if(requestBody==null){      // && !notForSignRequestBody){
            try {
                requestBodyBytes = request.getInputStream().readAllBytes();
                if (requestBodyBytes != null) {
                    RequestBody requestBody = getRequestBody(requestBodyBytes, replier, response);
                    setRequestBody(requestBody);
                }
            } catch (IOException ignore) {}
        }
        if(requestBody==null)
            setRequestBody(new RequestBody());

        if(requestBody.getFcdsl()==null)
            requestBody.setFcdsl(new Fcdsl());
        return true;
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
        if(requestBody == null)return new SignInfo(CodeMessage.Code1003BodyMissed, null, 0,0,null,null, null, null, null);
        if(requestBody.getNonce()==null)return new SignInfo(CodeMessage.Code1018NonceMissed, null, 0,0,null,null, null, null, null);
        if(requestBody.getTime()==null)return new SignInfo(CodeMessage.Code1019TimeMissed, null, 0,0,null,null, null, null, null);
        if(requestBody.getUrl()==null)return new SignInfo(CodeMessage.Code1024UrlMissed, null, 0,0,null,null, null, null, null);
        if(request.getHeader(SIGN)==null)
            return new SignInfo(CodeMessage.Code1000SignMissed, null, 0,0,null,null, null, null, null);
        if(request.getHeader(SESSION_NAME)==null)
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
        if (requestBody == null) {
            replyBody.replyHttp(CodeMessage.Code1003BodyMissed, null);
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
                    \t\tFid = <Freecash address of the requester>
                    \t\tSign = <The signature of request body signed by the private key of the FID.>
                    \tRequest body:{"url":"%s","nonce":"%d","time":"%d"}"""
                    .formatted(urlHead+ ApipApiNames.VERSION_1 + ApipApiNames.SIGN_IN,nonce,timestamp);
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
                    .formatted(urlHead+ ApipApiNames.VERSION_1 + ApipApiNames.SIGN_IN,nonce,timestamp);
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
                    .formatted(urlHead+ ApipApiNames.VERSION_1 + ApipApiNames.SIGN_IN,nonce,timestamp);
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

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
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
}
