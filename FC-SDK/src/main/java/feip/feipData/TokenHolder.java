package feip.feipData;

public class TokenHolder {
    private String id;
    private String fid;
    private String tokenId;
    private Double balance;
    private Long firstHeight;
    private Long lastHeight;

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

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public Long getFirstHeight() {
        return firstHeight;
    }

    public void setFirstHeight(Long firstHeight) {
        this.firstHeight = firstHeight;
    }

    public Long getLastHeight() {
        return lastHeight;
    }

    public void setLastHeight(Long lastHeight) {
        this.lastHeight = lastHeight;
    }
}
