package clients.fcspClient;

import java.util.Date;

public class DiskItem {
    private String did;
    private Long since;
    private Long expire;
    private Long size;

    public static final String MAPPINGS = "{\"mappings\":{\"properties\":{\"did\":{\"type\":\"keyword\"},\"since\":{\"type\":\"long\"},\"expire\":{\"type\":\"long\"},\"size\":{\"type\":\"long\"}}}}";
    public DiskItem() {}

    public DiskItem(String did, Long since, Long expire, long size) {
        this.did = did;
        this.since = since;
        this.expire = expire;
        this.size = size;
    }



    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public Long getSince() {
        return since;
    }

    public void setSince(Long since) {
        this.since = since;
    }

    public Long getExpire() {
        return expire;
    }

    public void setExpire(Long expire) {
        this.expire = expire;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }
}
