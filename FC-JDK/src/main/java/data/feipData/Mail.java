package data.feipData;

import data.fcData.FcObject;

public class Mail extends FcObject {
    private String alg;
	private String cipher;
	private String cipherSend;
	private String cipherReci;
	private String textId;
	
	private String sender;
	private String recipient;
	private Long birthTime;
	private Long birthHeight;
	private Long lastHeight;
	private Boolean active;


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
	public Long getBirthTime() {
		return birthTime;
	}
	public void setBirthTime(Long birthTime) {
		this.birthTime = birthTime;
	}
	public Long getBirthHeight() {
		return birthHeight;
	}
	public void setBirthHeight(Long birthHeight) {
		this.birthHeight = birthHeight;
	}
	public Long getLastHeight() {
		return lastHeight;
	}
	public void setLastHeight(Long lastHeight) {
		this.lastHeight = lastHeight;
	}
	public Boolean isActive() {
		return active;
	}
	public void setActive(Boolean active) {
		this.active = active;
	}
	public String getSender() {
		return sender;
	}
	public void setSender(String sender) {
		this.sender = sender;
	}
	public String getRecipient() {
		return recipient;
	}
	public void setRecipient(String recipient) {
		this.recipient = recipient;
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

	public Boolean getActive() {
		return active;
	}
}
