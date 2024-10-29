package javaTools.http;

import clients.ApiUrl;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpTools {


    private static final Logger log = LoggerFactory.getLogger(HttpTools.class);
    public static final String CONTENT_TYPE = "Content-Type";

    public static String getApiNameFromUrl(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex == -1 || lastSlashIndex == url.length() - 1)return null;
        String name = url.substring(lastSlashIndex + 1);

        int firstQuestionIndex = name.indexOf('?');
        if(firstQuestionIndex!=-1){
            name = name.substring(0,firstQuestionIndex);
        }
        return name;
    }

    @Nullable
    public static Map<String, String> parseParamsMapFromUrl(String rawStr) {
        Map<String,String >paramMap = new HashMap<>();
        try {
            int questionMarkIndex = rawStr.indexOf('?');
            if (questionMarkIndex == -1) {
                return null;
            }
            String paramString = rawStr.substring(questionMarkIndex + 1);
            String[] pairs = paramString.split("&");

            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                paramMap.put(key, value);
            }
        } catch (Exception e) {
            e.printStackTrace(); // Handle the exception appropriately in real code
        }
        return paramMap;
    }

    public static CloseableHttpResponse post(String url, Map<String,String>requestHeaderMap, String requestBodyType, byte[] requestBodyBytes) {
        CloseableHttpResponse httpResponse;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(url);
            if (requestHeaderMap != null) {
                for (String key : requestHeaderMap.keySet()) {
                    httpPost.setHeader(key, requestHeaderMap.get(key));
                }
            }

            switch (requestBodyType) {
                case "string" -> {
                    StringEntity entity = new StringEntity(new String(requestBodyBytes));
                    httpPost.setEntity(entity);
                }
                case "bytes" -> {
                    ByteArrayEntity entity = new ByteArrayEntity(requestBodyBytes);
                    httpPost.setEntity(entity);
                }

                default -> {
                    return null;
                }
            }


            try {
                httpResponse = httpClient.execute(httpPost);
            } catch (HttpHostConnectException e) {
                log.debug("Failed to connect " + url + ". Check the URL.");
                return null;
            }

            if (httpResponse == null) {
                log.debug("httpResponse == null.");
                return null;
            }

            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                log.debug("Post response status: {}.{}", httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase());
                return httpResponse;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return httpResponse;
    }

    @NotNull
    public static String getEntireUrl(HttpServletRequest request) {
        String url = request.getRequestURL().toString();
        String queryString = request.getQueryString();
        if (queryString != null) {
            url += "?" + queryString;
        }
        return url;
    }

//    public static String parseApiName(String url) {
//        int index = url.lastIndexOf('/');
//        if(index!=url.length()-1){
//            return url.substring(index);
//        }
//        return null;
//    }

    @Test
    public void test() {
        Map<String,String>map = new HashMap<>();
        map.put("p1","10");
        map.put("p2","200");

        String url = ApiUrl.makeUrl("http://120.1.1.1/","/tail/1/","/put",map);
        System.out.println(url);
    }
    public static boolean illegalUrl(String url){
        try {
            URI uri = new URI(url);
            uri.toURL();
            return false;
        }catch (Exception e){
            e.printStackTrace();
            return true;
        }
    }

    public static String makeUrlParamsString(Map<String, String> paramMap) {
        StringBuilder stringBuilder = new StringBuilder();
        if(paramMap !=null&& paramMap.size()>0){
            stringBuilder.append("?");
            for(String key: paramMap.keySet()){
                stringBuilder.append(key).append("=").append(paramMap.get(key)).append("&");
            }
            stringBuilder.deleteCharAt(stringBuilder.lastIndexOf("&"));
        }
        return stringBuilder.toString();
    }

}
