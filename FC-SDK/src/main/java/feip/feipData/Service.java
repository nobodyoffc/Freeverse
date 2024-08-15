package feip.feipData;

import feip.feipData.serviceParams.DiskParams;
import javaTools.StringTools;

import java.util.Map;

public class Service {

	protected String sid;
	protected String stdName;
	protected String[] localNames;
	protected String desc;
	protected String[] types;
	protected String ver;
	protected String[] urls;
	protected String[] waiters;
	protected String[] protocols;
	protected String[] codes;
	protected String[] services;
	private Object params;
	protected String owner;
	
	protected Long birthTime;
	protected Long birthHeight;
	protected String lastTxId;
	protected Long lastTime;
	protected Long lastHeight;
	protected Long tCdd;
	protected Float tRate;
	protected Boolean active;
	protected Boolean closed;
	protected String closeStatement;

	public static Service fromMap(Map<String, String> map, Class<DiskParams> diskParamsClass) {
		Service service = new Service();

		service.sid = map.get("sid");
		service.stdName = map.get("stdName");
		service.localNames = StringTools.splitString(map.get("localNames"));
		service.desc = map.get("desc");
		service.ver = map.get("ver");
		service.types = StringTools.splitString(map.get("types"));
		service.urls = StringTools.splitString(map.get("urls"));
		service.waiters = StringTools.splitString(map.get("waiters"));
		service.protocols = StringTools.splitString(map.get("protocols"));
		service.services = StringTools.splitString(map.get("services"));
		service.codes = StringTools.splitString(map.get("codes"));

		service.owner = map.get("owner");

		service.birthTime = StringTools.parseLong(map.get("birthTime"));
		service.birthHeight = StringTools.parseLong(map.get("birthHeight"));
		service.lastTxId = map.get("lastTxId");
		service.lastTime = StringTools.parseLong(map.get("lastTime"));
		service.lastHeight = StringTools.parseLong(map.get("lastHeight"));
		service.tCdd = StringTools.parseLong(map.get("tCdd"));
		service.tRate = StringTools.parseFloat(map.get("tRate"));
		service.active = StringTools.parseBoolean(map.get("active"));
		service.closed = StringTools.parseBoolean(map.get("closed"));
		service.closeStatement = map.get("closeStatement");

		return service;
	}

	public String[] getServices() {
		return services;
	}

	public void setServices(String[] services) {
		this.services = services;
	}
	
	public String getSid() {
		return sid;
	}
	public void setSid(String sid) {
		this.sid = sid;
	}
	public String getStdName() {
		return stdName;
	}
	public void setStdName(String stdName) {
		this.stdName = stdName;
	}
	public String[] getLocalNames() {
		return localNames;
	}
	public void setLocalNames(String[] localNames) {
		this.localNames = localNames;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public String[] getTypes() {
		return types;
	}
	public void setTypes(String[] types) {
		this.types = types;
	}
	public String[] getUrls() {
		return urls;
	}
	public void setUrls(String[] urls) {
		this.urls = urls;
	}
	public String[] getWaiters() {
		return waiters;
	}
	public void setWaiters(String[] waiters) {
		this.waiters = waiters;
	}
	public String getOwner() {
		return owner;
	}
	public void setOwner(String signer) {
		this.owner = signer;
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
	public String[] getProtocols() {
		return protocols;
	}
	public void setProtocols(String[] protocols) {
		this.protocols = protocols;
	}
	public Object getParams() {
		return params;
	}
	public void setParams(Object params) {
		this.params = params;
	}
	public String[] getCodes() {
		return codes;
	}
	public void setCodes(String[] codes) {
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

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

	public Boolean getActive() {
		return active;
	}

	public Boolean getClosed() {
		return closed;
	}
}
