package feip.feipData;

import fcData.FcObject;

import java.util.List;

public class Secret extends FcObject {
	private List<String> secretIds;
    private String alg;
	private String cipher;
	
	private String owner;
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
	public List<String> getSecretIds() {
		return secretIds;
	}
	public void setSecretIds(List<String> secretIds) {
		this.secretIds = secretIds;
	}
	public Boolean getActive() {
		return active;
	}
	
	
}
