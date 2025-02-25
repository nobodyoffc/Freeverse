package feip.feipData;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import constants.FieldNames;
import feip.FeipOp;

public class ContactData {

	private String op;
	private String contactId;
	private List<String> contactIds;
	private String alg;
	private String cipher;

	public enum Op implements FeipOp.FeipOpFields {
		ADD(FeipOp.ADD, new String[]{FieldNames.ALG, FieldNames.CIPHER}),
		DELETE(FeipOp.DELETE, new String[]{FieldNames.CONTACT_IDS}),
		RECOVER(FeipOp.RECOVER, new String[]{FieldNames.CONTACT_IDS});

		private final FeipOp feipOp;
		private final String[] requiredFields;

		Op(FeipOp feipOp, String[] requiredFields) {
			this.feipOp = feipOp;
			this.requiredFields = requiredFields;
		}

		@Override
		public String getValue() {
			return feipOp.getValue();
		}

		@Override
		public String[] getRequiredFields() {
			return requiredFields;
		}

		@Override
		public String toLowerCase() {
			return getValue().toLowerCase();
		}

		public static Op fromString(String text) {
			for (Op op : Op.values()) {
				if (op.getValue().equalsIgnoreCase(text)) {
					return op;
				}
			}
			throw new IllegalArgumentException("No constant with text " + text + " found");
		}
	}

	public static Map<String, String[]> getOpFields() {
		Map<String, String[]> fields = new HashMap<>();
		for (Op op : Op.values()) {
			fields.put(op.toLowerCase(), op.getRequiredFields());
		}
		return fields;
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
