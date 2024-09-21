package clients;

import apip.ApipTools;
import apip.apipData.Session;
import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import fcData.FcReplierHttp;
import fch.FchMainNetwork;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import constants.Constants;
import constants.ReplyCodeMessage;
import constants.UpStrings;
import crypto.Hash;
import fcData.AlgorithmId;
import fcData.Signature;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.StringTools;
import javaTools.http.AuthType;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static apip.ApipTools.isGoodSign;
import static clients.FcClientEvent.RequestBodyType.*;
import static constants.UpStrings.*;
import static javaTools.http.HttpTools.CONTENT_TYPE;

public class FcClientEvent {

    protected static final Logger log = LoggerFactory.getLogger(FcClientEvent.class);
    protected ApiUrl apiUrl;
    protected Fcdsl fcdsl;
    protected Map<String, String> requestHeaderMap;
    protected Signature signatureOfRequest;
    protected Map<String,String>requestParamMap;
    protected RequestBody requestBody;
    protected RequestBodyType requestBodyType;
    protected String requestBodyStr;
    protected byte[] requestBodyBytes;
    protected Map<String, String> responseHeaderMap;
    protected FcReplierHttp responseBody;
    protected String responseBodyStr;
    protected String requestFileName;
    protected ResponseBodyType responseBodyType;
    protected String responseFileName;
    protected String responseFilePath;
    protected byte[] responseBodyBytes;
    protected Signature signatureOfResponse;
    protected HttpResponse httpResponse;
    protected AuthType authType;
    protected String via;
    protected Integer code;
    protected String message;

    public enum RequestBodyType {
        NONE,STRING,BYTES,FILE,FCDSL
    }
    public enum ResponseBodyType {
        FC_REPLY,BYTES,FILE,STRING
    }

    public FcClientEvent() {
    }
    public FcClientEvent(String urlHead, String sn, String ver, String apiName,
                         RequestBodyType requestBodyType,
                         @Nullable Fcdsl fcdsl,
                         @Nullable String requestBodyStr,
                         @Nullable byte[] requestBodyBytes,
                         @Nullable Map<String,String>paramMap,
                         @Nullable String requestFileName,
                         ResponseBodyType responseBodyType,
                         @Nullable String responseFileName,
                         @Nullable String responseFilePath,
                         AuthType authType,
                         @Nullable byte[] authKey,
                         @Nullable String via) {
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        initiate(urlHead, urlTail, requestBodyType, fcdsl, requestBodyStr, requestBodyBytes, paramMap,requestFileName, responseBodyType,responseFileName,responseFilePath,authType, authKey, via);
    }
    public FcClientEvent(String urlHead, String urlTail,
                         RequestBodyType requestBodyType,
                         @Nullable Fcdsl fcdsl,
                         @Nullable String requestBodyStr,
                         @Nullable byte[] requestBodyBytes,
                         @Nullable Map<String,String>paramMap,
                         @Nullable String requestFileName,
                         ResponseBodyType responseBodyType,
                         @Nullable String responseFileName,
                         @Nullable String responseFilePath,
                         AuthType authType,
                         @Nullable byte[] authKey,
                         @Nullable String via) {
        initiate(urlHead, urlTail, requestBodyType, fcdsl, requestBodyStr, requestBodyBytes, paramMap,requestFileName,responseBodyType,responseFileName,responseFilePath, authType, authKey, via);
    }

