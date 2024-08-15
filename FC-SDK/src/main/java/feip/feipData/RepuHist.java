package feip.feipData;

public class RepuHist {
	private String txId;
	private long height;
	private int index;
	private long time;
	
	private String ratee;
	private String rater;
	private long reputation;
	private long hot;
	private String cause;
	
	public String getTxId() {
		return txId;
	}
	public void setTxId(String txId) {
		this.txId = txId;
	}
	public long getHeight() {
		return height;
	}
	public void setHeight(long height) {
		this.height = height;
	}
	public int getIndex() {
		return index;
	}
	public void setIndex(int index) {
		this.index = index;
	}
	public long getTime() {
		return time;
	}
	public void setTime(long time) {
		this.time = time;
	}
	public String getRatee() {
		return ratee;
	}
	public void setRatee(String ratee) {
		this.ratee = ratee;
	}
	public String getRater() {
		return rater;
	}
	public void setRater(String rater) {
		this.rater = rater;
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
	public String getCause() {
		return cause;
	}
	public void setCause(String cause) {
		this.cause = cause;
	}

}
