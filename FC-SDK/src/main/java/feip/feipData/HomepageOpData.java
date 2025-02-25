package feip.feipData;

import feip.FeipOp;
import java.util.HashMap;
import java.util.Map;

import constants.FieldNames;

public class HomepageOpData {
	
	private String op;
	private String[] homepages;
	
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
		OP_FIELDS.put(Op.REGISTER.toLowerCase(), new String[]{FieldNames.HOMEPAGES});
		OP_FIELDS.put(Op.UNREGISTER.toLowerCase(), new String[]{FieldNames.HOMEPAGES});
	}
	
	// Factory method for REGISTER operation
	public static HomepageOpData makeRegister(String[] homepages) {
		HomepageOpData data = new HomepageOpData();
		data.setOp(Op.REGISTER.toLowerCase());
		data.setHomepages(homepages);
		return data;
	}

	// Factory method for UNREGISTER operation
	public static HomepageOpData makeUnregister(String[] homepages) {
		HomepageOpData data = new HomepageOpData();
		data.setOp(Op.UNREGISTER.toLowerCase());
		data.setHomepages(homepages);
		return data;
	}

	// Existing getters and setters
	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
	}
	public String[] getHomepages() {
		return homepages;
	}
	public void setHomepages(String[] url) {
		this.homepages = url;
	}
}
