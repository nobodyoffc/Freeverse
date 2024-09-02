package diskClient;

import java.util.Date;

public class DiskDataInfo {
    private String did;
    private Date since;
    private Date expire;
    private long size;

    public static final String MAPPINGS = "{\"mappings\":{\"properties\":{\"did\":{\"type\":\"keyword\"},\"since\":{\"type\":\"date\",\"format\":\"epoch_millis||strict_date_optional_time\"},\"expire\":{\"type\":\"date\",\"format\":\"epoch_millis||strict_date_optional_time\"},\"size\":{\"type\":\"long\"}}}}";
    public DiskDataInfo() {}

    public DiskDataInfo(String did, Date since, Date expire, long size) {
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

    public Date getSince() {
        return since;
    }

    public void setSince(Date since) {
        this.since = since;
    }

    public Date getExpire() {
        return expire;
    }

    public void setExpire(Date expire) {
        this.expire = expire;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