    private void initiate(String urlHead, String urlTail, RequestBodyType requestBodyType, @Nullable Fcdsl fcdsl, @Nullable String requestBodyStr,@Nullable byte[] requestBodyBytes, @Nullable Map<String, String> paramMap,String requestFileName,
                          @Nullable ResponseBodyType responseBodyType, @Nullable String responseFileName, @Nullable String responseFilePath,
                          AuthType authType, @Nullable byte [] authKey, @Nullable String via) {
        boolean signUrl= AuthType.FC_SIGN_URL.equals(authType);
        apiUrl=new ApiUrl(urlHead, urlTail, paramMap,signUrl, via);
        this.requestBodyType = requestBodyType;
        this.responseBodyType = responseBodyType;
        this.requestFileName = requestFileName;
        this.responseFileName = responseFileName;
        this.responseFilePath = responseFilePath;
        this.authType = authType;
        this.via = via;
        if(this.requestHeaderMap==null)requestHeaderMap = new HashMap<>();
        switch (requestBodyType){
            case FCDSL -> {
                this.fcdsl = fcdsl;
                makeFcdslRequest(urlHead,apiUrl.getUrlTail(), via, fcdsl);
            }
            case STRING -> {
                this.requestBodyStr = requestBodyStr;
                if(requestBodyStr !=null)
                    this.requestBodyBytes = requestBodyStr.getBytes();
                else if (paramMap != null) {
                    requestBody = new RequestBody(apiUrl.getUrl(), via);
                    requestBody.setData(paramMap);
                    if(requestBody!=null)
                        this.requestBodyBytes = JsonTools.toJson(requestBody).getBytes();
                    requestHeaderMap.put(CONTENT_TYPE, javaTools.http.ContentType.APPLICATION_JSON.getType());
                }
                requestHeaderMap.put("Content-Type", javaTools.http.ContentType.TEXT_PLAIN.toString());
            }
            case BYTES -> {
                this.requestBodyBytes = requestBodyBytes;
                requestHeaderMap.put("Content-Type", javaTools.http.ContentType.APPLICATION_OCTET_STREAM.toString());
            }
            case FILE -> {
                this.requestFileName = requestFileName;
                requestHeaderMap.put("Content-Type", javaTools.http.ContentType.APPLICATION_OCTET_STREAM.toString());
            }
            default -> {}
        }

        if(authType.equals(AuthType.FC_SIGN_BODY)){
            makeHeaderSession(authKey, this.requestBodyBytes);
        }else if (AuthType.FC_SIGN_URL.equals(authType))
            makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
    }

    public FcClientEvent(String urlHead, String sn, String ver, String apiName, Map<String,String>paramMap, AuthType authType, byte[] authKey, String via) {
        this.requestParamMap=paramMap;
        this.via=via;
        this.authType=authType;
        switch (authType){
            case FC_SIGN_URL -> {
                apiUrl=new ApiUrl(urlHead, sn,ver,apiName,paramMap, true,via);
                makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
            }
            case FC_SIGN_BODY -> {
                apiUrl=new ApiUrl(urlHead, sn,ver,apiName,null, null,null);
                requestBody=new RequestBody(apiUrl.getUrl(),via);
                requestBody.setData(paramMap);
                requestHeaderMap = new HashMap<>();
                makeRequestBodyBytes();
                makeHeaderSession(authKey,requestBodyBytes);
            }
            default -> apiUrl=new ApiUrl(urlHead, sn,ver,apiName,null, null,via);
        }
    }
    public FcClientEvent(String url) {
        apiUrl=new ApiUrl(url);
    }
    public FcClientEvent(String urlHead, String urlTail) {
        apiUrl=new ApiUrl(urlHead,urlTail,null,null,null);
    }
    public FcClientEvent(String urlHead, String urlTailPath, String apiName) {
        apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,null,null,null);
    }
    public FcClientEvent(String urlHead, String urlTailPath, String apiName, Map<String,String>paramMap) {
        apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,paramMap,false,null);
    }
    public FcClientEvent(String urlHead, String sn, String version, String apiName) {
        apiUrl=new ApiUrl(urlHead, sn, version, apiName,null,null,null);
    }
    public FcClientEvent(String urlHead, String sn, String version, String apiName, Map<String,String>paramMap) {
        apiUrl=new ApiUrl(urlHead, sn, version, apiName,paramMap,false,null);
    }
    public FcClientEvent(String urlHead, String sn, String ver, String apiName, Fcdsl fcdsl, AuthType authType, byte[] authKey, String via) {
        boolean signUrl= AuthType.FC_SIGN_URL.equals(authType);
        apiUrl=new ApiUrl(urlHead, sn, ver, apiName,null,signUrl,via);
        this.fcdsl =fcdsl;
        makeFcdslRequest(urlHead,apiUrl.getUrlTail(),via,fcdsl);
        if(authType.equals(AuthType.FC_SIGN_BODY)){
            makeHeaderSession(authKey,requestBodyBytes);
        }else if (AuthType.FC_SIGN_URL.equals(authType))
            makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
    }

    public FcClientEvent(byte[] authKey, String urlHead, String urlTailPath, String apiName, Fcdsl fcdsl, AuthType authType, String via) {
        urlTailPath=ApiUrl.formatUrlPath(urlTailPath);
        apiUrl=new ApiUrl(urlHead,urlTailPath+apiName);
        this.fcdsl =fcdsl;
        makeFcdslRequest(urlHead,apiUrl.getUrlTail(),via,fcdsl);
        if(authType.equals(AuthType.FC_SIGN_BODY)){
            makeHeaderSession(authKey,requestBodyBytes);
        }else if (AuthType.FC_SIGN_URL.equals(authType))makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
    }



    public int checkResponse() {
        if (responseBody == null) {
            code = ReplyCodeMessage.Code3001ResponseIsNull;
            message = ReplyCodeMessage.Msg3001ResponseIsNull;
            return code;
        }

        if (responseBody.getCode() != 0) {
            code = responseBody.getCode();
            message = responseBody.getMessage();
            return code;
        }

        if (responseBody.getData() == null) {
            code = ReplyCodeMessage.Code3005ResponseDataIsNull;
            message = ReplyCodeMessage.Msg3005ResponseDataIsNull;
            return code;
        }
        return 0;
    }

    public void signInPost(@Nullable String via, byte[] priKey, @Nullable RequestBody.SignInMode mode){
        makeSignInRequest(via, mode);
        makeHeaderAsySign(priKey);
        post();
        if(responseBody!=null){
            code = responseBody.getCode();
            message = responseBody.getMessage();
        }else{
            code = 1020;
            message = "Failed to sign in.";
        }
    }

