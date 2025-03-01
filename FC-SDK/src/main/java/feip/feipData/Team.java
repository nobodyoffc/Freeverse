package feip.feipData;

import fcData.FcObject;
import tools.JsonTools;

public class Team extends FcObject {

	private String owner;
	private String stdName;
	private String[] localNames;
	private String[] waiters;
	private String[] accounts;
	private String consensusId;
	private String desc;
	private String[] members;
	private Long memberNum;
	private String[] exMembers;
	private String[] managers;
	private String transferee;
	private String[] invitees;
	private String[] notAgreeMembers;
	
	private Long birthTime;
	private Long birthHeight;
	private String lastTxId;
	private Long lastTime;
	private Long lastHeight;
	private Long tCdd;
	private Float tRate;
	private Boolean active;

	public String toJson(){
		return JsonTools.toJson(this);
	}
	public String toNiceJson(){
		return JsonTools.toNiceJson(this);
	}
	public static Team fromJson(String json){
		return JsonTools.fromJson(json, Team.class);
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

	public Long getMemberNum() {
		return memberNum;
	}

	public void setMemberNum(Long memberNum) {
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
