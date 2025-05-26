package data.feipData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import constants.FieldNames;

public class HomepageOpData {
	
	private String op;
	private List<String> homepages;
	
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

	// Existing getters and setters
	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
	}

	public List<String> getHomepages() {
		return homepages;
	}

	public void setHomepages(List<String> homepages) {
		this.homepages = homepages;
	}
}