//
//    public boolean get(ResponseBodyType responseBodyType) {
//        return get(null,responseBodyType,null, null);
//    }
//    public boolean get(String fileName, String responseFilePath){
//        return get(null,ResponseBodyType.FILE,fileName, responseFilePath);
//    }
//
//    public boolean get(byte[] sessionKey) {
//        return get(sessionKey,null,null, null);
//    }
//    public boolean get(byte[] sessionKey, ResponseBodyType responseBodyType) {
//        return get(sessionKey,responseBodyType,null,null );
//    }
//    public boolean get(byte[] sessionKey, String responseFileName, String responseFilePath) {
//        return get(sessionKey,ResponseBodyType.FILE,responseFileName, responseFilePath);
//    }

    public boolean get() {
        return get(null);
    }

    public boolean get(@Nullable byte[] sessionKey){
        if(responseBodyType==null)responseBodyType=ResponseBodyType.FC_REPLY;

        if(authType == AuthType.FC_SIGN_URL || authType == AuthType.FC_SIGN_BODY ){
            if(sessionKey==null){
                code = ReplyCodeMessage.Code1023MissSessionKey;
                message = ReplyCodeMessage.Msg1023MissSessionKey;
                return false;
            }
        }

        if (apiUrl.getUrl() == null) {
            code = ReplyCodeMessage.Code3004RequestUrlIsAbsent;
            message = ReplyCodeMessage.Msg3004RequestUrlIsAbsent;
            System.out.println(message);
            return false;
        }

        try(CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(apiUrl.getUrl());

            // add request headers
            if(requestHeaderMap!=null) {
                for (String head : requestHeaderMap.keySet()) {
                    request.addHeader(head, requestHeaderMap.get(head));
                }
            }

            try {
                httpResponse = httpClient.execute(request);
            }catch (HttpHostConnectException e){
                log.debug("Failed to connect "+apiUrl.getUrl()+". Check the URL.");
                code = ReplyCodeMessage.Code3001ResponseIsNull;
                message = ReplyCodeMessage.Msg3001ResponseIsNull;
                return false;
            }

            if(httpResponse==null){
                code= ReplyCodeMessage.Code3002GetRequestFailed;
                message = ReplyCodeMessage.Msg3002GetRequestFailed;
                return false;
            }

            parseResponseHeader();

            return makeReply(sessionKey);
        } catch (IOException e) {
            log.error("Error when requesting post.", e);
            code = ReplyCodeMessage.Code3007ErrorWhenRequestingPost;
            message = ReplyCodeMessage.Msg3007ErrorWhenRequestingPost+":"+e.getMessage();
            return false;
        }
    }

    private boolean makeReply(byte[] sessionKey) throws IOException {
        switch (responseBodyType){
            case STRING ->{
                return makeStringReply(sessionKey);
            }
            case FC_REPLY ->{
                return makeFcReply(sessionKey);
            }
            case BYTES -> {
                return makeBytesReply(sessionKey);
            }
            case FILE -> {
                return makeFileReply(sessionKey);
            }
            default -> {
                return makeDefaultReply();
            }
        }
    }

    private boolean makeDefaultReply() {
        code = ReplyCodeMessage.Code1020OtherError;
        message = "ResponseBodyType is "+responseBodyType+".";
        return false;
    }

    private boolean makeStringReply(byte[] sessionKey) throws IOException {
        responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
        responseBodyStr = new String(responseBodyBytes);
        code=0;
        return checkReplySign(sessionKey);
    }

    private boolean checkReplySign(byte[] sessionKey) {
        if (responseHeaderMap != null && responseHeaderMap.get(SIGN) != null) {
            if (sessionKey ==null || !checkResponseSign(sessionKey)) {
                code = ReplyCodeMessage.Code1008BadSign;
                message = ReplyCodeMessage.Msg1008BadSign;
                return false;
            }
        }
        return true;
    }

    private boolean makeBytesReply(byte[] sessionKey) throws IOException {
        responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
        if(responseBodyBytes==null){
            code= ReplyCodeMessage.Code1020OtherError;
            message = "The response body is null.";
            return false;
        }
        code= ReplyCodeMessage.Code0Success;
        message=ReplyCodeMessage.Msg0Success;
        return checkReplySign(sessionKey);
    }

    private boolean makeFileReply(byte[] sessionKey) throws IOException {
        String code = responseHeaderMap.get(CODE);
        if(!"0".equals(code)) {
            return makeFcReply(sessionKey);
        }
        String fileName;
        if(responseFileName==null)fileName= StringTools.getTempName();
        else fileName=responseFileName;
        String gotDid = downloadFileFromHttpResponse(fileName, responseFilePath);
        if(gotDid==null){
            this.code = ReplyCodeMessage.Code1020OtherError;
            message = "Failed to download file from HttpResponse.";
            return false;
        }
        if(responseFileName==null)
            Files.move(Paths.get(fileName),Paths.get(gotDid), StandardCopyOption.REPLACE_EXISTING);

        if(responseBody==null)responseBody=new FcReplierHttp();
        responseBody.setCode(ReplyCodeMessage.Code0Success);
        responseBody.setMessage(ReplyCodeMessage.Msg0Success);
        responseBody.setData(gotDid);

        return checkReplySign(sessionKey);
    }

    private boolean makeFcReply(byte[] sessionKey) throws IOException {
        responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
        responseBodyStr = new String(responseBodyBytes);
        parseFcResponse(httpResponse);
        try {
            this.responseBody = new Gson().fromJson(responseBodyStr, FcReplierHttp.class);
        } catch (JsonSyntaxException ignore) {
            log.debug("Failed to parse responseBody json.");
            code= ReplyCodeMessage.Code1020OtherError;
            message = "Failed to parse responseBody from HttpResponse.";
            return false;
        }
        if(!checkReplySign(sessionKey))return false;
        return checkResponseCode();
    }

    private boolean checkResponseCode() {
        if(responseBodyType.equals(ResponseBodyType.FC_REPLY) && responseBody!=null){
            if(responseBody.getMessage()!=null)
                message = responseBody.getMessage();
            if(responseBody.getCode()!=null) {
                code = responseBody.getCode();
            }
        }
        return code == 0;
    }


    public String downloadFileFromHttpResponse(String did, String responseFilePath) {
        if(responseFilePath==null)responseFilePath=System.getProperty(Constants.UserDir);
        if(httpResponse==null)
            return null;
        String finalFileName=did;
        InputStream inputStream = null;
        try {
            inputStream = httpResponse.getEntity().getContent();
        } catch (IOException e) {
            code= ReplyCodeMessage.Code1020OtherError;
            message="Failed to get inputStream from http response.";
            return null;
        }

        while(true) {
            File file = new File(responseFilePath,finalFileName);
            if (!file.exists()) {
                try {
                    boolean done = file.createNewFile();
                    if (!done) {
                        code = ReplyCodeMessage.Code1020OtherError;
                        message = "Failed to create file " + finalFileName;
                        return null;
                    }
                    break;
                } catch (IOException e) {
                    code = ReplyCodeMessage.Code1020OtherError;
                    message = "Failed to create file " + finalFileName;
                    return null;
                }
            }else{
                if(finalFileName.contains("_")){
                    try {
                        int order = Integer.parseInt(finalFileName.substring(finalFileName.indexOf("_")+1));
                        order++;
                        finalFileName = did.substring(0,6)+"_"+order;

                    }catch (Exception ignore){};
                }else{
                    finalFileName = did.substring(0,6)+"_"+1;
                }
            }
        }

        HashFunction hashFunction = Hashing.sha256();
        Hasher hasher = hashFunction.newHasher();

        if(!responseFilePath.endsWith("/"))responseFilePath=responseFilePath+"/";
        try(FileOutputStream outputStream = new FileOutputStream(responseFilePath+finalFileName)){
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                hasher.putBytes(buffer, 0, bytesRead);
            }
            inputStream.close();
        } catch (IOException e) {
            code= ReplyCodeMessage.Code1020OtherError;
            message="Failed to read buffer.";
            return null;
        }

        String didFromResponse = Hex.toHex(Hash.sha256(hasher.hash().asBytes()));

        if(!did.equals(didFromResponse)){
            code= ReplyCodeMessage.Code1020OtherError;
            message="The DID of the file from response is not equal to the requested DID.";
            return null;
        }

        if(!finalFileName.equals(did)){
            try {
                Files.move(Paths.get(responseFilePath,finalFileName), Paths.get(responseFilePath,did), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                code= ReplyCodeMessage.Code1020OtherError;
                message="Failed to replace the old file.";
                return null;
            }
        }

        return didFromResponse;
    }
    public boolean post() {
        return post(null);
    }
