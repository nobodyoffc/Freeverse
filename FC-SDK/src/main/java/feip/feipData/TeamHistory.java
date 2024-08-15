package feip.feipData;

public class TeamHistory {

	private String txId;
	private long height;
	private int index;
	private long time;
	private String signer;
	private long cdd;
	
	private String tid;
	private String op;
	private String stdName;
	private String[] localNames;
	private String[] waiters;
	private String[] accounts;
	private String consensusId;
	private String desc;
	private String transferee;
	private String[] list;
	private int rate;
	
	public String getTxId() {
		return txId;
	}
	public void setTxId(String txId) {
		this.txId = txId;
	}
	public long getHeight() {
		return height;
	}
	public void setHeight(long height) {
		this.height = height;
	}
	public int getIndex() {
		return index;
	}
	public void setIndex(int index) {
		this.index = index;
	}
	public long getTime() {
		return time;
	}
	public void setTime(long time) {
		this.time = time;
	}
	public String getSigner() {
		return signer;
	}
	public void setSigner(String signer) {
		this.signer = signer;
	}
	public long getCdd() {
		return cdd;
	}
	public void setCdd(long cdd) {
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
