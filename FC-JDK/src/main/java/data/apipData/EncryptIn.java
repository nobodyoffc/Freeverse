package data.apipData;

import core.crypto.EncryptType;
import data.fcData.AlgorithmId;

public class EncryptIn {
    private String fid;
    private EncryptType type;
    private String pubkey;
    private String symkey;
    private String password;
    private byte[] prikey;
    private String msg;
    private AlgorithmId alg;

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public String getSymkey() {
        return symkey;
    }

    public void setSymkey(String symkey) {
        this.symkey = symkey;
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

    public byte[] getPrikey() {
        return prikey;
    }

    public void setPrikey(byte[] prikey) {
        this.prikey = prikey;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }
}
