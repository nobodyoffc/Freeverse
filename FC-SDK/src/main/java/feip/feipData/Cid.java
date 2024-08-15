package feip.feipData;

public class Cid {
	
	private String fid;
	private String cid;
	private String [] usedCids;
	private String priKey;
	private String master;
	private String[] homepages;
	private String noticeFee;
	private long reputation;
	private long hot;
	private long nameTime;
	private long lastHeight;

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

	public String getMaster() {
		return master;
	}

	public void setMaster(String master) {
		this.master = master;
	}

	public String[] getHomepages() {
		return homepages;
	}

	public void setHomepages(String[] homepage) {
		this.homepages = homepage;
	}

	public String getNoticeFee() {
		return noticeFee;
	}

	public void setNoticeFee(String noticeFee) {
		this.noticeFee = noticeFee;
	}

	public long getReputation() {
		return reputation;
	}

	public void setReputation(long reputation) {
		this.reputation = reputation;
	}

	public long getHot() {
		return hot;
	}

	public void setHot(long hot) {
		this.hot = hot;
	}

	public long getNameTime() {
		return nameTime;
	}

	public void setNameTime(long nameTime) {
		this.nameTime = nameTime;
	}

	public long getLastHeight() {
		return lastHeight;
	}

	public void setLastHeight(long lastHeight) {
		this.lastHeight = lastHeight;
	}

	public String getPriKey() {
		return priKey;
	}

	public void setPriKey(String priKey) {
		this.priKey = priKey;
	}

}
