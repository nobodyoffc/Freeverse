package clients;

import javaTools.BytesTools;
import javaTools.http.HttpTools;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;
import static constants.Strings.VIA;

public class ApiUrl{
    private String url;
    private String urlHead;
    private String urlTail;
    private String urlTailPath;
    private String type;
    private String sn;
    private String ver;
    private String api;
    private String paramStr;
    private Map<String,String> paramMap;

    public ApiUrl(){}
    public ApiUrl(String url){
        this.url = url;
        this.api = HttpTools.getApiNameFromUrl(url);
    }
    public ApiUrl(String urlHead,String urlTail){
        this.urlHead = formatUrlPath(urlHead);
        if(urlTail!=null) {
            if(urlTail.startsWith("/"))urlTail=urlTail.substring(1);
            this.urlTail=urlTail;
            parseUrlTail();
        }
        url=this.urlHead+this.urlTail;
    }

    public ApiUrl(String urlHead,String urlTail,@Nullable Map<String,String>paramMap,@Nullable Boolean ifSignUrl,@Nullable String via) {
        this.urlHead = urlHead;
        if(urlTail!=null){
            this.urlTail=urlTail;
            parseUrlTail();
            parseUrlTailPath();
        }
        if(paramMap!=null)
            makeParamStr(paramMap, via, ifSignUrl);
        makeUrl();
    }

    public ApiUrl(String urlHead, String urlTailPath, String api, @Nullable Map<String,String>paramMap, @Nullable Boolean ifSignUrl, @Nullable String via) {
        this.urlHead = formatUrlPath(urlHead);
        this.api = api;
        this.urlTailPath = formatUrlPath(urlTailPath);
        parseUrlTailPath();
        makeUrlTail();
        if(paramMap!=null)
            makeParamStr(paramMap, via, ifSignUrl);
        makeUrl();
    }
    public static String formatUrlPath(String urlPath){
        if(urlPath.startsWith("/"))urlPath=urlPath.substring(1);
        if(!urlPath.endsWith("/"))urlPath = urlPath+"/";
        return urlPath;
    }
    public ApiUrl(String urlHead, String sn, String ver, String api, @Nullable Map<String,String>paramMap, @Nullable Boolean isSignUrl, @Nullable String via) {
        this.urlHead = formatUrlPath(urlHead);
        this.sn = sn;
        this.ver = ver;
        this.api = api;
        makeUrlTail();
        makeParamStr(paramMap, via, isSignUrl);
        makeUrl();
    }

    public static String makeUrl(String urlHead, String type,String sn, String ver, String apiName, Map<String,String> paramMap){
        String urlTailPath =makeUrlTailPath(sn,ver);
        return makeUrl(urlHead,urlTailPath,apiName,paramMap);
    }

    public static String makeUrlTailPath(String sn, String ver){

        StringBuilder stringBuilder = new StringBuilder();
        if(sn!=null){
            stringBuilder.append(sn);
            stringBuilder.append("/");
        }
        if(ver!=null){
            stringBuilder.append(ver);
            stringBuilder.append("/");
        }
        String urlTailPath=stringBuilder.toString();
        if("".equals(urlTailPath))urlTailPath=null;

        return urlTailPath;
    }

    public static String makeUrlTailPathV1(String type,String sn, String ver){

        StringBuilder stringBuilder = new StringBuilder();
        if(type!=null){
            stringBuilder.append(type);
            stringBuilder.append("/");
        }
        if(sn!=null){
            stringBuilder.append(sn);
            stringBuilder.append("/");
        }
        if(ver!=null){
            stringBuilder.append(ver);
            stringBuilder.append("/");
        }
        String urlTailPath=stringBuilder.toString();
        if("".equals(urlTailPath))urlTailPath=null;

        return urlTailPath;
    }

