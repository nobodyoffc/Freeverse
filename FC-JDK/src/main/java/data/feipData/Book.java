package data.feipData;

import data.fcData.FcObject;

import java.util.List;

public class Book extends FcObject {
	private String title;
	private String ver;
	private String did;
	private List<String> authors;
	private String lang;
	private String summary;

	private String publisher;
	private Long birthTime;
	private Long birthHeight;
	private String lastTxId;
	private Long lastTime;
	private Long lastHeight;
	private Long tCdd;
	private Float tRate;
	private Boolean deleted;

	public String getVer() {
		return ver;
	}
	public void setVer(String ver) {
		this.ver = ver;
	}
	public String getDid() {
		return did;
	}
	public void setDid(String did) {
		this.did = did;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}

	public String getPublisher() {
		return publisher;
	}
	public void setPublisher(String publisher) {
		this.publisher = publisher;
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
	public Long getLastHeight() {
		return lastHeight;
	}
	public void setLastHeight(Long lastHeight) {
		this.lastHeight = lastHeight;
	}
	public Long gettCdd() {
		return tCdd;
	}
	public void settCdd(Long tCdd) {
		this.tCdd = tCdd;
	}
	public Float gettRate() {
		return tRate;
	}
	public void settRate(Float tRate) {
		this.tRate = tRate;
	}
	public Boolean isDeleted() {
		return deleted;
	}
	public void setDeleted(Boolean deleted) {
		this.deleted = deleted;
	}

	public List<String> getAuthors() {
		return authors;
	}

	public void setAuthors(List<String> authors) {
		this.authors = authors;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public Boolean getDeleted() {
		return deleted;
	}


	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}
} 