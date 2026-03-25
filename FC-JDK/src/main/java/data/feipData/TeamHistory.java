package data.feipData;

import data.fcData.FcObject;

import java.util.List;
import java.util.Map;

public class TeamHistory extends FcObject {
	private Long height;
	private Integer index;
	private Long time;
	private String signer;
	private Long cdd;
	
	private String tid;
	private List<String> tids;
	private String op;
	private String stdName;
	private Map<String, String> localNames;
	private String[] waiters;
	private String[] accounts;
	private String consensusId;
	private String desc;
	private Map<String, String> home;
	private String transferee;
	private String[] list;
	private Integer rate;

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
	public Long getCdd() {
		return cdd;
	}
	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}
	public String getTid() {
		return tid;
	}
	public void setTid(String tid) {
		this.tid = tid;
	}
	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
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
	public String getConsensusId() {
		return consensusId;
	}
	public void setConsensusId(String consensusId) {
		this.consensusId = consensusId;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public String getTransferee() {
		return transferee;
	}
	public void setTransferee(String transferee) {
		this.transferee = transferee;
	}
	public String[] getList() {
		return list;
	}
	public void setList(String[] list) {
		this.list = list;
	}
	public Integer getRate() {
		return rate;
	}
	public void setRate(Integer rate) {
		this.rate = rate;
	}

	public String[] getWaiters() {
		return waiters;
	}

	public void setWaiters(String[] waiters) {
		this.waiters = waiters;
	}

	public String[] getAccounts() {
		return accounts;
	}

	public void setAccounts(String[] accounts) {
		this.accounts = accounts;
	}
    public List<String> getTids() {
        return tids;
    }
    public void setTids(List<String> tids) {
        this.tids = tids;
    }

	public Map<String, String> getHome() {
		return home;
	}

	public void setHome(Map<String, String> home) {
		this.home = home;
	}
}
