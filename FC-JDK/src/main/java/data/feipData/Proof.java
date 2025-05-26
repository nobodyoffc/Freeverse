package data.feipData;

import data.fcData.FcObject;

public class Proof extends FcObject {

	private String title;
	private String content;
	private String[] cosignersInvited;
	private String[] cosignersSigned;
	private Boolean transferable;
	private Boolean active;
	private Boolean destroyed;
	
	private String issuer;
	private String owner;

	private Long birthTime;
	private Long birthHeight;
	private String lastTxId;
	private Long lastTime;
	private Long lastHeight;

	public Boolean isDestroyed() {
		return destroyed;
	}

	public void setDestroyed(Boolean destroyed) {
		this.destroyed = destroyed;
	}

	public String getLastTxId() {
		return lastTxId;
	}

	public void setLastTxId(String lastTxId) {
		this.lastTxId = lastTxId;
	}

	public Long getLastTime() {
		return lastTime;
	}

	public void setLastTime(Long lastTime) {
		this.lastTime = lastTime;
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

	public Boolean isTransferable() {
		return transferable;
	}

	public void setTransferable(Boolean transferable) {
		this.transferable = transferable;
	}

	public Boolean isActive() {
		return active;
	}

	public void setActive(Boolean active) {
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
}
