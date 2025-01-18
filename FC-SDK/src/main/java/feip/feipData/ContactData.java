package feip.feipData;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
public class ContactData {

	private String op;
	private String contactId;
	private List<String> contactIds;
	private String alg;
	private String cipher;

	public enum Op {
		ADD("add"),
		DELETE("delete"),
		RECOVER("recover");

		private final String value;

		Op(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public static Op fromString(String text) {
			for (Op op : Op.values()) {
				if (op.value.equalsIgnoreCase(text)) {
					return op;
				}
			}
			throw new IllegalArgumentException("No constant with text " + text + " found");
		}

		public String toLowerCase() {
			return this.value.toLowerCase();
		}
	}

	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();
	
	static {
		OP_FIELDS.put(Op.ADD.getValue(), new String[]{"alg", "cipher"});
		OP_FIELDS.put(Op.DELETE.getValue(), new String[]{"contactIds"});
		OP_FIELDS.put(Op.RECOVER.getValue(), new String[]{"contactIds"});
	}

	public String getCipher() {
		return cipher;
	}

	public void setCipher(String cipher) {
		this.cipher = cipher;
	}
	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
	}
	public String getContactId() {
		return contactId;
	}
	public void setContactId(String contactId) {
		this.contactId = contactId;
	}

	public String getAlg() {
		return alg;
	}

	public void setAlg(String alg) {
		this.alg = alg;
	}
	public List<String> getContactIds() {
		return contactIds;
	}
	public void setContactIds(List<String> contactIds) {
		this.contactIds = contactIds;
	}

	// Factory method for ADD operation
	public static ContactData makeAdd(String alg, String cipher) {
		ContactData data = new ContactData();
		data.setOp(Op.ADD.getValue());
		data.setAlg(alg);
		data.setCipher(cipher);
		return data;
	}

	// Factory method for DELETE operation
	public static ContactData makeDelete(List<String> contactIds) {
		ContactData data = new ContactData();
		data.setOp(Op.DELETE.getValue());
		data.setContactIds(contactIds);
		return data;
	}

	// Factory method for RECOVER operation
	public static ContactData makeRecover(List<String> contactIds) {
		ContactData data = new ContactData();
		data.setOp(Op.RECOVER.getValue());
		data.setContactIds(contactIds);
		return data;
	}
}
