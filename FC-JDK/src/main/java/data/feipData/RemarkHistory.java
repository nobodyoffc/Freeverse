package data.feipData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import data.fcData.FcObject;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RemarkHistory extends FcObject {
	//txId
	private Long height;
	private Integer index;
	private Long time;
	private String signer;

	private String remarkId;
	private String[] remarkIds;

	private String op;

	private String title;
	private String ver;
	private String did;
	private String onDid;

	private List<String> authors;
	private String lang;
	private String summary;

	private Integer rate;
	
	private Long cdd;

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

	public String getSigner() {
		return signer;
	}

	public void setSigner(String signer) {
		this.signer = signer;
	}

	public String getRemarkId() {
		return remarkId;
	}

	public void setRemarkId(String remarkId) {
		this.remarkId = remarkId;
	}

	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

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

	public String getOnDid() {
		return onDid;
	}

	public void setOnDid(String onDid) {
		this.onDid = onDid;
	}

	public Integer getRate() {
		return rate;
	}

	public void setRate(Integer rate) {
		this.rate = rate;
	}

	public Long getCdd() {
		return cdd;
	}

	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}

	public String[] getRemarkIds() {
		return remarkIds;
	}

	public void setRemarkIds(String[] remarkIds) {
		this.remarkIds = remarkIds;
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

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}
} 