package feip.feipData;

public class Secret {
	private String secretId;
    private String alg;
	private String cipher;
	
	private String owner;
	private long birthTime;
	private long birthHeight;
	private long lastHeight;
	private boolean active;
	
	public String getSecretId() {
		return secretId;
	}
	public void setSecretId(String secretId) {
		this.secretId = secretId;
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
	public long getBirthTime() {
		return birthTime;
	}
	public void setBirthTime(long birthTime) {
		this.birthTime = birthTime;
	}
	public long getBirthHeight() {
		return birthHeight;
	}
	public void setBirthHeight(long birthHeight) {
		this.birthHeight = birthHeight;
	}
	public long getLastHeight() {
		return lastHeight;
	}
	public void setLastHeight(long lastHeight) {
		this.lastHeight = lastHeight;
	}
	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	
	
}
