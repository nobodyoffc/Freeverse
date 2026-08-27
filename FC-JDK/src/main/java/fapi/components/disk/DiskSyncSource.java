package fapi.components.disk;

/**
 * Configuration for a remote FAPI DISK service to synchronize data from.
 */
public class DiskSyncSource {
    private String sid;
    private String url;
    private boolean enabled = true;
    /**
     * Optional recharge amount in FCH for this source. When the balance falls
     * below minDealerBalance, this amount is paid instead of the client's
     * default purchase amount (the deficit still sets the lower bound).
     * Null or <= 0 means use the default.
     */
    private Double rechargeFch;

    public DiskSyncSource() {}

    public DiskSyncSource(String sid, String url, boolean enabled) {
        this.sid = sid;
        this.url = url;
        this.enabled = enabled;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Double getRechargeFch() {
        return rechargeFch;
    }

    public void setRechargeFch(Double rechargeFch) {
        this.rechargeFch = rechargeFch;
    }

    @Override
    public String toString() {
        return "DiskSyncSource{sid='" + sid + "', url='" + url + "', enabled=" + enabled
                + (rechargeFch != null ? ", rechargeFch=" + rechargeFch : "") + '}';
    }
}
