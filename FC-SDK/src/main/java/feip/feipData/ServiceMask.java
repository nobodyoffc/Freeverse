package feip.feipData;

import feip.feipData.serviceParams.Params;

public class ServiceMask {

	protected String sid;
	protected String stdName;
	protected String[] types;
	protected String ver;
	protected String owner;
	protected String account;
	protected Long lastTime;
	protected Long tCdd;
	protected Float tRate;
	protected Boolean active;

	public static ServiceMask ServiceToMask(Service service,String account){
		ServiceMask serviceMask = new ServiceMask();
		serviceMask.setSid(service.getSid());
		serviceMask.setStdName(service.getStdName());
		serviceMask.setTypes(service.getTypes());
		serviceMask.setVer(service.getVer());
		serviceMask.setOwner(service.getOwner());
		serviceMask.setLastTime(service.getLastTime());
		serviceMask.settCdd(service.gettCdd());
		serviceMask.settRate(service.gettRate());
		serviceMask.setAccount(account);
		return serviceMask;
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

	public String[] getTypes() {
		return types;
	}

	public void setTypes(String[] types) {
		this.types = types;
	}

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getAccount() {
		return account;
	}

	public void setAccount(String account) {
		this.account = account;
	}

	public Long getLastTime() {
		return lastTime;
	}

	public void setLastTime(Long lastTime) {
		this.lastTime = lastTime;
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

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}
}
