package feip.feipData;

import java.util.HashMap;
import java.util.Map;

import constants.FieldNames;
import feip.FeipOp;

import java.util.List;
public class ContactOpData {

	private String op;
	private String contactId;
	private List<String> contactIds;
	private String alg;
	private String cipher;

	public enum Op {
		ADD(FeipOp.ADD),
		DELETE(FeipOp.DELETE),
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
			throw new IllegalArgumentException("No constant with text " + text + " found");
		}

		public String toLowerCase() {
			return feipOp.getValue().toLowerCase();
		}
	}

	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();
	
	static {
		OP_FIELDS.put(Op.ADD.toLowerCase(), new String[]{FieldNames.ALG, FieldNames.CIPHER});
		OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{FieldNames.CONTACT_IDS});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.CONTACT_IDS});
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
	public static ContactOpData makeAdd(String alg, String cipher) {
		ContactOpData data = new ContactOpData();
		data.setOp(Op.ADD.toLowerCase());
		data.setAlg(alg);
		data.setCipher(cipher);
		return data;
	}

	// Factory method for DELETE operation
	public static ContactOpData makeDelete(List<String> contactIds) {
		ContactOpData data = new ContactOpData();
		data.setOp(Op.DELETE.toLowerCase());
		data.setContactIds(contactIds);
		return data;
	}

	// Factory method for RECOVER operation
	public static ContactOpData makeRecover(List<String> contactIds) {
		ContactOpData data = new ContactOpData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setContactIds(contactIds);
		return data;
	}
}
