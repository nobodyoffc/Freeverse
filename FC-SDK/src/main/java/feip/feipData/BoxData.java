package feip.feipData;

public class BoxData {

    private String bid;
    private String op;
    private String name;
    private String desc;
    private Object contain;
    private String cipher;
    private String alg;

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }

    public void setContain(Object contain) {
        this.contain = contain;
    }

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public String getBid() {
        return bid;
    }

    public void setBid(String bid) {
        this.bid = bid;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public Object getContain() {
        return contain;
    }

    public void setContain(String contain) {
        this.contain = contain;
    }
}
