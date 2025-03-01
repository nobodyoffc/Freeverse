package feip.feipData;

import crypto.Hash;
import fcData.FcObject;

import java.util.HexFormat;

public class TokenHolder extends FcObject {
    private String fid;
    private String tokenId;
    private Double balance;
    private Long firstHeight;
    private Long lastHeight;

    public static String getTokenHolderId(String fid, String tokenId) {
        return HexFormat.of().formatHex(Hash.sha256((fid + tokenId).getBytes()));
    }

    @Override
    public String getId() {
        if(this.id==null)
            this.id = HexFormat.of().formatHex(Hash.sha256((fid + tokenId).getBytes()));
        return this.id;
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
