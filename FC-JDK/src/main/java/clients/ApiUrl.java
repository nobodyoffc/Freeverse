package clients;

import org.jetbrains.annotations.NotNull;
import utils.BytesUtils;
import utils.http.HttpUtils;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;
import static constants.Strings.VIA;

public class ApiUrl{
    private String url;
    private String urlHead;
    private String urlTail;

    //urlHead
    private String protocol;
    private String host;
    private Integer port;
    private String type;

    //urlTail
    private String sn;
    private String name;
    private String ver;

    //parameters
    private String paramStr;
    private Map<String,String> paramMap;

    public ApiUrl(){}
    public ApiUrl(String url){
        this.url = url;
        try {
            parseUrl();
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid URL: " + url, e);
        }
    }
    public ApiUrl(String urlHead, String urlTail){
        this.urlHead = formatUrlHead(urlHead);
        parseUrlHead(urlHead);

        this.urlTail = formatUrlTail(urlTail);
        parseUrlTail(urlTail);

        this.url = this.urlHead + this.urlTail;
    }

    public ApiUrl(String urlHead, String urlTail, @Nullable Map<String,String> paramMap, @Nullable Boolean ifSignUrl, @Nullable String via) {
        this.urlHead = formatUrlHead(urlHead);
        parseUrlHead(urlHead);

        this.urlTail = formatUrlTail(urlTail);
        parseUrlTail(urlTail);

        if(paramMap != null)
            makeParamStr(paramMap, via, ifSignUrl);
        makeUrl();
    }

    public ApiUrl(String urlHead, String sn, String ver, String name, @Nullable Map<String,String> paramMap, @Nullable Boolean isSignUrl, @Nullable String via) {
        this.urlHead = formatUrlHead(urlHead);
        parseUrlHead(urlHead);

        this.sn = sn;
        this.ver = ver;
        this.name = name;
        this.urlTail = makeUrlTail(sn, name, ver);

        if(paramMap != null)
            makeParamStr(paramMap, via, isSignUrl);
        makeUrl();
    }

    public static String formatUrlHead(String urlHead){
        if(urlHead.startsWith("/"))
            urlHead = urlHead.substring(1);
        if(!urlHead.endsWith("/"))
            urlHead = urlHead + "/";
        return urlHead;
    }

    public static String formatUrlTail(String urlTail){
        if(urlTail.startsWith("/"))
            urlTail = urlTail.substring(1);
        return urlTail;
    }

    public static String makeUrlTail(String sn, @NotNull String name, String ver){
        StringBuilder stringBuilder = new StringBuilder();

        if(sn != null){
            stringBuilder.append(sn);
            stringBuilder.append("/");
        }

        stringBuilder.append(name);

        if(ver != null){
            stringBuilder.append("/");
            stringBuilder.append(ver);
        }

        String urlTail = stringBuilder.toString();
        if("".equals(urlTail))
            urlTail = null;

        return urlTail;
    }

    public void makeUrl() {
        if(urlHead == null) return;

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(urlHead);

        if(urlTail == null)
            this.urlTail = makeUrlTail(this.sn, this.name, this.ver);
        if(urlTail != null)
            stringBuilder.append(urlTail);

        if(paramMap != null && paramStr == null)
            this.paramStr = HttpUtils.makeUrlParamsString(paramMap);
        if(paramStr != null)
            stringBuilder.append(paramStr);

        url = stringBuilder.toString();
    }

