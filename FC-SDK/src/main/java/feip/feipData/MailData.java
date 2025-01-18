package feip.feipData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MailData {

	private String op;
	private String mailId;
    private String alg;
	private String msg;
	private String cipher;
	private String cipherSend;
	private String cipherReci;
	private String textId;
	private List<String> mailIds;

	public enum Op {
		SEND("send"),
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
		OP_FIELDS.put(Op.SEND.toLowerCase(), new String[]{"alg", "cipher", "cipherSend", "cipherReci", "textId"});
		OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{"mailIds"});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{"mailIds"});
	}

	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
	}
	public String getMailId() {
		return mailId;
	}
	public void setMailId(String mailId) {
		this.mailId = mailId;
	}

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
	}
	public String getCipherSend() {
		return cipherSend;
	}
	public void setCipherSend(String cipherSend) {
		this.cipherSend = cipherSend;
	}
	public String getCipherReci() {
		return cipherReci;
	}
	public void setCipherReci(String cipherReci) {
		this.cipherReci = cipherReci;
	}
	public String getMsg() {
		return msg;
	}
	public void setMsg(String msg) {
		this.msg = msg;
	}
	public String getTextId() {
		return textId;
	}
	public void setTextId(String textId) {
		this.textId = textId;
	}

	public String getCipher() {
		return cipher;
	}

	public void setCipher(String cipher) {
		this.cipher = cipher;
	}



	public List<String> getMailIds() {
		return mailIds;
	}
	public void setMailIds(List<String> mailIds) {
		this.mailIds = mailIds;
	}
	// Factory method for SEND operation
	public static MailData makeSend(String alg, String cipher, String cipherSend, String cipherReci, String textId) {
		MailData data = new MailData();
		data.setOp(Op.SEND.toLowerCase());
		data.setAlg(alg);
		data.setCipher(cipher);
		data.setCipherSend(cipherSend);
		data.setCipherReci(cipherReci);
		data.setTextId(textId);
		return data;
	}

	// Factory method for DELETE operation
	public static MailData makeDelete(List<String> mailIds) {
		MailData data = new MailData();
		data.setOp(Op.DELETE.toLowerCase());
		data.setMailIds(mailIds);
		return data;
	}

	// Factory method for RECOVER operation
	public static MailData makeRecover(List<String> mailIds) {
		MailData data = new MailData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setMailIds(mailIds);
		return data;
	}
	
}
