package apip.apipData;

import apip.ApipTools;

public class ApipUrl {
    private String url;
    private String urlHead;
    private String urlTail;
    private String method;

    public ApipUrl() {
    }

    public ApipUrl(String url) {
        this.url = url;
    }

    public ApipUrl(String urlHead, String urlTail) {
        this.urlHead = urlHead;
        this.urlTail = urlTail;
        this.url = mergeUrl(urlHead, urlTail);
    }

    public static String mergeUrl(String urlHead, String urlTail) {
        String slash = "/";
        if (urlHead.endsWith(slash) && urlTail.startsWith(slash)) urlHead = urlHead.substring(0, urlHead.length() - 1);
        else if (!urlHead.endsWith(slash) && !urlTail.startsWith(slash)) urlHead = urlHead + slash;
        return urlHead + urlTail;
    }

    public String getMethodFromUrl() {
        return ApipTools.getApiNameFromUrl(url);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
