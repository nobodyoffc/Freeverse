package feip.feipData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BoxData {

    private String bid;
    private String op;
    private String name;
    private String desc;
    private Object contain;
    private String cipher;
    private String alg;
    private List<String> bids;

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

    public List<String> getBids() {
        return bids;
    }

    public void setBids(List<String> bids) {
        this.bids = bids;
    }

    public enum Op {
        CREATE("create"),
        UPDATE("update"),
        DROP("drop"),
        RECOVER("recover");

        private final String value;

        Op(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Op fromValue(String value) {
            for (Op op : values()) {
                if (op.value.equals(value)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown op: " + value);
        }
    }

    public static final Map<String, String[]> OP_FIELDS = new HashMap<>();
    static {
        // For create: name is required, bid must be null
        OP_FIELDS.put(Op.CREATE.getValue(), new String[]{"name", "desc", "contain", "cipher", "alg"});
        // For update: both bid and name are required
        OP_FIELDS.put(Op.UPDATE.getValue(), new String[]{"bid", "name", "desc", "contain", "cipher", "alg"});
        // For drop and recover: bids is required
        OP_FIELDS.put(Op.DROP.getValue(), new String[]{"bids"});
        OP_FIELDS.put(Op.RECOVER.getValue(), new String[]{"bids"});
    }

    // Update factory method names to match the actual operations
    public static BoxData makeCreate(String name, String desc, Object contain, String cipher, String alg) {
        BoxData data = new BoxData();
        data.setOp(Op.CREATE.getValue());
        data.setName(name);
        data.setDesc(desc);
        data.setContain(contain);
        data.setCipher(cipher);
        data.setAlg(alg);
        return data;
    }

    public static BoxData makeUpdate(String bid, String name, String desc, Object contain, String cipher, String alg) {
        BoxData data = new BoxData();
        data.setBid(bid);
        data.setOp(Op.UPDATE.getValue());
        data.setName(name);
        data.setDesc(desc);
        data.setContain(contain);
        data.setCipher(cipher);
        data.setAlg(alg);
        return data;
    }

    public static BoxData makeDrop(List<String> bids) {
        BoxData data = new BoxData();
        data.setBids(bids);
        data.setOp(Op.DROP.getValue());
        return data;
    }

    public static BoxData makeRecover(List<String> bids) {
        BoxData data = new BoxData();
        data.setBids(bids);
        data.setOp(Op.RECOVER.getValue());
        return data;
    }
}
