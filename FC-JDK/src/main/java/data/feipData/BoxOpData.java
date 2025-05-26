package data.feipData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import constants.FieldNames;
import constants.Values;

public class BoxOpData{

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
        CREATE(FeipOp.CREATE),
        UPDATE(FeipOp.UPDATE),
        DROP(FeipOp.DROP),
        RECOVER(FeipOp.RECOVER);

		private final FeipOp feipOp;

		Op(FeipOp feipOp) {
			this.feipOp = feipOp;
		}

		public FeipOp getFeipOp() {
			return feipOp;
		}

        public static Op fromString(String text) {
            for (Op op : Op.values()) {
                if (op.name().equalsIgnoreCase(text)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown op: " + text);
        }

        public String toLowerCase() {
            return feipOp.getValue().toLowerCase();
        }
    }

    public static final Map<String, String[]> OP_FIELDS = new HashMap<>();
    static {
        // For create: name is required, bid must be null
        OP_FIELDS.put(Op.CREATE.toLowerCase(), new String[]{FieldNames.NAME, Values.DESC, FieldNames.CONTAIN, FieldNames.CIPHER, FieldNames.ALG});
        // For update: both bid and name are required
        OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.BID, FieldNames.NAME, Values.DESC, FieldNames.CONTAIN, FieldNames.CIPHER, FieldNames.ALG});
        // For drop and recover: bids is required
        OP_FIELDS.put(Op.DROP.toLowerCase(), new String[]{FieldNames.BIDS});
        OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.BIDS});
    }

    // Update factory method names to match the actual operations
    public static BoxOpData makeCreate(String name, String desc, Object contain, String cipher, String alg) {
        BoxOpData data = new BoxOpData();
        data.setOp(Op.CREATE.toLowerCase());
        data.setName(name);
        data.setDesc(desc);
        data.setContain(contain);
        data.setCipher(cipher);
        data.setAlg(alg);
        return data;
    }

    public static BoxOpData makeUpdate(String bid, String name, String desc, Object contain, String cipher, String alg) {
        BoxOpData data = new BoxOpData();
        data.setBid(bid);
        data.setOp(Op.UPDATE.toLowerCase());
        data.setName(name);
        data.setDesc(desc);
        data.setContain(contain);
        data.setCipher(cipher);
        data.setAlg(alg);
        return data;
    }

    public static BoxOpData makeDrop(List<String> bids) {
        BoxOpData data = new BoxOpData();
        data.setBids(bids);
        data.setOp(Op.DROP.toLowerCase());
        return data;
    }

    public static BoxOpData makeRecover(List<String> bids) {
        BoxOpData data = new BoxOpData();
        data.setBids(bids);
        data.setOp(Op.RECOVER.toLowerCase());
        return data;
    }
}
