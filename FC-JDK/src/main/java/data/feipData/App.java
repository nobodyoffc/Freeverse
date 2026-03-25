package data.feipData;

import data.fcData.FcObject;

import java.util.List;
import java.util.Map;

public class App extends FcObject {
	private String stdName;
	private Map<String, String> localNames;
	private List<String> types;
	private String desc;
	private String ver;
	private Map<String, String> home;
	private List<Download> downloads;
	private List<String> waiters;
	private List<String> protocols;
	private List<String> codes;
	private List<String> services;
	
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

	public String getStdName() {
		return stdName;
	}
	public void setStdName(String stdName) {
		this.stdName = stdName;
	}
	public Map<String, String> getLocalNames() {
		return localNames;
	}
	public void setLocalNames(Map<String, String> localNames) {
		this.localNames = localNames;
	}
	public List<String> getTypes() {
		return types;
	}
	public void setTypes(List<String> types) {
		this.types = types;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public Map<String, String> getHome() {
		return home;
	}
	public void setHome(Map<String, String> home) {
		this.home = home;
	}
	public List<String> getWaiters() {
		return waiters;
	}
	public void setWaiters(List<String> waiters) {
		this.waiters = waiters;
	}
	public List<String> getProtocols() {
		return protocols;
	}
	public void setProtocols(List<String> protocols) {
		this.protocols = protocols;
	}
	public List<String> getServices() {
		return services;
	}
	public void setServices(List<String> services) {
		this.services = services;
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
	public List<String> getCodes() {
		return codes;
	}
	public void setCodes(List<String> codes) {
		this.codes = codes;
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
	public List<Download> getDownloads() {
		return downloads;
	}
	public void setDownloads(List<Download> downloads) {
		this.downloads = downloads;
	}

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

	public static class Download {

		private String os;
		private String link;
		private String did;

		
		public String getOs() {
			return os;
		}
		public void setOs(String os) {
			this.os = os;
		}
		public String getLink() {
			return link;
		}
		public void setLink(String link) {
			this.link = link;
		}

		public String getDid() {
			return did;
		}

		public void setDid(String did) {
			this.did = did;
		}

	}

}
