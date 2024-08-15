package server;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import apip.apipData.Session;
import clients.redisClient.RedisTools;
import com.google.gson.Gson;
import constants.ApiNames;
import constants.FieldNames;
import constants.ReplyCodeMessage;
import constants.Strings;
import crypto.Hash;

import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.ObjectTools;
import javaTools.http.AuthType;
import fcData.FcReplier;
import javaTools.http.HttpTools;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static constants.Strings.*;
import static crypto.KeyTools.pubKeyToFchAddr;
import static javaTools.http.AuthType.*;
import static javaTools.http.HttpTools.getApiNameFromUrl;
import static javaTools.http.HttpTools.illegalUrl;
import static server.Settings.addSidBriefToName;

public class RequestChecker {
    /*
    1. is signed?
    2. is body stream?
    3. is free?

    1. native free: getService
    2. set free: get
    3. none free: post
     */
    public static RequestCheckResult checkRequest(String sid, HttpServletRequest request, FcReplier replier,AuthType authType, Jedis jedis,boolean notForSignRequestBody) {

        RequestCheckResult requestCheckResult = new RequestCheckResult();
        replier.setRequestCheckResult(requestCheckResult);

        String url = HttpTools.getEntireUrl(request);
        if(illegalUrl(url)){
            replier.reply(ReplyCodeMessage.Code1016IllegalUrl, null,jedis);
            return null;
        }

        String apiNameFromUrl = getApiNameFromUrl(url);
        requestCheckResult.setApiName(apiNameFromUrl);

        boolean isForbidFreeApi = checkForbidFree(jedis, sid, replier, requestCheckResult);

        SignInfo signInfo = null;

        if(authType.equals(FREE)) {
            return checkFreeRequest(request, replier, jedis, notForSignRequestBody, requestCheckResult, url);
        }

        Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(sid, PARAMS));
        long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, SETTINGS), WINDOW_TIME);

        requestCheckResult.setFreeRequest(false);
        if (authType.equals(FC_SIGN_URL)) {
            signInfo = getUrlSignInfo(request);
            try {
                Fcdsl fcdsl = Fcdsl.urlParamsToFcdsl(url);
                RequestBody requestBody = new RequestBody();
                requestBody.setFcdsl(fcdsl);
                requestCheckResult.setRequestBody(requestBody);
            }catch (Exception ignore){}
        } else if (authType.equals(FC_SIGN_BODY)) {
            signInfo = getBodySignInfo(request);
            requestCheckResult.setRequestBody(signInfo.requestBody);

            if(isBadUrl(signInfo.url,url)){
                Map<String,String> dataMap = new HashMap<>();
                dataMap.put("requestedURL",request.getRequestURL().toString());
                dataMap.put("signedURL",signInfo.url);
                replier.setData(dataMap);
                replier.reply(ReplyCodeMessage.Code1005UrlUnequal, dataMap, jedis);
                return null;
            }
        }

        if(requestCheckResult.getRequestBody()==null)
            requestCheckResult.setRequestBody(new RequestBody());

        if(requestCheckResult.getRequestBody().getFcdsl()==null)
            requestCheckResult.getRequestBody().setFcdsl(new Fcdsl());

        if(signInfo == null){
            if(isForbidFreeApi) {
                replier.replyOtherError("Failed to check sign.", null, jedis);
                return null;
            }else return requestCheckResult;
        }
        if (signInfo.code != 0) {
            if(isForbidFreeApi) {
                replier.reply(signInfo.code, null, jedis);
                return null;
            }else return requestCheckResult;
        }
        Session session = getSession(signInfo.sessionName, jedis);
        if (session == null) {
            replier.reply(ReplyCodeMessage.Code1009SessionTimeExpired, null, jedis);
            return null;
        }

        String fid;
        fid = session.getFid();
        String sessionKey = session.getSessionKey();

        requestCheckResult.setSessionName(signInfo.sessionName);
        requestCheckResult.setSessionKey(sessionKey);
        requestCheckResult.setFid(fid);

        if (isBadBalance(sid, fid, apiNameFromUrl, jedis)) {
            String data = "Send at lest " + paramsMap.get(MIN_PAYMENT) + " F to " + paramsMap.get(ACCOUNT) + " to buy the service #" + sid + ".";
            replier.reply(ReplyCodeMessage.Code1004InsufficientBalance, data, jedis);
            return null;
        }

        if (isBadNonce(signInfo.nonce, windowTime, jedis)) {
            replier.reply(ReplyCodeMessage.Code1007UsedNonce, null, jedis);
            return null;
        }

        replier.setNonce(signInfo.nonce);

        byte[] bytesSigned = null;
        if (authType.equals(FC_SIGN_URL)) bytesSigned = url.getBytes();
        else bytesSigned = signInfo.requestBodyBytes;

        if (isBadSymSign(signInfo.sign, bytesSigned, replier, sessionKey)) {
            replier.reply(ReplyCodeMessage.Code1008BadSign, replier.getData(), jedis);
            return null;
        }

        if (isBadTime(signInfo.time, windowTime)) {
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("windowTime", String.valueOf(windowTime));
            replier.reply(ReplyCodeMessage.Code1006RequestTimeExpired, dataMap, jedis);
            return null;
        }
        if (signInfo.via != null) requestCheckResult.setVia(signInfo.via);

        if (authType.equals(FC_SIGN_URL)) {
            Fcdsl fcdsl = Fcdsl.urlParamsToFcdsl(url);
            if (fcdsl != null) {
                RequestBody requestBody = new RequestBody();
                requestBody.setFcdsl(fcdsl);
                requestCheckResult.setRequestBody(requestBody);
            }
        }
        return requestCheckResult;
    }

    @NotNull
    private static RequestCheckResult checkFreeRequest(HttpServletRequest request, FcReplier replier, Jedis jedis, boolean notForSignRequestBody, RequestCheckResult requestCheckResult, String url) {
        byte[] requestBodyBytes;
        requestCheckResult.setFreeRequest(true);
        try{
            Fcdsl fcdsl = Fcdsl.urlParamsToFcdsl(url);
            if (fcdsl != null) {
                RequestBody requestBody = new RequestBody();
                requestBody.setFcdsl(fcdsl);
                requestCheckResult.setRequestBody(requestBody);
            }
        }catch (Exception ignore){}

        if(requestCheckResult.getRequestBody()==null && !notForSignRequestBody){
            try {
                requestBodyBytes = request.getInputStream().readAllBytes();
                if (requestBodyBytes != null) {
                    RequestBody requestBody = getRequestBody(requestBodyBytes, replier, jedis);
                    requestCheckResult.setRequestBody(requestBody);
                }
            } catch (IOException ignore) {}
        }
        if(requestCheckResult.getRequestBody()==null)
            requestCheckResult.setRequestBody(new RequestBody());

        if(requestCheckResult.getRequestBody().getFcdsl()==null)
            requestCheckResult.getRequestBody().setFcdsl(new Fcdsl());
        return requestCheckResult;
    }

    private static SignInfo getUrlSignInfo(HttpServletRequest request) {
        int code=0;
        String nonceStr = request.getParameter(NONCE);
        if(nonceStr==null){
            code = ReplyCodeMessage.Code1018NonceMissed;
            return new SignInfo(code, null, 0,0,null,null, null, null, null);
        }
        long nonce = Long.parseLong(nonceStr);
        String timeStr = request.getParameter(TIME);
        if(timeStr==null) {
            code = ReplyCodeMessage.Code1019TimeMissed;
            return new SignInfo(code, null, 0,0,null,null, null, null, null);
        }
        long time = Long.parseLong(timeStr);

        String via = request.getParameter(VIA);

        String sign = request.getHeader(ReplyCodeMessage.SignInHeader);
        if(sign==null) {
            code = ReplyCodeMessage.Code1000SignMissed;
            return new SignInfo(code, null, 0,0,null,null, null, null, null);
        }

        String sessionName = request.getHeader(ReplyCodeMessage.SessionNameInHeader);
        if(sessionName==null) {
            code = ReplyCodeMessage.Code1002SessionNameMissed;
            return new SignInfo(code, null, 0,0,null,null, null, null, null);
        }

        return new SignInfo(code, null, nonce,time,via,sign, sessionName, null, null);
    }

    private static SignInfo getBodySignInfo(HttpServletRequest request) {
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
        if(requestBody == null)return new SignInfo(ReplyCodeMessage.Code1003BodyMissed, null, 0,0,null,null, null, null, null);
        if(requestBody.getNonce()==null)return new SignInfo(ReplyCodeMessage.Code1018NonceMissed, null, 0,0,null,null, null, null, null);
        if(requestBody.getTime()==null)return new SignInfo(ReplyCodeMessage.Code1019TimeMissed, null, 0,0,null,null, null, null, null);
        if(requestBody.getUrl()==null)return new SignInfo(ReplyCodeMessage.Code1024UrlMissed, null, 0,0,null,null, null, null, null);
        if(request.getHeader(SIGN)==null)
            return new SignInfo(ReplyCodeMessage.Code1000SignMissed, null, 0,0,null,null, null, null, null);
        if(request.getHeader(SESSION_NAME)==null)
            return new SignInfo(ReplyCodeMessage.Code1002SessionNameMissed, null, 0,0,null,null, null, null, null);
        return new SignInfo(ReplyCodeMessage.Code0Success, requestBody.getUrl(),requestBody.getNonce(), requestBody.getTime(), requestBody.getVia(), request.getHeader(SIGN),request.getHeader(SESSION_NAME), requestBodyBytes, requestBody);
    }

    @Nullable
    public static Map<String, String> parseOtherMap(FcReplier replier, Jedis jedis, Object other) {
        Map<String,String> otherMap = ObjectTools.objectToMap(other,String.class,String.class);
        if(otherMap==null){
            replier.replyOtherError("The other parameter has to be a Map<String,String>.",null, jedis);
            return null;
        }
        return otherMap;
    }

    public static boolean isBadHex(FcReplier replier, Jedis jedis, String text) {
        if(!Hex.isHexString(text)){
            replier.replyOtherError("It is not a hex string.", text, jedis);
            return true;
        }
        return false;
    }

    private record SignInfo(int code, String url, long nonce, long time, String via, String sign, String sessionName,
                            byte[] requestBodyBytes, RequestBody requestBody){
    }

    private static Boolean checkForbidFree(Jedis jedis, String sid, FcReplier replier, RequestCheckResult requestCheckResult) {
        boolean isForbidFreeApi;
        String isForbidFreeApiStr = jedis.hget(Settings.addSidBriefToName(sid, SETTINGS), FieldNames.FORBID_FREE_API);
        isForbidFreeApi = "true".equalsIgnoreCase(isForbidFreeApiStr);
        requestCheckResult.setFreeRequest(isForbidFreeApi);
        return isForbidFreeApi;
    }
    private static boolean isBadSymSign(String sign, byte[] requestBodyBytes, FcReplier replier, String sessionKey) {
        if(sign==null)return true;
        byte[] signBytes = BytesTools.bytesMerger(requestBodyBytes, Hex.fromHex(sessionKey));
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));

        if(!sign.equals(doubleSha256Hash)){
            replier.setData("The sign of the request body should be: "+doubleSha256Hash);
            return true;
        }
        return false;
    }

    public static Session getSession(String sessionName,Jedis jedis) {
        Session session;

        jedis.select(1);

        String fid = jedis.hget(sessionName, "fid");
        String sessionKey = jedis.hget(sessionName, "sessionKey");

        jedis.select(0);
        if (fid == null || sessionKey == null) {
            return null;
        }

        session = new Session();
        session.setFid(fid);
        session.setSessionKey(sessionKey);
        session.setSessionName(sessionName);

        return session;
    }

