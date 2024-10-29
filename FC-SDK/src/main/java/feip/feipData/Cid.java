package feip.feipData;

public class Cid {
	
	private String fid;
	private String cid;
	private String [] usedCids;
	private String priKey;
	private String master;
	private String[] homepages;
	private String noticeFee;
	private Long reputation;
	private Long hot;
	private Long nameTime;
	private Long lastHeight;

	public String getFid() {
		return fid;
	}

	public void setFid(String fid) {
		this.fid = fid;
	}

	public String getCid() {
		return cid;
	}

	public void setCid(String cid) {
		this.cid = cid;
	}

	public String[] getUsedCids() {
		return usedCids;
	}

	public void setUsedCids(String[] usedCids) {
		this.usedCids = usedCids;
	}

	public String getPriKey() {
		return priKey;
	}

	public void setPriKey(String priKey) {
		this.priKey = priKey;
	}

	public String getMaster() {
		return master;
	}

	public void setMaster(String master) {
		this.master = master;
	}

	public String[] getHomepages() {
		return homepages;
	}

	public void setHomepages(String[] homepages) {
		this.homepages = homepages;
	}

	public String getNoticeFee() {
		return noticeFee;
	}

	public void setNoticeFee(String noticeFee) {
		this.noticeFee = noticeFee;
	}

	public Long getReputation() {
		return reputation;
	}

	public void setReputation(Long reputation) {
		this.reputation = reputation;
	}

	public Long getHot() {
		return hot;
	}

	public void setHot(Long hot) {
		this.hot = hot;
	}

	public Long getNameTime() {
		return nameTime;
	}

	public void setNameTime(Long nameTime) {
		this.nameTime = nameTime;
	}

	public Long getLastHeight() {
		return lastHeight;
	}

	public void setLastHeight(Long lastHeight) {
		this.lastHeight = lastHeight;
	}
}
