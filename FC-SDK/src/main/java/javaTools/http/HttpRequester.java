package javaTools.http;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class HttpRequester {
    public static int Code1ResponseIsNull = 1;
    public static String Msg1ResponseIsNull = "Http response is null.";
    public static int Code2GetRequestFailed = 2;
    public static String Msg2GetRequestFailed = "The request of GET is failed.";
    public static int Code3CloseHttpClietFailed = 3;
    public static String Msg3CloseHttpClietFailed = "Failed to close the http client.";
    public static int Code4RequestUrlIsAbsent = 4;
    public static String Msg4RequestUrlIsAbsent = "The URL of requesting is absent.";
    public static int Code5RequestBodyIsAbsent = 5;
    public static String Msg5RequestBodyIsAbsent = "The request body is absent.";
    public static int Code6ResponseStatusWrong = 6;
    public static String Msg6ResponseStatusWrong = "The status of response is";
    public static int Code7DoPostRequestWrong = 7;
    public static String Msg7DoPostRequestWrong = "Do post request wrong.";
    public static int Code8BadResponseSign = 8;
    public static String Msg8BadResponseSign = "The sign in header is not correct to the response body.";
    public static int Code9BadQuery = 9;
    public static String Msg9BadQuery = "Bad query for this API.";
    public static int Code10ResponseDataIsNull = 10;
    public static String Msg10ResponseDataIsNull = "The data object in response body is null.";
    private final Logger log = LoggerFactory.getLogger(HttpRequester.class);
    private int code;
    private String message;
    private String responseBodyStr;

    public void request(String getOrPost, String endpoint, Map<String, String> requestHeaderMap, @Nullable String requestBody) {
        CloseableHttpResponse httpResponse;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpGet httpGet = null;
            HttpPost httpPost = null;
            if (getOrPost.equalsIgnoreCase("get")) {
                httpGet = new HttpGet(endpoint);
            } else if (getOrPost.equalsIgnoreCase("post")) {
                httpPost = new HttpPost(endpoint);
            } else return;

            if (requestHeaderMap != null) {
                for (String key : requestHeaderMap.keySet()) {
                    if (getOrPost.equalsIgnoreCase("get")) httpGet.setHeader(key, requestHeaderMap.get(key));
                    else httpPost.setHeader(key, requestHeaderMap.get(key));
                }
            }

            if (getOrPost.equalsIgnoreCase("post")) {
                StringEntity entity = new StringEntity(requestBody);
                httpPost.setEntity(entity);
            }

            if (getOrPost.equalsIgnoreCase("post")) {
                httpResponse = httpClient.execute(httpPost);
            } else httpResponse = httpClient.execute(httpGet);

            if (httpResponse == null) {
                log.debug("httpResponse == null.");
                code = Code1ResponseIsNull;
                message = Msg1ResponseIsNull;
                return;
            }

            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                log.debug("Post response status: {} {}", httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase());
                code = Code6ResponseStatusWrong;
                message = Msg6ResponseStatusWrong + ": " + httpResponse.getStatusLine().getStatusCode() + " " + httpResponse.getStatusLine().getReasonPhrase();
                return;
            }

            byte[] responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();

            responseBodyStr = new String(responseBodyBytes);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Do post request wrong.");
            code = Code7DoPostRequestWrong;
            message = Msg7DoPostRequestWrong;
        }
    }

    public Logger getLog() {
        return log;
    }

    public int getCode() {
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

    public String getResponseBodyStr() {
        return responseBodyStr;
    }

    public void setResponseBodyStr(String responseBodyStr) {
        this.responseBodyStr = responseBodyStr;
    }
}