//    @Nullable
//    public static RequestCheckResult checkRequest(String sid, HttpServletRequest request, FcReplier replier, AuthType authType, Jedis jedis, boolean isRequestBodyStream) {
//
//        RequestCheckResult requestCheckResult = new RequestCheckResult();
//        replier.setRequestCheckResult(requestCheckResult);
//        boolean isForbidFreeApi;
//        String url = request.getRequestURL().toString();
//        requestCheckResult.setApiName(HttpTools.getApiNameFromUrl(url));
//        String queryString = request.getQueryString();
//        if (queryString != null) {
//            url += "?" + queryString;
//        }
//        if(authType.equals(FREE)){
//            requestCheckResult.setFreeRequest(Boolean.TRUE);
//            RequestBody requestBody;
//            try {
//                byte[]  requestBodyBytes = request.getInputStream().readAllBytes();
//                requestBody = getRequestBody(requestBodyBytes,replier,jedis);
//            } catch (IOException ignore) {
//                requestBody=null;
//            }
//            if(requestBody!=null){
//                requestCheckResult.setRequestBody(requestBody);
//            }else {
//                try {
//                    Fcdsl fcdsl = Fcdsl.urlParamsToFcdsl(url);
//                    requestBody = new RequestBody();
//                    requestBody.setFcdsl(fcdsl);
//                    requestCheckResult.setRequestBody(requestBody);
//                }catch (Exception ignore){};
//            }
//        }
//        else {
//            String sign = request.getHeader(SIGN);
//            String sessionName = request.getHeader(SESSION_NAME);
//
//            if(sign == null || sessionName == null) {
//                if (checkForbidFree(jedis, sid, replier, requestCheckResult)) return null;
//                checkFreeRequest(request,replier,requestCheckResult,jedis,isRequestBodyStream);
//            }else{
//                if(authType.equals(FC_SIGN_URL))
//                    requestCheckResult = checkUrlSignRequest(sid, request, replier, requestCheckResult,jedis);
//                else {
//                    if(authType.equals(FC_SIGN_BODY)) {
//                        requestCheckResult =  checkBodySignRequest(sid, request, replier, requestCheckResult,jedis, isRequestBodyStream);
//                    }
//                    else {
//                        replier.reply(ReplyCodeMessage.Code1020OtherError, "Wrong AuthType.", jedis);
//                        return null;
//                    }
//                }
//            }
//        }
//        return requestCheckResult;
//    }

    @Nullable
    public static Map<String, String> checkOtherRequest(String sid, HttpServletRequest request, AuthType authType, FcReplier replier, Jedis jedis) {
        RequestCheckResult requestCheckResult = checkRequest(sid, request, replier, authType, jedis, false);
        if (requestCheckResult == null) {
            return null;
        }
        RequestBody requestBody = requestCheckResult.getRequestBody();
        if (requestBody == null) {
            replier.reply(ReplyCodeMessage.Code1003BodyMissed, null, jedis);
            return null;
        }
        Fcdsl fcdsl = requestBody.getFcdsl();
        if (fcdsl==null) {
            replier.replyOtherError("The FCDSL is missed.", null, jedis);
            return null;
        }
        replier.setNonce(requestBody.getNonce());
        //Check API
        Map<String,String> other = requestBody.getFcdsl().getOther();
        if (other == null) {
            replier.replyOtherError("The other parameter is missed. Check it.", null, jedis);
            return null;
        }
        return other;
    }

    public RequestCheckResult checkSignInRequest(String sid, HttpServletRequest request, FcReplier replier, Map<String, String> paramsMap, long windowTime, Jedis jedis){
        RequestCheckResult requestCheckResult = new RequestCheckResult();
        replier.setRequestCheckResult(requestCheckResult);
        String url = HttpTools.getEntireUrl(request);
        if(illegalUrl(url)){
            replier.reply(ReplyCodeMessage.Code1016IllegalUrl, null, jedis);
            return null;
        }
        String apiName = HttpTools.getApiNameFromUrl(url);
        requestCheckResult.setApiName(apiName);

        String fid = request.getHeader(ReplyCodeMessage.FidInHeader);
        if(fid==null){
            String data = "A FID is required in request header.";
            replier.reply(ReplyCodeMessage.Code1015FidMissed, data,jedis);
            return null;
        }
        requestCheckResult.setFid(fid);

        String sign = request.getHeader(ReplyCodeMessage.SignInHeader);

        if (paramsMap==null||paramsMap.get(URL_HEAD)==null) {
            replier.reply(ReplyCodeMessage.Code1020OtherError, "Failed to get parameters from redis.", jedis);
            return null;
        }

        if(sign==null||"".equals(sign)){
            promoteSignInRequest(replier, paramsMap.get(URL_HEAD),jedis);
            return null;
        }

        byte[] requestBodyBytes;
        try {
            requestBodyBytes = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            replier.reply(ReplyCodeMessage.Code1020OtherError, "Getting request body bytes wrong.", jedis);
            return null;
        }
        if(requestBodyBytes==null){
            replier.reply(ReplyCodeMessage.Code1003BodyMissed, null, jedis);
            return null;
        }

        RequestBody signInRequestBody = getRequestBody(requestBodyBytes,replier, jedis);
        if(signInRequestBody==null)return null;
        if(isBadNonce(signInRequestBody.getNonce(), windowTime,jedis )){
            replier.reply(ReplyCodeMessage.Code1007UsedNonce, null,jedis);
            return null;
        }
        replier.setNonce(signInRequestBody.getNonce());

        String pubKey;
        try {
            pubKey = checkAsySignAndGetPubKey(fid,sign,requestBodyBytes);
        } catch (SignatureException e) {
            replier.reply(ReplyCodeMessage.Code1008BadSign, null, jedis);
            return null;
        }
        if(null==pubKey){
            replier.reply(ReplyCodeMessage.Code1008BadSign, null,jedis);
            return null;
        }
        requestCheckResult.setPubKey(pubKey);

        if(isBadBalance(sid,fid,apiName, jedis)){
            String data = "Send at lest "+paramsMap.get(MIN_PAYMENT)+" F to "+paramsMap.get(ACCOUNT)+" to buy the service #"+sid+".";
            replier.reply(ReplyCodeMessage.Code1004InsufficientBalance, data, jedis);
            return null;
        }

        if(isBadUrl(signInRequestBody.getUrl(),url)){
            Map<String,String> dataMap = new HashMap<>();
            dataMap.put("requestedURL",request.getRequestURL().toString());
            dataMap.put("signedURL",signInRequestBody.getUrl());
            replier.setData(dataMap);
            replier.reply(ReplyCodeMessage.Code1005UrlUnequal, replier.getData(), jedis);
            return null;
        }

        if (isBadTime(signInRequestBody.getTime(),windowTime)){
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("windowTime", String.valueOf(windowTime));
            replier.reply(ReplyCodeMessage.Code1006RequestTimeExpired, dataMap, jedis);
            return null;
        }
        if(signInRequestBody.getVia()!=null) requestCheckResult.setVia(signInRequestBody.getVia());

        requestCheckResult.setRequestBody(signInRequestBody);
        return requestCheckResult;
    }

    private static void promoteSignInRequest(FcReplier replier, String urlHead, Jedis jedis) {
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
                    .formatted(urlHead+ApiNames.Version1 + ApiNames.SignIn,nonce,timestamp);
        }
        replier.reply(ReplyCodeMessage.Code1000SignMissed, data,jedis);
    }

    private static void promoteJsonRequest(FcReplier replier, String urlHead, Jedis jedis) {
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
                    \t\tSessionName = <The session name which is the hex of the first 6 bytes of the sessionKey>
                    \t\tSign = <The value of double sha256 of the request body bytes adding the sessionKey bytes.>
                    \tRequest body:
                    \t\t{
                    \t\t\t"url":%s,
                    \t\t\t"nonce":%d
                    \t\t\t"time":%d
                    \t\t\t<your request parameters...>
                    \t\t}
                    """
                    .formatted(urlHead+ApiNames.Version1 + ApiNames.SignIn,nonce,timestamp);
        }
        replier.reply(ReplyCodeMessage.Code1000SignMissed, data,jedis);
    }

    private static void promoteUrlSignRequest(FcReplier replier, String urlHead, Jedis jedis) {
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
                    \t\tSessionName = <The session name which is the hex of the first 6 bytes of the sessionKey>
                    \t\tSign = <The value of double sha256 of the whole requested URL bytes adding the sessionKey bytes.>
                    \tRequest URL should be:
                    \t\t\t%s/"nonce=%s&time=%s<your more parameters>
                    """
                    .formatted(urlHead+ApiNames.Version1 + ApiNames.SignIn,nonce,timestamp);
        }
        replier.reply(ReplyCodeMessage.Code1000SignMissed, data, jedis);
    }

    private static RequestBody getRequestBody(byte[] requestBodyBytes, FcReplier replier, Jedis jedis) {
        String requestDataJson = new String(requestBodyBytes);
        RequestBody connectRequestBody;
        try {
            connectRequestBody = new Gson().fromJson(requestDataJson, RequestBody.class);
        }catch(Exception e){
            replier.reply(ReplyCodeMessage.Code1013BadRequest, "Parsing request body wrong.", jedis);
            return null;
        }
        return connectRequestBody;
    }

    public static boolean isBadNonce(long nonce, long windowTime, Jedis jedis){

        if(windowTime==0)return false;

        jedis.select(2);
        if (nonce == 0) return true;
        String nonceStr = String.valueOf(nonce);
        if (jedis.get(nonceStr) != null)
            return true;
        jedis.set(nonceStr, "");
        jedis.expire(nonceStr, windowTime);
        jedis.select(0);
        return false;
    }
    public static boolean isBadBalance(String sid, String fid, String apiName, Jedis jedis){

        long nPrice;
        if(apiName!=null)nPrice = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, Strings.N_PRICE), apiName);
        else nPrice=1;
        double price;
        String priceStr;

        Map<String, String> paramsMap = jedis.hgetAll(addSidBriefToName(sid, PARAMS));

        if(fid.equals(paramsMap.get(ACCOUNT)))return false;

        jedis.select(0);
        priceStr = paramsMap.get(PRICE_PER_K_BYTES);
        if(priceStr==null)priceStr = paramsMap.get(PRICE_PER_REQUEST);
        if(priceStr==null)return false;
        price = Double.parseDouble(priceStr);
        long balance = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, BALANCE),fid);
        return balance < nPrice*price*100000000;
    }

    private String checkAsySignAndGetPubKey(String fid, String sign, byte[] requestBodyBytes) throws SignatureException {
        String message = new String(requestBodyBytes);

        sign = sign.replace("\\u003d", "=");

        String signPubKey = ECKey.signedMessageToKey(message, sign).getPublicKeyAsHex();

        String signFid = pubKeyToFchAddr(signPubKey);

        if(signFid.equals(fid))return signPubKey;
        return null;
    }

    public static boolean isBadTime(long userTime, long windowTime){
        if(windowTime==0)return false;
        long currentTime = System.currentTimeMillis();
        return Math.abs(currentTime - userTime) > windowTime;
    }
    public static boolean isBadUrl(String signedUrl, String requestUrl){
        return !requestUrl.equals(signedUrl);
    }
}
