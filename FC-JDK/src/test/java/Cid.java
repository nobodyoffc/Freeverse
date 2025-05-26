import data.fcData.FcObject;

public class Cid extends FcObject {

	private String cid;
	private String [] usedCids;
	private String prikey;
	private String master;
	private String[] homepages;
	private String noticeFee;
	private Long reputation;
	private Long hot;
	private Long nameTime;
	private Long lastHeight;

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

	public String getPrikey() {
		return prikey;
	}

	public void setPrikey(String prikey) {
		this.prikey = prikey;
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
