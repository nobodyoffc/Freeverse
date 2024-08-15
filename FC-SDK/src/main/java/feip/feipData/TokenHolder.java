package feip.feipData;

public class TokenHolder {
    private String id;
    private String fid;
    private String tokenId;
    private double balance;
    private long firstHeight;
    private long lastHeight;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public long getFirstHeight() {
        return firstHeight;
    }

    public void setFirstHeight(long firstHeight) {
        this.firstHeight = firstHeight;
    }

    public long getLastHeight() {
        return lastHeight;
    }

    public void setLastHeight(long lastHeight) {
        this.lastHeight = lastHeight;
    }
}