//    public boolean post(String requestFileName) {
//        return post(null,RequestBodyType.FILE,null,requestFileName,null,null);
//    }
//    public boolean post(byte[] sessionKey) {
//        return post(sessionKey,null,null,null,null,null);
//    }
//    public boolean post(byte[] sessionKey, RequestBodyType requestBodyType) {
//        return post(sessionKey, requestBodyType,null,null,null,null);
//    }
//    public boolean post(byte[] sessionKey, RequestBodyType requestBodyType,ResponseBodyType responseBodyType) {
//        return post(sessionKey, requestBodyType,responseBodyType,null,null,null);
//    }
//    public boolean post(byte[] sessionKey, RequestBodyType requestBodyType, String fileName) {
//        return post(sessionKey, requestBodyType,null,fileName,null,null);
//    }
//    public boolean post(byte[] sessionKey, ResponseBodyType responseBodyType, String responseFileName, String responseFilePath) {
//        return post(sessionKey, null,responseBodyType,null,responseFileName,responseFilePath);
//    }

    public boolean post(@Nullable byte[] sessionKey) {
        if(this.requestBodyType==null)requestBodyType= STRING;
        if(responseBodyType==null)responseBodyType=ResponseBodyType.FC_REPLY;

        if (apiUrl.getUrl() == null) {
            code = ReplyCodeMessage.Code3004RequestUrlIsAbsent;
            message = ReplyCodeMessage.Msg3004RequestUrlIsAbsent;
            System.out.println(message);
            return false;
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(apiUrl.getUrl());
            if (requestHeaderMap != null) {
                for (String key : requestHeaderMap.keySet()) {
                    httpPost.setHeader(key, requestHeaderMap.get(key));
                }
            }

            switch (requestBodyType){
                case STRING,FCDSL ->{
                    StringEntity entity = new StringEntity(new String(requestBodyBytes));
                    httpPost.setEntity(entity);
                }
                case BYTES -> {
                    ByteArrayEntity entity = new ByteArrayEntity(requestBodyBytes);
                    httpPost.setEntity(entity);
                }
                case FILE -> {
                    File file = new File(requestFileName);
                    if(!file.exists()){
                        this.code = ReplyCodeMessage.Code1020OtherError;
                        message = "File "+requestFileName+" doesn't exist.";
                        return false;
                    }
                    FileInputStream fileInputStream = new FileInputStream(file);
                    HttpEntity entity = new InputStreamEntity(
                            fileInputStream,
                            file.length(),
                            ContentType.APPLICATION_OCTET_STREAM
                    );
                    httpPost.setEntity(entity);
                }
                default -> {
                    return false;
                }
            }

            try {
                httpResponse = httpClient.execute(httpPost);
            }catch (HttpHostConnectException e){
                log.debug("Failed to connect "+apiUrl.getUrl()+". Check the URL.");
                code = ReplyCodeMessage.Code3001ResponseIsNull;
                message = ReplyCodeMessage.Msg3001ResponseIsNull;
                return false;
            }

            if (httpResponse == null) {
                log.debug("httpResponse == null.");
                code = ReplyCodeMessage.Code3001ResponseIsNull;
                message = ReplyCodeMessage.Msg3001ResponseIsNull;
                return false;
            }

            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                log.debug("Post response status: {}.{}", httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase());
                if(httpResponse.getHeaders(UpStrings.CODE)!=null&&httpResponse.getHeaders(UpStrings.CODE).length>0){
                    if(httpResponse.getHeaders(UpStrings.CODE)[0]!=null) {
                        code = Integer.valueOf(httpResponse.getHeaders(UpStrings.CODE)[0].getValue());
                        message = ReplyCodeMessage.getMsg(code);
                        log.debug("Code:{}. Message:{}",code,message);
                    }
                }else {
                    code = ReplyCodeMessage.Code3006ResponseStatusWrong;
                    message = ReplyCodeMessage.Msg3006ResponseStatusWrong + ": " + httpResponse.getStatusLine().getStatusCode();
                    log.debug("Code:{}. Message:{}",code,message);
                }
                return false;
            }

            parseResponseHeader();
            return makeReply(sessionKey);
//            switch (responseBodyType){
//                case STRING ->{
//                    responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
//                    responseBodyStr = new String(responseBodyBytes);
//                    code=0;
//                }
//                case FC_REPLY ->{
//                    if (makeFcReply(sessionKey)) return false;
//                }
//                case BYTES -> responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
//                case FILE -> {
//                    String codeStr = responseHeaderMap.get(CODE);
//                    if(!"0".equals(codeStr))
//                        if (makeFcReply(sessionKey)) return false;
//
//                    String fileName;
//                    if(responseFileName==null)fileName= StringTools.getTempName();
//                    else fileName=responseFileName;
//                    String gotDid = downloadFileFromHttpResponse(fileName,responseFilePath);
//                    if(responseFileName==null){
//                        Files.move(Paths.get(fileName),Paths.get(gotDid), StandardCopyOption.REPLACE_EXISTING);
//                    }
//                    if(responseBody==null)responseBody=new FcReplier();
//                    responseBody.setCode(ReplyCodeMessage.Code0Success);
//                    responseBody.setMessage(ReplyCodeMessage.Msg0Success);
//                    responseBody.setData(gotDid);
//                }
//                default -> {
//                    code = ReplyCodeMessage.Code1020OtherError;
//                    message = "ResponseBodyType is null.";
//                    log.debug("Code:{}. Message:{}",code,message);
//                    return false;
//                }
//            }
        } catch (Exception e) {
            log.error("Error when requesting post.", e);
            code = ReplyCodeMessage.Code3007ErrorWhenRequestingPost;
            message = ReplyCodeMessage.Msg3007ErrorWhenRequestingPost+":"+e.getMessage();
            log.debug("Code:{}. Message:{}",code,message);
            return false;
        }
