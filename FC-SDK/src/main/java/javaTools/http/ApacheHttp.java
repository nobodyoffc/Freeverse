package javaTools.http;

import constants.NetNames;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ApacheHttp {
    private static final Logger log = LoggerFactory.getLogger(ApacheHttp.class);

    public static Response request(Request request) {
        String requestUrl = request.getUrl();
        Map<String, String> headerMap = request.getHeaderMap();
        String requestBody = request.getBody();
        if (request.getMethod().equalsIgnoreCase(NetNames.POST))
            return apachePostRequest(requestUrl, headerMap, requestBody);
        if (request.getMethod().equalsIgnoreCase(NetNames.GET))
            return apacheGetRequest(requestUrl, headerMap);
        return null;
    }

    private static Response apacheGetRequest(String requestUrl, Map<String, String> headerMap) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(requestUrl);
            if (headerMap != null) {
                for (String key : headerMap.keySet()) {
                    httpGet.setHeader(key, headerMap.get(key));
                }
            }

            HttpResponse response = httpClient.execute(httpGet);

            Response response1 = makeResponseFromApache(response);
            if (response1 != null) return response1;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Response apachePostRequest(String requestUrl, Map<String, String> headerMap, String requestBody) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(requestUrl);
            if (headerMap != null) {
                for (String key : headerMap.keySet()) {
                    httpPost.setHeader(key, headerMap.get(key));
                }
            }

            StringEntity entity = new StringEntity(requestBody);
            httpPost.setEntity(entity);

            HttpResponse response = httpClient.execute(httpPost);
            Response response1 = makeResponseFromApache(response);
            if (response1 != null) return response1;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Response makeResponseFromApache(HttpResponse response) throws IOException {
        HttpEntity responseEntity = response.getEntity();

        Response response1 = new Response();
        Header[] headers = response.getAllHeaders();
        Map<String, String> headerMap = new HashMap<>();
        for (Header header : headers) headerMap.put(header.getName(), header.getValue());
        response1.setHeaderMap(headerMap);
        response1.setCode(String.valueOf(response.getStatusLine().getStatusCode()));
        response1.setMessage(response.getStatusLine().getReasonPhrase());

        if (responseEntity != null) {
            response1.setBody(EntityUtils.toString(responseEntity));
            return response1;
        }
        return null;
    }

    public static class Request {
        private String url;
        private String method;
        private String body;
        private Map<String, String> headerMap;

        public Request(String url, String method, String body, Map<String, String> headerMap) {
            this.url = url;
            this.method = method;
            this.body = body;
            this.headerMap = headerMap;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public Map<String, String> getHeaderMap() {
            return headerMap;
        }

        public void setHeaderMap(Map<String, String> headerMap) {
            this.headerMap = headerMap;
        }
    }

    public static class Response {
        private String code;
        private String message;
        private String body;
        private Map<String, String> headerMap;

        public boolean isBadResponse() {
            if (!code.equals("200")) {
                log.debug("Http response:" + code + " " + message);
                if (body == null) {
                    log.debug("ResponseBody is null.");
                }
                return true;
            } else return false;
        }

        public Map<String, String> getHeaderMap() {
            return headerMap;
        }

        public void setHeaderMap(Map<String, String> headerMap) {
            this.headerMap = headerMap;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}

