package data.feipData;

import java.util.Map;

import constants.FieldNames;
import constants.Values;

import java.util.List;
import static java.util.Map.entry;

public class NidOpData {

	private String op;
	private String name;
	private String desc;
	private String oid;
	private List<String> names;

	public enum Op {
		ADD(FeipOp.ADD),
		STOP(FeipOp.STOP),
		RECOVER(FeipOp.RECOVER);

        private final FeipOp feipOp;

        Op(FeipOp feipOp) {
            this.feipOp = feipOp;
        }

        public FeipOp getFeipOp() {
            return feipOp;
        }

        public static Op fromString(String text) {
            for (Op op : values()) {
                if (op.name().equalsIgnoreCase(text)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("No constant with text " + text + " found");
        }

        public String toLowerCase() {
            return feipOp.getValue().toLowerCase();
        }
	}

	public static final Map<String, String[]> OP_FIELDS = Map.ofEntries(
		entry(Op.ADD.toLowerCase(), new String[]{FieldNames.NAME, FieldNames.OID, Values.DESC}),
		entry(Op.STOP.toLowerCase(), new String[]{FieldNames.NAMES}),
		entry(Op.RECOVER.toLowerCase(), new String[]{FieldNames.NAMES})
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

	public static NidOpData makeAdd(String name, String oid, String desc) {
		NidOpData data = new NidOpData();
		data.setOp(Op.ADD.toLowerCase());
		data.setName(name);
		data.setOid(oid);
		data.setDesc(desc);
		return data;
	}

	public static NidOpData makeStop(List<String> names) {
		NidOpData data = new NidOpData();
		data.setOp(Op.STOP.toLowerCase());
		data.setNames(names);
		return data;
	}

	public static NidOpData makeRecover(List<String> names) {
		NidOpData data = new NidOpData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setNames(names);
		return data;
	}
}
