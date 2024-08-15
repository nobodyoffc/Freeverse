package fcData;

public class DataEncrypt {
    private String alg;
    private EncryptMode mode;
    private String pubKey;
    private String msg;
    private String symKey;
    private String password;

    public EncryptMode getMode() {
        return mode;
    }

    public void setMode(EncryptMode mode) {
        this.mode = mode;
    }

    public String getSymKey() {
        return symKey;
    }

    public void setSymKey(String symKey) {
        this.symKey = symKey;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }


    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