//
//
//        if (responseHeaderMap != null && responseHeaderMap.get(SIGN) != null) {
//            if (sessionKey==null || !checkResponseSign(sessionKey)) {
//                code = ReplyCodeMessage.Code1008BadSign;
//                message = ReplyCodeMessage.Msg1008BadSign;
//                log.debug("Code:{}. Message:{}",code,message);
//                return false;
//            }
//        }
//        return checkResponseCode();
    }

    private void parseResponseHeader() {
        this.responseHeaderMap = getHeaders(httpResponse);
        String sign = this.responseHeaderMap.get(SIGN);
        String sessionName = this.responseHeaderMap.get(SESSION_NAME);
        this.signatureOfResponse = new Signature(sign, sessionName);
    }

//    public void makeRequestBody() {
//        requestBody = new RequestBody();
//        requestBody.makeRequestBody(apiUrl.getUrl(), requestBody.getVia());
//    }

    protected void makeFcdslRequest(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl) {
        if(apiUrl==null){
            apiUrl=new ApiUrl(urlHead,urlTail);
        }
        String url = ApiUrl.makeUrl(urlHead, urlTail);
        requestBody = new RequestBody(url, via);
        requestBody.setFcdsl(fcdsl);

        Gson gson = new Gson();
        requestBodyStr = gson.toJson(requestBody);
        requestBodyBytes = requestBodyStr.getBytes(StandardCharsets.UTF_8);

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put(CONTENT_TYPE, javaTools.http.ContentType.APPLICATION_JSON.getType());
    }

