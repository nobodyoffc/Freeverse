package data.feipData;

import data.fcData.FcObject;

import java.util.List;
import java.util.Map;

public class AppHistory extends FcObject {

	private Long height;
	private Integer index;
	private Long time;
	private String signer;

	private String stdName;
	private String ver;
	private Map<String, String> localNames;
	private String desc;
	private List<String> types;
	private Map<String, String> home;
	private List<App.Download> downloads;
	private List<String> waiters;
	private List<String> protocols;
	private List<String> codes;
	private List<String> services;
	private String closeStatement;
	
	private String aid;
	private List<String> aids;
	private String op;
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
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public List<String> getTypes() {
		return types;
	}
	public void setTypes(List<String> types) {
		this.types = types;
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
	public String getAid() {
		return aid;
	}
	public void setAid(String aid) {
		this.aid = aid;
	}
	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
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
	public List<String> getCodes() {
		return codes;
	}
	public void setCodes(List<String> codes) {
		this.codes = codes;
	}
	public String getCloseStatement() {
		return closeStatement;
	}
	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}
	public List<App.Download> getDownloads() {
		return downloads;
	}
	public void setDownloads(List<App.Download> downloads) {
		this.downloads = downloads;
	}

	public List<String> getAids() {
		return aids;
	}

	public void setAids(List<String> aids) {
		this.aids = aids;
	}

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

}
