package data.apipData;

import static server.ApipApiNames.apiList;
import static server.ApipApiNames.freeApiList;

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

    public static String getApiNameFromUrl(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex != url.length() - 1) {
            String name = url.substring(lastSlashIndex + 1);
            if (apiList.contains(name) || freeApiList.contains(name)) {
                return name;
            }
            return "";
        } else {
            return "";  // Return empty string if '/' is the last character or not found
        }

    }

    public String getMethodFromUrl() {
        return getApiNameFromUrl(url);
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
