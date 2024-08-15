package feip.feipData;

public class MailData {

	private String op;
	private String mailId;
    private String alg;
	private String msg;
	private String cipher;
	private String cipherSend;
	private String cipherReci;
	private String textId;
	
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
	
}
