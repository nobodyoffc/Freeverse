package feip.feipData;

import fcData.FcObject;

public class RepuHist extends FcObject {
	private Long height;
	private Integer index;
	private Long time;
	
	private String ratee;
	private String rater;
	private Long reputation;
	private Long hot;
	private String cause;
	public Long getHeight() {
		return height;
	}
	public void setHeight(Long height) {
		this.height = height;
	}
	public Integer getIndex() {
		return index;
	}
	public void setIndex(Integer index) {
		this.index = index;
	}
	public Long getTime() {
		return time;
	}
	public void setTime(Long time) {
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
	public String getCause() {
		return cause;
	}
	public void setCause(String cause) {
		this.cause = cause;
	}

}
