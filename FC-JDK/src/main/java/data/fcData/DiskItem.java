package data.fcData;

public class DiskItem extends FcObject{
    private Long since;
    private Long expire;
    private Long size;

    public static final String MAPPINGS = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"since\":{\"type\":\"long\"},\"expire\":{\"type\":\"long\"},\"size\":{\"type\":\"long\"}}}}";
    public DiskItem() {}

    public DiskItem(String did, Long since, Long expire, long size) {
        this.id = did;
        this.since = since;
        this.expire = expire;
        this.size = size;
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
