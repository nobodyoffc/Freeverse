package diskClient;

import clients.FcClientEvent;

public class DiskClientEvent extends FcClientEvent {



//    private static final Logger log = LoggerFactory.getLogger(DiskClientData.class);
//
//    private String url;
//    private Map<String, String> requestHeaderMap;
//    private Signature signatureRequest;
//    private RequestBody requestBody;
//    private String requestBodyStr;
//    private byte[] requestBodyBytes;
//    private RequestBody signInRequestBody;
//    private byte[] signInRequestBodyBytes;
//    private Map<String, String> responseHeaderMap;
//    private ResponseBody responseBody;
//    private String responseBodyStr;
//    private byte[] responseBodyBytes;
//    private Signature signatureResponse;
//    private HttpResponse httpResponse;
//    private int code;
//    private String message;
//
//
//    public void get(String url, Map<String, String> requestHeaderMap) {
//
//        CloseableHttpClient httpClient = HttpClients.createDefault();
//
//        try {
//            HttpGet request = new HttpGet(url);
//
//            // add request headers
//            if (requestHeaderMap != null) {
//                for (String head : requestHeaderMap.keySet()) {
//                    request.addHeader(head, requestHeaderMap.get(head));
//                }
//            }
//            HttpResponse httpResponse = httpClient.execute(request);
//
//            if (httpResponse == null) {
//                code = ReplyInfo.Code3001ResponseIsNull;
//                message = ReplyInfo.Msg3001ResponseIsNull;
//                return;
//            }
//
//            responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
//
//            responseBodyStr = new String(responseBodyBytes);
//
//            parseApipResponse(httpResponse);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            code = ReplyInfo.Code3002GetRequestFailed;
//            message = ReplyInfo.Msg3002GetRequestFailed;
//        } finally {
//            try {
//                httpClient.close();
//            } catch (Exception e) {
//                e.printStackTrace();
//                code = ReplyInfo.Code3003CloseHttpClientFailed;
//                message = ReplyInfo.Msg3003CloseHttpClientFailed;
//            }
//        }
//    }
//
//    public void get() {
//        get(url, requestHeaderMap);
//    }
//
//    public boolean post(byte[] sessionKey) {
//        if (url== null) {
//            code = ReplyInfo.Code3004RequestUrlIsAbsent;
//            message = ReplyInfo.Msg3004RequestUrlIsAbsent;
//            System.out.println(message);
//            return false;
//        }
//        if (requestBodyBytes == null) {
//            code = ReplyInfo.Code1003BodyMissed;
//            message = ReplyInfo.Msg1003BodyMissed;
//            System.out.println(message);
//            return false;
//        }
//
//        doPost();
//
//        if (responseHeaderMap != null && responseHeaderMap.get(UpStrings.SIGN) != null) {
//            if (!checkResponseSign(sessionKey)) {
//                code = ReplyInfo.Code1008BadSign;
//                message = ReplyInfo.Msg1008BadSign;
//                System.out.println(message);
//                return false;
//            }
//        }
//        message = UpStrings.SUCCESS;
//        return true;
//    }
//
//    public boolean post(String urlHead, String urlTail, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
//        prepare(urlHead, urlTail, via, fcdsl);
//        makeHeaderSession(sessionKey);
//        return post(sessionKey);
//    }
//
//    private void prepare(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl) {
//        requestBody = new RequestBody(url, via);
//        requestBody.setFcdsl(fcdsl);
//
//        Gson gson = new Gson();
//        String requestBodyJson = gson.toJson(requestBody);
//        requestBodyBytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);
//
//        requestHeaderMap = new HashMap<>();
//        requestHeaderMap.put("Content-Type", "application/json");
//    }
//
//    public void asySignPost(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl, byte[] priKey, @Nullable RequestBody.SignInMode modeNullOrRefresh) throws IOException {
//        prepare(urlHead, urlTail, via, fcdsl, modeNullOrRefresh);
//        makeHeaderAsySign(priKey);
//        doPost();
//    }
//
//    private void makeHeaderAsySign(byte[] priKey) {
//        if (priKey == null) return;
//
//        ECKey ecKey = ECKey.fromPrivate(priKey);
//
//        requestBodyStr = new String(requestBodyBytes, StandardCharsets.UTF_8);
//        String sign = ecKey.signMessage(requestBodyStr);
//        String fid = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
//
//        signatureRequest = new Signature(fid, requestBodyStr, sign, Algorithm.EccAes256K1P7_No1_NrC7.name());
//        requestHeaderMap.put(UpStrings.FID, fid);
//        requestHeaderMap.put(UpStrings.SIGN, signatureRequest.getSign());
//    }
//
//    private void prepare(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl, @Nullable RequestBody.SignInMode modeNullOrRefresh) {
//        requestBody = new RequestBody(url, via, modeNullOrRefresh);
//        requestBody.setFcdsl(fcdsl);
//
//        Gson gson = new Gson();
//        String requestBodyJson = gson.toJson(requestBody);
//        requestBodyBytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);
//
//        requestHeaderMap = new HashMap<>();
//        requestHeaderMap.put("Content-Type", "application/json");
//    }
//
//    private void makeHeaderSession(byte[] sessionKey) {
//        String sign = ApipTools.getSessionKeySign(sessionKey, requestBodyBytes);
//        signatureRequest = new Signature(sign, ApipTools.getSessionName(sessionKey));
//        requestHeaderMap.put(UpStrings.SESSION_NAME, signatureRequest.getSymKeyName());
//        requestHeaderMap.put(UpStrings.SIGN, signatureRequest.getSign());
//    }
//
//    private void doPost() {
//        CloseableHttpResponse httpResponse;
//
//        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
//
//            HttpPost httpPost = new HttpPost(url);
//            if (requestHeaderMap != null) {
//                for (String key : requestHeaderMap.keySet()) {
//                    httpPost.setHeader(key, requestHeaderMap.get(key));
//                }
//            }
//
//            StringEntity entity = new StringEntity(new String(requestBodyBytes));
//
//            httpPost.setEntity(entity);
//
//            httpResponse = httpClient.execute(httpPost);
//
//            if (httpResponse == null) {
//                log.debug("httpResponse == null.");
//                code = ReplyInfo.Code3001ResponseIsNull;
//                message = ReplyInfo.Msg3001ResponseIsNull;
//                return;
//            }
//
//            if (httpResponse.getStatusLine().getStatusCode() != 200) {
//                log.debug("Post response status: {}.{}", httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase());
//                code = ReplyInfo.Code3006ResponseStatusWrong;
//                message = ReplyInfo.Msg3006ResponseStatusWrong + ": " + httpResponse.getStatusLine().getStatusCode();
//                return;
//            }
//
//            responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
//
//            responseBodyStr = new String(responseBodyBytes);
//            parseApipResponse(httpResponse);
//        } catch (Exception e) {
//            e.printStackTrace();
//            log.error("Error when requesting post.", e);
//            code = ReplyInfo.Code3007ErrorWhenRequestingPost;
//            message = ReplyInfo.Msg3007ErrorWhenRequestingPost;
//        }
//    }
//
//    private void parseApipResponse(HttpResponse response) {
//        if (response == null) return;
//        Gson gson = new Gson();
//        String sign;
//        try {
//            this.responseBody = gson.fromJson(responseBodyStr, ResponseBody.class);
//        } catch (JsonSyntaxException ignore) {
//        }
//
//        if (response.getHeaders(UpStrings.SIGN) != null && response.getHeaders(UpStrings.SIGN).length > 0) {
//            sign = response.getHeaders(UpStrings.SIGN)[0].getValue();
//            this.responseHeaderMap = new HashMap<>();
//            this.responseHeaderMap.put(UpStrings.SIGN, sign);
//            String symKeyName = null;
//            if (response.getHeaders(UpStrings.SESSION_NAME) != null && response.getHeaders(UpStrings.SESSION_NAME).length > 0) {
//                symKeyName = response.getHeaders(UpStrings.SESSION_NAME)[0].getValue();
//                this.responseHeaderMap.put(UpStrings.SESSION_NAME, symKeyName);
//            }
//            this.signatureResponse = new Signature(sign, symKeyName);
//        }
//    }
//
//    public boolean checkResponseSign(byte[] symKey) {
//        return isGoodSign(responseBodyBytes, signatureResponse.getSign(), symKey);
//    }
//
//    public boolean checkRequestSign(byte[] symKey) {
//        return isGoodSign(requestBodyBytes, signatureRequest.getSign(), symKey);
//    }
//
//
//    public String getType() {
//        return Constants.APIP;
//    }
//
//
//    public String getVer() {
//        return Constants.V1;
//    }
//
//    public Map<String, String> getRequestHeaderMap() {
//        return requestHeaderMap;
//    }
//
//    public void setRequestHeaderMap(Map<String, String> requestHeaderMap) {
//        this.requestHeaderMap = requestHeaderMap;
//    }
//
//    public RequestBody getRequestBody() {
//        return requestBody;
//    }
//
//    public void setRequestBody(RequestBody requestBody) {
//        this.requestBody = requestBody;
//    }
//
//    public RequestBody getSignInRequestBody() {
//        return signInRequestBody;
//    }
//
//    public void setSignInRequestBody(RequestBody signInRequestBody) {
//        this.signInRequestBody = signInRequestBody;
//    }
//
//    public Map<String, String> getResponseHeaderMap() {
//        return responseHeaderMap;
//    }
//
//    public void setResponseHeaderMap(Map<String, String> responseHeaderMap) {
//        this.responseHeaderMap = responseHeaderMap;
//    }
//
//    public ResponseBody getResponseBody() {
//        return responseBody;
//    }
//
//    public void setResponseBody(ResponseBody responseBody) {
//        this.responseBody = responseBody;
//    }
//
//    public Signature getSignatureRequest() {
//        return signatureRequest;
//    }
//
//    public void setSignatureRequest(Signature signatureRequest) {
//        this.signatureRequest = signatureRequest;
//    }
//
//    public Signature getSignatureResponse() {
//        return signatureResponse;
//    }
//
//    public void setSignatureResponse(Signature signatureResponse) {
//        this.signatureResponse = signatureResponse;
//    }
//
//    public byte[] getRequestBodyBytes() {
//        return requestBodyBytes;
//    }
//
//    public void setRequestBodyBytes(byte[] requestBodyBytes) {
//        this.requestBodyBytes = requestBodyBytes;
//    }
//
//    public byte[] getSignInRequestBodyBytes() {
//        return signInRequestBodyBytes;
//    }
//
//    public void setSignInRequestBodyBytes(byte[] signInRequestBodyBytes) {
//        this.signInRequestBodyBytes = signInRequestBodyBytes;
//    }
//
//    public byte[] getResponseBodyBytes() {
//        return responseBodyBytes;
//    }
//
//    public void setResponseBodyBytes(byte[] responseBodyBytes) {
//        this.responseBodyBytes = responseBodyBytes;
//    }
//
//    public String getResponseBodyStr() {
//        return responseBodyStr;
//    }
//
//    public void setResponseBodyStr(String responseBodyStr) {
//        this.responseBodyStr = responseBodyStr;
//    }
//
//    public HttpResponse getHttpResponse() {
//        return httpResponse;
//    }
//
//    public void setHttpResponse(HttpResponse httpResponse) {
//        this.httpResponse = httpResponse;
//    }
//
//    public int getCode() {
//        return code;
//    }
//
//    public void setCode(int code) {
//        this.code = code;
//    }
//
//    public String getMessage() {
//        return message;
//    }
//
//    public void setMessage(String message) {
//        this.message = message;
//    }
//
//    public String getRequestBodyStr() {
//        return requestBodyStr;
//    }
//
//    public void setRequestBodyStr(String requestBodyStr) {
//        this.requestBodyStr = requestBodyStr;
//    }
//
//    public String getUrl() {
//        return url;
//    }
//
//    public void setUrl(String url) {
//        this.url = url;
//    }
}
