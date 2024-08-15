package feip.feipData;

public class Proof {

	private String proofId;
	private String title;
	private String content;
	private String[] cosignersInvited;
	private String[] cosignersSigned;
	private boolean transferable;
	private boolean active;
	private boolean destroyed;
	
	private String issuer;
	private String owner;

	private long birthTime;
	private long birthHeight;
	private String lastTxId;
	private long lastTime;
	private long lastHeight;

	public boolean isDestroyed() {
		return destroyed;
	}

	public void setDestroyed(boolean destroyed) {
		this.destroyed = destroyed;
	}

	public String getLastTxId() {
		return lastTxId;
	}

	public void setLastTxId(String lastTxId) {
		this.lastTxId = lastTxId;
	}

	public long getLastTime() {
		return lastTime;
	}

	public void setLastTime(long lastTime) {
		this.lastTime = lastTime;
	}

	public String getProofId() {
		return proofId;
	}

	public void setProofId(String proofId) {
		this.proofId = proofId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String[] getCosignersInvited() {
		return cosignersInvited;
	}

	public void setCosignersInvited(String[] cosignersInvited) {
		this.cosignersInvited = cosignersInvited;
	}

	public String[] getCosignersSigned() {
		return cosignersSigned;
	}

	public void setCosignersSigned(String[] cosignersSigned) {
		this.cosignersSigned = cosignersSigned;
	}

	public boolean isTransferable() {
		return transferable;
	}

	public void setTransferable(boolean transferable) {
		this.transferable = transferable;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(String issuer) {
		this.issuer = issuer;
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
}