    public static String makeUrl(String urlHead, String urlTailPath, String apiName, Map<String,String> paramMap){
        if(!urlHead.endsWith("/"))urlHead=urlHead+"/";
        if(urlTailPath.equals("/"))urlTailPath=null;
        if(urlTailPath!=null && urlTailPath.startsWith("/"))
            urlTailPath=urlTailPath.substring(1);
        if(urlTailPath!=null && !urlTailPath.endsWith("/"))
            urlTailPath=urlTailPath+"/";
        if(apiName.startsWith("/"))
            apiName=apiName.substring(1);
        StringBuilder stringBuilder;
        if(urlTailPath==null)stringBuilder= new StringBuilder(urlHead + apiName);
        else stringBuilder= new StringBuilder(urlHead + urlTailPath + apiName);
        String paramsString = HttpTools.makeUrlParamsString(paramMap);
        stringBuilder.append(paramsString);
        return stringBuilder.toString();
    }

    public static String makeUrl(String urlHead, String urlTail){
        if(!urlHead.endsWith("/"))urlHead=urlHead+"/";
        if(urlTail.startsWith("/"))urlTail=urlTail.substring(1);
        return urlHead+urlTail;
    }

    public void makeUrl(String urlHead,String urlTail,@Nullable Map<String,String>paramMap,@Nullable Boolean ifSignUrl,@Nullable String via) {
        this.urlHead = urlHead;
        this.urlTail=urlTail;
        parseUrlTail();
        parseUrlTailPath();
        if(paramMap!=null)
            makeParamStr(paramMap, via, ifSignUrl);
        makeUrl(urlHead,urlTailPath, api);
        if(paramStr !=null) url = url+ paramStr;
    }
    public void makeUrl(String urlHead,String urlTailPath,String apiName,@Nullable Map<String,String>paramMap,@Nullable Boolean ifSignUrl,@Nullable String via) {
        this.urlHead = urlHead;
        this.api = apiName;
        this.urlTailPath = urlTailPath;
        parseUrlTailPath();
        makeUrlTail();
        if(paramMap!=null)
            makeParamStr(paramMap, via, ifSignUrl);
        makeUrl();
    }
    public void makeUrl(String urlHead,String type,String sn,String version,String apiName,@Nullable Map<String,String>paramMap,@Nullable Boolean ifSignUrl,@Nullable String via) {
        this.urlHead = urlHead;
        this.type=type;
        this.sn =sn;
        this.ver = version;
        this.api = apiName;
        makeUrlTail();
        if(paramMap!=null)
            makeParamStr(paramMap, via, ifSignUrl);
        makeUrl();
    }
    
    public void makeUrl() {
        StringBuilder stringBuilder = new StringBuilder();
        if(urlHead==null)return;
        if(!urlHead.endsWith("/"))urlHead=urlHead+"/";
        stringBuilder.append(urlHead);
        if(urlTailPath==null)makeUrlTailPath();
        if(urlTail==null)makeUrlTail();
        if(urlTail!=null)
            stringBuilder.append(urlTail);

        if(paramMap!=null&&paramStr==null)
            HttpTools.makeUrlParamsString(paramMap);
        if(paramStr !=null)
            stringBuilder.append(paramStr);
        url = stringBuilder.toString();
    }

    public static String makeUrl(String urlHead, String urlTailPath, String apiName) {
        String url;
        if(urlTailPath==null || urlTailPath.equals("")||urlTailPath.equals("/")){
            if(urlHead.endsWith("/"))url = urlHead+apiName;
            else url = urlHead+"/"+apiName;
            return url;
        }
        if(urlHead.endsWith("/")&& urlTailPath.startsWith("/"))
            urlHead=urlHead.substring(0,urlHead.length()-1);
        if(!urlTailPath.endsWith("/"))
            urlTailPath=urlTailPath+"/";
        if(!urlHead.endsWith("/") && !urlTailPath.startsWith("/"))
            urlHead+="/";
        url= urlHead + urlTailPath + apiName;
        return url;
    }
    
