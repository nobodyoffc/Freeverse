package feip.feipData;

public class Team {

	private String tid;
	private String owner;
	private String stdName;
	private String[] localNames;
	private String[] waiters;
	private String[] accounts;
	private String consensusId;
	private String desc;
	private String[] members;
	private long memberNum;
	private String[] exMembers;
	private String[] managers;
	private String transferee;
	private String[] invitees;
	private String[] notAgreeMembers;
	
	private long birthTime;
	private long birthHeight;
	private String lastTxId;
	private long lastTime;
	private long lastHeight;
	private long tCdd;
	private float tRate;
	private boolean active;
	public String getTid() {
		return tid;
	}
	public void setTid(String tid) {
		this.tid = tid;
	}
	public String getOwner() {
		return owner;
	}
	public void setOwner(String owner) {
		this.owner = owner;
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
	public String[] getMembers() {
		return members;
	}
	public void setMembers(String[] members) {
		this.members = members;
	}
	public String[] getExMembers() {
		return exMembers;
	}
	public void setExMembers(String[] exMembers) {
		this.exMembers = exMembers;
	}
	public String[] getManagers() {
		return managers;
	}
	public void setManagers(String[] managers) {
		this.managers = managers;
	}
	public String getTransferee() {
		return transferee;
	}
	public void setTransferee(String transferee) {
		this.transferee = transferee;
	}
	public String[] getInvitees() {
		return invitees;
	}
	public void setInvitees(String[] invitees) {
		this.invitees = invitees;
	}
	public String[] getNotAgreeMembers() {
		return notAgreeMembers;
	}
	public void setNotAgreeMembers(String[] notAgreeMembers) {
		this.notAgreeMembers = notAgreeMembers;
	}
	public long getBirthTime() {
		return birthTime;
	}
	public void setBirthTime(long birthTime) {
		this.birthTime = birthTime;
	}
	public long getBirthHeight() {
		return birthHeight;
	}
	public void setBirthHeight(long birthHeight) {
		this.birthHeight = birthHeight;
	}
	public String getLastTxId() {
		return lastTxId;
	}
	public void setLastTxId(String lastTxId) {
		this.lastTxId = lastTxId;
	}
	public long getLastTime() {
		return lastTime;
	}
	public void setLastTime(long lastTime) {
		this.lastTime = lastTime;
	}
	public long getLastHeight() {
		return lastHeight;
	}
	public void setLastHeight(long lastHeight) {
		this.lastHeight = lastHeight;
	}
	public long gettCdd() {
		return tCdd;
	}
	public void settCdd(long tCdd) {
		this.tCdd = tCdd;
	}
	public float gettRate() {
		return tRate;
	}
	public void settRate(float tRate) {
		this.tRate = tRate;
	}
	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}

	public long getMemberNum() {
		return memberNum;
	}

	public void setMemberNum(long memberNum) {
		this.memberNum = memberNum;
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