    private void parseUrl() throws MalformedURLException {
        if(url == null || url.isEmpty()) return;

        URL urlObj = new URL(url);

        // Get protocol, host, port
        this.protocol = urlObj.getProtocol();
        this.host = urlObj.getHost();
        this.port = urlObj.getPort();
        String path = urlObj.getPath();

        // Build urlHead (protocol://host:port/type/)
        StringBuilder headBuilder = new StringBuilder();
        headBuilder.append(protocol).append("://").append(host);
        if(port != -1) {
            headBuilder.append(":").append(port);
        }

        // Parse path to extract type, sn, name, ver
        // Path format: /type/[sn]/name[/ver]
        // Example: /APIP/sn2/cashSearch/v1
        if(path != null && !path.isEmpty() && !path.equals("/")) {
            String[] segments = path.substring(1).split("/");

            if(segments.length > 0) {
                // First segment is the type
                type = segments[0];
                headBuilder.append("/").append(type).append("/");

                // Parse remaining segments for urlTail (sn/name/ver)
                if(segments.length > 1) {
                    StringBuilder tailBuilder = new StringBuilder();
                    int index = 1;

                    // Check if next segment is sn number (starts with 'sn')
                    if(segments[index].startsWith("sn")) {
                        sn = segments[index];
                        tailBuilder.append(sn);
                        index++;
                    }

                    // Next segment is the name
                    if(index < segments.length) {
                        if(tailBuilder.length() > 0) tailBuilder.append("/");
                        name = segments[index];
                        tailBuilder.append(name);
                        index++;
                    }

                    // Last segment might be version (starts with 'v')
                    if(index < segments.length && segments[index].startsWith("v")) {
                        ver = segments[index];
                        tailBuilder.append("/").append(ver);
                    }

                    urlTail = tailBuilder.toString();
                    // Ensure urlTail doesn't start or end with '/'
                    if(urlTail.startsWith("/")) urlTail = urlTail.substring(1);
                    if(urlTail.endsWith("/")) urlTail = urlTail.substring(0, urlTail.length() - 1);
                }
            }
        } else {
            headBuilder.append("/");
        }

        urlHead = headBuilder.toString();

        // Ensure urlHead ends with '/'
        if(!urlHead.endsWith("/")) {
            urlHead = urlHead + "/";
        }

        // Parse query parameters
        String query = urlObj.getQuery();
        if(query != null && !query.isEmpty()) {
            paramStr = "?" + query;
            paramMap = new HashMap<>();
            String[] pairs = query.split("&");
            for(String pair : pairs) {
                int idx = pair.indexOf("=");
                if(idx > 0) {
                    String key = pair.substring(0, idx);
                    String value = idx < pair.length() - 1 ? pair.substring(idx + 1) : "";
                    paramMap.put(key, value);
                }
            }
        }
    }

    /**
     * Parse sn, name, and ver from urlTail string
     * @param urlTail URL tail in format: [sn/]name[/ver]
     * Examples:
     *   - "sn2/cashSearch/v1"
     *   - "cashSearch/v1"
     *   - "cashSearch"
     *   - "sn2/cashSearch"
     */
    private void parseUrlTail(String urlTail) {
        if(urlTail == null || urlTail.isEmpty()) return;

        // Remove leading/trailing slashes if present
        String urlTailClean = urlTail;
        if(urlTailClean.startsWith("/")) {
            urlTailClean = urlTailClean.substring(1);
        }
        if(urlTailClean.endsWith("/")) {
            urlTailClean = urlTailClean.substring(0, urlTailClean.length() - 1);
        }

        // Parse path segments: [sn/]name[/ver]
        // Example: sn2/cashSearch/v1
        String[] segments = urlTailClean.split("/");

        if(segments.length == 0) return;

        int index = 0;

        // Check if first segment is sn number (starts with 'sn')
        if(segments[index].startsWith("sn")) {
            this.sn = segments[index];
            index++;
        }

        // Next segment is the name
        if(index < segments.length && !segments[index].isEmpty()) {
            this.name = segments[index];
            index++;
        }

        // Last segment might be version (starts with 'v')
        if(index < segments.length && segments[index].startsWith("v")) {
            this.ver = segments[index];
        }

        // Reconstruct urlTail with parsed components
        StringBuilder tailBuilder = new StringBuilder();
        if(this.sn != null) {
            tailBuilder.append(this.sn);
        }
        if(this.name != null) {
            if(tailBuilder.length() > 0) tailBuilder.append("/");
            tailBuilder.append(this.name);
        }
        if(this.ver != null) {
            tailBuilder.append("/").append(this.ver);
        }

        this.urlTail = tailBuilder.toString();
    }

