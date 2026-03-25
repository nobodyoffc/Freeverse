package data.feipData;

import data.fcData.FcObject;

import java.util.List;
import java.util.Map;

public class Code extends FcObject {
	private String name;
	private String ver;
	private String did;
	private String desc;
	private List<String> langs;
	private Map<String, String> home;
	private List<String> protocols;
	private List<String> waiters;
	
	private String owner;
	private Long birthTime;
	private Long birthHeight;
	private String lastTxId;
	private Long lastTime;
	private Long lastHeight;
	private Long tCdd;
	private Float tRate;
	private Boolean active;
	private Boolean closed;
	private String closeStatement;

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
	public Boolean isActive() {
		return active;
	}
	public void setActive(Boolean active) {
		this.active = active;
	}
	public Boolean isClosed() {
		return closed;
	}
	public void setClosed(Boolean closed) {
		this.closed = closed;
	}
	public String getCloseStatement() {
		return closeStatement;
	}
	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}
	
	
}
