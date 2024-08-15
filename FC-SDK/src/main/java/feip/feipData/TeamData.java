package feip.feipData;

public class TeamData {

	private String tid;
	private String op;
	private String stdName;
	private String[] localNames;
	private String[] waiters;
	private String[] accounts;
	private String consensusId;
	private String desc;
	private String transferee;
	private String confirm;
	private String[] list;
	private int rate;
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
	public String[] getLocalNames() {
		return localNames;
	}
	public void setLocalNames(String[] localNames) {
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
	public String getConfirm() {
		return confirm;
	}
	public void setConfirm(String confirm) {
		this.confirm = confirm;
	}
	public String[] getList() {
		return list;
	}
	public void setList(String[] list) {
		this.list = list;
	}
	public int getRate() {
		return rate;
	}
	public void setRate(int rate) {
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
}
