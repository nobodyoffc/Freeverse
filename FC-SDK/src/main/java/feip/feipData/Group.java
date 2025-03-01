package feip.feipData;

import fcData.FcObject;
import tools.JsonTools;

public class Group extends FcObject {
	private String name;
	private String desc;
	
	private String[] namers;
	private String[] members;
	private Long memberNum;
	private Long birthTime;
	private Long birthHeight;
	private String lastTxId;
	private Long lastTime;
	private Long lastHeight;
	private Long cddToUpdate;
	private Long tCdd;

	public String toJson(){
		return JsonTools.toJson(this);
	}

	public String toNiceJson(){
		return JsonTools.toNiceJson(this);
	}

	public static Group fromJson(String json){
		return JsonTools.fromJson(json, Group.class);
	}

	public Long getMemberNum() {
		return memberNum;
	}

	public void setMemberNum(Long memberNum) {
		this.memberNum = memberNum;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
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
	public Long getCddToUpdate() {
		return cddToUpdate;
	}
	public void setCddToUpdate(Long requiredCdd) {
		this.cddToUpdate = requiredCdd;
	}
	public Long gettCdd() {
		return tCdd;
	}
	public void settCdd(Long tCdd) {
		this.tCdd = tCdd;
	}
	public String[] getNamers() {
		return namers;
	}
	public void setNamers(String[] namers) {
		this.namers = namers;
	}
	public String[] getMembers() {
		return members;
	}
	public void setMembers(String[] members) {
		this.members = members;
	}
}
