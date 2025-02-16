package server;

import feip.feipData.Service;

public class FreeApi {
    private String urlHead;
    private Boolean active;
    private String sid;
    private Service.ServiceType serviceType;

    public FreeApi() {
    }

    public FreeApi(String urlHead, Boolean active, Service.ServiceType serviceType) {
        this.active = active;
        this.urlHead = urlHead;
        this.serviceType = serviceType;
    }

    public String getUrlHead() {
        return urlHead;
    }

    public void setUrlHead(String urlHead) {
        this.urlHead = urlHead;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public Service.ServiceType getApiType() {
        return serviceType;
    }

    public void setApiType(Service.ServiceType serviceType) {
        this.serviceType = serviceType;
    }
}
