package feip.feipData;

import java.util.Map;

import constants.FieldNames;
import feip.FeipOp;

import java.util.List;
import static java.util.Map.entry;

public class NidOpData {

	private String op;
	private String name;
	private String desc;
	private String oid;
	private List<String> names;

	public enum Op {
		REGISTER(FeipOp.REGISTER),
		UPDATE(FeipOp.UPDATE),
		CLOSE(FeipOp.CLOSE),
		RATE(FeipOp.RATE);

        private final FeipOp feipOp;

        Op(FeipOp feipOp) {
            this.feipOp = feipOp;
        }

        public FeipOp getFeipOp() {
            return feipOp;
        }

        public static Op fromValue(String value) {
            for (Op op : values()) {
                if (op.getFeipOp().equals(value)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown op: " + value);
        }
	}

	public static final Map<String, String[]> OP_FIELDS = Map.ofEntries(
		entry(Op.REGISTER.name(), new String[]{FieldNames.NAME, FieldNames.DESC}),
		entry(Op.UPDATE.name(), new String[]{FieldNames.OID, FieldNames.NAME, FieldNames.DESC}),
		entry(Op.CLOSE.name(), new String[]{FieldNames.NAMES}),
		entry(Op.RATE.name(), new String[]{FieldNames.OID})
	);


	public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
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

	public String getOid() {
		return oid;
	}

	public void setOid(String oid) {
		this.oid = oid;
	}

	public static NidOpData makeRegister(String name, String desc) {
		NidOpData data = new NidOpData();
		data.setOp(Op.REGISTER.name());
		data.setName(name);
		data.setDesc(desc);
		return data;
	}

	public static NidOpData makeUpdate(String oid, String name, String desc) {
		NidOpData data = new NidOpData();
		data.setOp(Op.UPDATE.name());
		data.setOid(oid);
		data.setName(name);
		data.setDesc(desc);
		return data;
	}

	public static NidOpData makeClose(List<String> names) {
		NidOpData data = new NidOpData();
		data.setOp(Op.CLOSE.name());
		data.setNames(names);
		return data;
	}

	public static NidOpData makeRate(String oid) {
		NidOpData data = new NidOpData();
		data.setOp(Op.RATE.name());
		data.setOid(oid);
		return data;
	}
}