//    public boolean post(byte[] sessionKey, RequestBodyType requestBodyType, ResponseBodyType responseBodyType, String requestFileName,String responseFileName) {
//
////        if(requestHeaderMap==null)
////            requestHeaderMap = new HashMap<>();
//        if (apiUrl.getUrl() == null) {
//            code = ReplyInfo.Code3004RequestUrlIsAbsent;
//            message = ReplyInfo.Msg3004RequestUrlIsAbsent;
//            System.out.println(message);
//            return false;
//        }
//
//        post(sessionKey,requestBodyType,responseBodyType, requestFileName, responseFileName);
//
//        if (responseHeaderMap != null && responseHeaderMap.get(UpStrings.SIGN) != null) {
//            if (sessionKey==null || !checkResponseSign(sessionKey)) {
//                code = ReplyInfo.Code1008BadSign;
//                message = ReplyInfo.Msg1008BadSign;
//                return false;
//            }
//        }
//
//        if(responseBody!=null){
//            boolean done=false;
//            if(responseBody.getCode()!=null) {
//                code = responseBody.getCode();
//                if(code==0)done=true;
//            }
//            if(responseBody.getMessage()!=null)
//                message = responseBody.getMessage();
//            return done;
//        }
//        return false;
//    }

    protected void makeHeaderAsySign(byte[] priKey) {
        if (priKey == null) return;

        ECKey ecKey = ECKey.fromPrivate(priKey);

        requestBodyStr = new String(requestBodyBytes, StandardCharsets.UTF_8);
        String sign = ecKey.signMessage(requestBodyStr);
        String fid = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();

        signatureOfRequest = new Signature(fid, requestBodyStr, sign, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, null);
        requestHeaderMap.put(UpStrings.FID, fid);
        requestHeaderMap.put(SIGN, signatureOfRequest.getSign());
    }
    protected void makeFcdslRequest(@Nullable String via, @Nullable Fcdsl fcdsl) {
        requestBody = new RequestBody(apiUrl.getUrl(), via);
        if(fcdsl!=null)requestBody.setFcdsl(fcdsl);

        makeRequestBodyBytes();

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    private void makeRequestBodyBytes() {
        Gson gson = new Gson();
        String requestBodyJson = gson.toJson(requestBody);
        requestBodyBytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);
    }

    protected void makeSignInRequest(@Nullable String via, @Nullable RequestBody.SignInMode mode) {
        requestBody = new RequestBody(apiUrl.getUrl(), via, mode);
        makeRequestBodyBytes();
        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    public void makeHeaderSession(byte[] sessionKey,byte[] dataBytes) {
        String sign = Session.getSessionKeySign(sessionKey, dataBytes);
        signatureOfRequest = new Signature(sign, ApipTools.getSessionName(sessionKey));
        if(requestHeaderMap==null)requestHeaderMap=new HashMap<>();
        requestHeaderMap.put(UpStrings.SESSION_NAME, signatureOfRequest.getSymKeyName());
        requestHeaderMap.put(SIGN, signatureOfRequest.getSign());
    }

    protected boolean parseFcResponse(HttpResponse response) {
        if (response == null) return false;
        Gson gson = new Gson();
        String sign;
        try {
            this.responseBody = gson.fromJson(responseBodyStr, FcReplierHttp.class);
        } catch (JsonSyntaxException ignore) {
            return false;
        }

        if (response.getHeaders(SIGN) != null && response.getHeaders(SIGN).length > 0) {
            sign = response.getHeaders(SIGN)[0].getValue();
            this.responseHeaderMap.put(SIGN, sign);
            String symKeyName = null;
            if (response.getHeaders(UpStrings.SESSION_NAME) != null && response.getHeaders(UpStrings.SESSION_NAME).length > 0) {
                symKeyName = response.getHeaders(UpStrings.SESSION_NAME)[0].getValue();
                this.responseHeaderMap.put(UpStrings.SESSION_NAME, symKeyName);
            }
            this.signatureOfResponse = new Signature(sign, symKeyName);
        }
        return true;
    }

    public static Map<String, String> getHeaders(HttpResponse response) {
        Map<String, String> headersMap = new HashMap<>();
        Header[] headers = response.getAllHeaders();

        for (Header header : headers) {
            headersMap.put(header.getName(), header.getValue());
        }

        return headersMap;
    }

    public boolean checkResponseSign(byte[] symKey) {
        return isGoodSign(responseBodyBytes, signatureOfResponse.getSign(), symKey);
    }

    public boolean checkRequestSign(byte[] symKey) {
        return isGoodSign(requestBodyBytes, signatureOfRequest.getSign(), symKey);
    }

    public String getType() {
        return Constants.APIP;
    }

    public Map<String, String> getRequestHeaderMap() {
        return requestHeaderMap;
    }

    public void setRequestHeaderMap(Map<String, String> requestHeaderMap) {
        this.requestHeaderMap = requestHeaderMap;
    }

    public Signature getSignatureOfRequest() {
        return signatureOfRequest;
    }

    public void setSignatureOfRequest(Signature signatureOfRequest) {
        this.signatureOfRequest = signatureOfRequest;
    }

    public RequestBody getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(RequestBody requestBody) {
        this.requestBody = requestBody;
    }

    public String getRequestBodyStr() {
        return requestBodyStr;
    }

    public void setRequestBodyStr(String requestBodyStr) {
        this.requestBodyStr = requestBodyStr;
    }

    public byte[] getRequestBodyBytes() {
        return requestBodyBytes;
    }

    public void setRequestBodyBytes(byte[] requestBodyBytes) {
        this.requestBodyBytes = requestBodyBytes;
    }

    public Map<String, String> getResponseHeaderMap() {
        return responseHeaderMap;
    }

    public void setResponseHeaderMap(Map<String, String> responseHeaderMap) {
        this.responseHeaderMap = responseHeaderMap;
    }

    public FcReplierHttp getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(FcReplierHttp responseBody) {
        this.responseBody = responseBody;
    }

    public String getResponseBodyStr() {
        return responseBodyStr;
    }

    public void setResponseBodyStr(String responseBodyStr) {
        this.responseBodyStr = responseBodyStr;
    }

    public byte[] getResponseBodyBytes() {
        return responseBodyBytes;
    }

    public void setResponseBodyBytes(byte[] responseBodyBytes) {
        this.responseBodyBytes = responseBodyBytes;
    }

    public Signature getSignatureOfResponse() {
        return signatureOfResponse;
    }

    public void setSignatureOfResponse(Signature signatureOfResponse) {
        this.signatureOfResponse = signatureOfResponse;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Fcdsl getFcdsl() {
        return fcdsl;
    }

    public void setFcdsl(Fcdsl fcdsl) {
        this.fcdsl = fcdsl;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public void setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

    public ApiUrl getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(ApiUrl apiUrl) {
        this.apiUrl = apiUrl;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Map<String, String> getRequestParamMap() {
        return requestParamMap;
    }

    public void setRequestParamMap(Map<String, String> requestParamMap) {
        this.requestParamMap = requestParamMap;
    }

    public String getRequestFileName() {
        return requestFileName;
    }

    public void setRequestFileName(String requestFileName) {
        this.requestFileName = requestFileName;
    }

    public String getResponseFileName() {
        return responseFileName;
    }

    public void setResponseFileName(String responseFileName) {
        this.responseFileName = responseFileName;
    }

    public String getResponseFilePath() {
        return responseFilePath;
    }

    public void setResponseFilePath(String responseFilePath) {
        this.responseFilePath = responseFilePath;
    }

    public ResponseBodyType getResponseBodyType() {
        return responseBodyType;
    }

    public void setResponseBodyType(ResponseBodyType responseBodyType) {
        this.responseBodyType = responseBodyType;
    }

    public RequestBodyType getRequestBodyType() {
        return requestBodyType;
    }

    public void setRequestBodyType(RequestBodyType requestBodyType) {
        this.requestBodyType = requestBodyType;
    }
}
