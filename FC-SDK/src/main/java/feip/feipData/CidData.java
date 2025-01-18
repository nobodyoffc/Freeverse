package feip.feipData;

import feip.FeipOp;
import java.util.HashMap;
import java.util.Map;

public class CidData {
	
	private String op;
	private String name;
	
	public enum Op {
		REGISTER(FeipOp.REGISTER),
		UNREGISTER(FeipOp.UNREGISTER);

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
			throw new IllegalArgumentException("No constant with text " + text + " found");
		}

		public String toLowerCase() {
			return this.name().toLowerCase();
		}
	}

	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();

	static {
		OP_FIELDS.put(Op.REGISTER.toLowerCase(), new String[]{"name"});
		OP_FIELDS.put(Op.UNREGISTER.toLowerCase(), new String[]{});
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

	// Factory method for REGISTER operation
	public static CidData makeRegister(String name) {
		CidData data = new CidData();
		data.setOp(Op.REGISTER.toLowerCase());
		data.setName(name);
		return data;
	}

	// Factory method for UNREGISTER operation
	public static CidData makeUnregister() {
		CidData data = new CidData();
		data.setOp(Op.UNREGISTER.toLowerCase());
		return data;
	}
}
