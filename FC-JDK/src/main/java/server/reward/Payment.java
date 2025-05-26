package server.reward;

public class Payment {
    private String fid;
    private Integer share;// A share of 1234 means 0.1234 or 12.34% of the total.
    private Long fixed;
    private Long amount;
    public Long getFixed() {
        return fixed;
    }

    public void setFixed(Long fixed) {
        this.fixed = fixed;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public Integer getShare() {
        return share;
    }

    public void setShare(Integer share) {
        this.share = share;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }
}