    /**
     * Parse protocol, host, port, and type from urlHead string
     * @param urlHead URL head in format: protocol://host[:port]/type/ or protocol://host[:port]/
     * Examples:
     *   - "https://api.example.com:8080/APIP/"
     *   - "http://localhost:8080/DISK/"
     *   - "https://api.example.com/"
     */
    public void parseUrlHead(String urlHead) {
        if(urlHead == null || urlHead.isEmpty()) return;

        // Remove trailing slash if present
        if(!urlHead.contains("://")) {
            if(urlHead.startsWith("127.0.0.1")||urlHead.startsWith("localhost"))
                urlHead = "http://" + urlHead;
            else urlHead = "https://" + urlHead;
        }

        String urlHeadClean = urlHead.endsWith("/") ? urlHead.substring(0, urlHead.length() - 1) : urlHead;

        URL urlObj;
        try {
            urlObj = new URL(urlHeadClean);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        // Get protocol, host, port
        this.protocol = urlObj.getProtocol();
        this.host = urlObj.getHost();
        this.port = urlObj.getPort();

        // Parse path to extract type
        String path = urlObj.getPath();
        if(path != null && !path.isEmpty() && !path.equals("/")) {
            // Remove leading slash and get first segment as type
            String[] segments = path.substring(1).split("/");
            if(segments.length > 0 && !segments[0].isEmpty()) {
                this.type = segments[0];
            }
        }

        // Reconstruct urlHead with parsed components
        StringBuilder headBuilder = new StringBuilder();
        headBuilder.append(protocol).append("://").append(host);
        if(port != -1) {
            headBuilder.append(":").append(port);
        }
        if(type != null) {
            headBuilder.append("/").append(type);
        }
        headBuilder.append("/");

        this.urlHead = headBuilder.toString();
    }


    public void makeParamStr(Map<String, String> paramMap, @Nullable String via, Boolean ifSignUrl) {
        if(paramMap != null)
            this.paramMap = paramMap;

        if(Boolean.TRUE.equals(ifSignUrl)){
            if(this.paramMap == null)
                this.paramMap = new HashMap<>();
            long time = System.currentTimeMillis();
            long nonce = BytesUtils.bytes4ToLongBE(BytesUtils.getRandomBytes(4));
            this.paramMap.put(TIME, String.valueOf(time));
            this.paramMap.put(NONCE, String.valueOf(nonce));
            if(via != null)
                this.paramMap.put(VIA, via);
        }

        if(this.paramMap != null)
            this.paramStr = HttpUtils.makeUrlParamsString(this.paramMap);
    }

    /**
     * Parse parameter string into parameter map using URLDecoder for proper URL decoding
     * @param paramStr Parameter string in format: ?key1=value1&key2=value2 or key1=value1&key2=value2
     * Examples:
     *   - "?time=1234567890&nonce=12345"
     *   - "time=1234567890&nonce=12345&via=client1"
     */
    public void parseParams(String paramStr) {
        if(paramStr == null || paramStr.isEmpty()) return;

        // Remove leading '?' if present
        String paramStrClean = paramStr.startsWith("?") ? paramStr.substring(1) : paramStr;

        if(paramStrClean.isEmpty()) return;

        // Initialize paramMap if null
        if(this.paramMap == null) {
            this.paramMap = new HashMap<>();
        }

        // Parse key-value pairs with proper URL decoding
        String[] pairs = paramStrClean.split("&");
        for(String pair : pairs) {
            int idx = pair.indexOf("=");
            if(idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = idx < pair.length() - 1 ?
                    URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
                this.paramMap.put(key, value);
            }
        }

        // Set paramStr with leading '?'
        this.paramStr = "?" + paramStrClean;
    }

    public void makeUrlTail(){
        this.urlTail = makeUrlTail(this.sn, this.name, this.ver);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

    public String getVer() {
        return ver;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrlHead() {
        return urlHead;
    }

    public void setUrlHead(String urlHead) {
        this.urlHead = formatUrlHead(urlHead);
    }

    public String getUrlTail() {
        return urlTail;
    }

    public void setUrlTail(String urlTail) {
        this.urlTail = formatUrlTail(urlTail);
    }

    public Map<String, String> getParamMap() {
        return paramMap;
    }

    public void setParamMap(Map<String, String> paramMap) {
        this.paramMap = paramMap;
    }

    public String getParamStr() {
        return paramStr;
    }

    public void setParamStr(String paramStr) {
        this.paramStr = paramStr;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }
}
