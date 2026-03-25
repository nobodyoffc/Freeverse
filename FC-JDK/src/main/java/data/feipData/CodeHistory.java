package data.feipData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import data.fcData.FcObject;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeHistory extends FcObject {
	//txId
	private Long height;
	private Integer index;
	private Long time;
	private String signer;

	private String codeId;
	private List<String> codeIds;
	private String op;
	private String name;
	private String ver;
	private String did;
	private String desc;
	private List<String> langs;
	private Map<String, String> home;
	private List<String> protocols;
	private List<String> waiters;
	private Integer rate;
	private String closeStatement;
	
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

	public String getCodeId() {
		return codeId;
	}

	public void setCodeId(String codeId) {
		this.codeId = codeId;
	}

	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public List<String> getLangs() {
		return langs;
	}

	public void setLangs(List<String> langs) {
		this.langs = langs;
	}

	public Map<String, String> getHome() {
		return home;
	}

	public void setHome(Map<String, String> home) {
		this.home = home;
	}

	public List<String> getProtocols() {
		return protocols;
	}

	public void setProtocols(List<String> protocols) {
		this.protocols = protocols;
	}

	public List<String> getWaiters() {
		return waiters;
	}

	public void setWaiters(List<String> waiters) {
		this.waiters = waiters;
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

	public String getCloseStatement() {
		return closeStatement;
	}

	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}

	public List<String> getCodeIds() {
		return codeIds;
	}

	public void setCodeIds(List<String> codeIds) {
		this.codeIds = codeIds;
	}
}
