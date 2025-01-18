package swap;

public class SwapPriceData {
    private String id;
    private String sid;
    private String gTick;
    private String mTick;
    private double gAmt;
    private double mAmt;
    private double price;
    private long time;
    public static final String  swapPriceMappingJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"sid\":{\"type\":\"keyword\"},\"gTick\":{\"type\":\"keyword\"},\"mTick\":{\"type\":\"keyword\"},\"gAmt\":{\"type\":\"double\"},\"mAmt\":{\"type\":\"double\"},\"price\":{\"type\":\"double\"},\"time\":{\"type\":\"long\"}}}}";

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getgTick() {
        return gTick;
    }

    public void setgTick(String gTick) {
        this.gTick = gTick;
    }

    public String getmTick() {
        return mTick;
    }

    public void setmTick(String mTick) {
        this.mTick = mTick;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public double getgAmt() {
        return gAmt;
    }

    public void setgAmt(double gAmt) {
        this.gAmt = gAmt;
    }

    public double getmAmt() {
        return mAmt;
    }

    public void setmAmt(double mAmt) {
        this.mAmt = mAmt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
