package fcData;

public class DataDecrypt {
    private String alg;
    private EncryptMode mode;
    private String pubKey;
    private String iv;
    private String cipher;
    private String sum;
    private String symKey;
    private String password;

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

    public EncryptMode getMode() {
        return mode;
    }

    public void setMode(EncryptMode mode) {
        this.mode = mode;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public String getSum() {
        return sum;
    }

    public void setSum(String sum) {
        this.sum = sum;
    }
}