    private void parseUrlTailPath() {
        if(urlTailPath==null)return;
        urlTailPath=formatUrlPath(urlTailPath);
        if(urlTailPath.endsWith("/")) urlTailPath = urlTailPath.substring(0, urlTailPath.length()-1);
        if(urlTailPath.contains("/")) {
            this.ver = urlTailPath.substring(urlTailPath.lastIndexOf("/")+1);
            this.sn = urlTailPath.substring(0, urlTailPath.lastIndexOf("/"));
        }
        else this.ver = urlTailPath;
    }
    private void parseUrlTail() {
        if(urlTail==null)return;
        if(urlTail.contains("/")){
            this.api = urlTail.substring(urlTail.lastIndexOf("/")+1);
            this.urlTailPath = urlTail.substring(0, urlTail.lastIndexOf("/"));
            parseUrlTailPath();
        }else {
            this.api =urlTail;
            this.urlTailPath="";
        }
    }
    private void parseParamStr() {
        String rawStr;
        if(paramStr!=null)rawStr=paramStr;
        else if(url!=null)rawStr =url;
        else if(urlTail!=null)rawStr =urlTail;
        else return;
        paramMap = HttpTools.parseParamsMapFromUrl(rawStr);
    }

    public void makeParamStr(Map<String, String> paramMap, @Nullable String via, Boolean ifSignUrl) {
        if(paramMap !=null)
            this.paramMap = paramMap;
        if(Boolean.TRUE.equals(ifSignUrl)){
            if(this.paramMap ==null)
                this.paramMap = new HashMap<>();
            long time = System.currentTimeMillis();
            long nonce = BytesTools.bytes4ToLongBE(BytesTools.getRandomBytes(4));
            this.paramMap.put(TIME, String.valueOf(time));
            this.paramMap.put(NONCE, String.valueOf(nonce));
            if(via !=null) this.paramMap.put(VIA, via);
        }
        if(this.paramMap !=null) this.paramStr = HttpTools.makeUrlParamsString(this.paramMap);
    }

    public void makeUrlTail(){
        StringBuilder stringBuilder = new StringBuilder();
        if(urlTailPath==null){
            makeUrlTailPath();
        }
        if(urlTailPath!=null){
            urlTailPath=formatUrlPath(urlTailPath);
            stringBuilder.append(urlTailPath);
        }
        stringBuilder.append(api);
        urlTail=stringBuilder.toString();
    }

    public void makeUrlTailPath(){
        StringBuilder stringBuilder = new StringBuilder();
//        if(type!=null){
//            stringBuilder.append(type);
//            stringBuilder.append("/");
//        }
        if(sn!=null){
            stringBuilder.append(sn);
            stringBuilder.append("/");
        }
        if(ver!=null){
            stringBuilder.append(ver);
            stringBuilder.append("/");
        }
        urlTailPath=stringBuilder.toString();
        if("".equals(urlTailPath))urlTailPath=null;
    }

//    public void makeUrlForSign(String urlHead, String protocol, String version, String apiName, String via, Map<String, String> urlParamMap) {
//        this.protocol=protocol;
//        this.version=version;
//        makeUrlTailPath();
//        if(via!=null) {
//            if (urlParamMap == null) urlParamMap = makeUrlParamMap(via);
//            else urlParamMap.put(VIA, via);
//        }
//        url = HttpTools.makeUrl(urlHead, urlTailPath, apiName, urlParamMap);
//    }


    public static Map<String, String> makeUrlCheckParamMap(String via) {
        Map<String,String> urlParamMap = new HashMap<>();
        long time = System.currentTimeMillis();
        long nonce = BytesTools.bytes4ToLongBE(BytesTools.getRandomBytes(4));

        urlParamMap.put(TIME, String.valueOf(time));
        urlParamMap.put(NONCE, String.valueOf(nonce));
        if(via !=null)urlParamMap.put(VIA, via);
        return urlParamMap;
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

    public String getUrlTailPath() {
        return urlTailPath;
    }

    public void setUrlTailPath(String urlTailPath) {
        this.urlTailPath = urlTailPath;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public String getUrlHead() {
        return urlHead;
    }

    public void setUrlHead(String urlHead) {
        this.urlHead = urlHead;
    }

    public String getUrlTail() {
        return urlTail;
    }

    public void setUrlTail(String urlTail) {
        this.urlTail = urlTail;
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
}
