package feip.feipData;

import java.util.List;

public class Contact {

	private String contactId;
	private List<String> contactIds;
    private String alg;
	private String cipher;
	
	private String owner;
	private Long birthTime;
	private Long birthHeight;
	private Long lastHeight;
	private Boolean active;
	
	public String getContactId() {
		return contactId;
	}
	public void setContactId(String contactId) {
		this.contactId = contactId;
	}
	public List<String> getContactIds() {
		return contactIds;
	}
	public void setContactIds(List<String> contactIds) {
		this.contactIds = contactIds;
	}	
    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
	}
	public String getCipher() {
		return cipher;
	}
	public void setCipher(String cipher) {
		this.cipher = cipher;
	}
	public String getOwner() {
		return owner;
	}
	public void setOwner(String owner) {
		this.owner = owner;
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
}
