package feip.feipData;

import java.util.Map;
import java.util.List;
import static java.util.Map.entry;

public class NidData {

	private String op;
	private String name;
	private String desc;
	private String oid;
	private List<String> names;

	public enum Op {
		REGISTER("register"),
		UPDATE("update"),
		CLOSE("close"),
		RATE("rate");

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

	public static final Map<String, String[]> OP_FIELDS = Map.ofEntries(
		entry(Op.REGISTER.getValue(), new String[]{"name", "desc"}),
		entry(Op.UPDATE.getValue(), new String[]{"oid", "name", "desc"}),
		entry(Op.CLOSE.getValue(), new String[]{"oids"}),
		entry(Op.RATE.getValue(), new String[]{"oid"})
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

	public static NidData makeRegister(String name, String desc) {
		NidData data = new NidData();
		data.setOp(Op.REGISTER.name());
		data.setName(name);
		data.setDesc(desc);
		return data;
	}

	public static NidData makeUpdate(String oid, String name, String desc) {
		NidData data = new NidData();
		data.setOp(Op.UPDATE.name());
		data.setOid(oid);
		data.setName(name);
		data.setDesc(desc);
		return data;
	}

	public static NidData makeClose(List<String> names) {
		NidData data = new NidData();
		data.setOp(Op.CLOSE.name());
		data.setNames(names);
		return data;
	}

	public static NidData makeRate(String oid) {
		NidData data = new NidData();
		data.setOp(Op.RATE.name());
		data.setOid(oid);
		return data;
	}
}
