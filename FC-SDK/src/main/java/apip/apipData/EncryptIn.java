package apip.apipData;

import crypto.EncryptType;
import fcData.AlgorithmId;

public class EncryptIn {
    private String fid;
    private EncryptType type;
    private String pubKey;
    private String symKey;
    private String password;
    private byte[] priKey;
    private String msg;
    private AlgorithmId alg;

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }

    public String getSymKey() {
        return symKey;
    }

    public void setSymKey(String symKey) {
        this.symKey = symKey;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public AlgorithmId getAlg() {
        return alg;
    }

    public void setAlg(AlgorithmId alg) {
        this.alg = alg;
    }
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public EncryptType getType() {
        return type;
    }

    public void setType(EncryptType type) {
        this.type = type;
    }

    public byte[] getPriKey() {
        return priKey;
    }

    public void setPriKey(byte[] priKey) {
        this.priKey = priKey;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }
}
