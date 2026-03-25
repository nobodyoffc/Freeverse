package fapi.components.disk;

/**
 * Configuration for a remote FAPI DISK service to synchronize data from.
 */
public class DiskSyncSource {
    private String sid;
    private String url;
    private boolean enabled = true;

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

    @Override
    public String toString() {
        return "DiskSyncSource{sid='" + sid + "', url='" + url + "', enabled=" + enabled + '}';
    }
}
