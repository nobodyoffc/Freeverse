package fcData;

import java.util.List;

import tools.JsonTools;

public class Room extends FcObject{
	private String owner;
	private String name;
	private List<String> members;
	private Long memberNum;
	private Long birthTime;

	public String toJson(){
		return JsonTools.toJson(this);
	}

	public String toNiceJson(){
		return JsonTools.toNiceJson(this);
	}

	public static Room fromJson(String json){
		return JsonTools.fromJson(json, Room.class);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getMembers() {
		return members;
	}

	public void setMembers(List<String> members) {
		this.members = members;
	}

	public Long getMemberNum() {
		return memberNum;
	}

	public void setMemberNum(Long memberNum) {
		this.memberNum = memberNum;
	}

	public Long getBirthTime() {
		return birthTime;
	}

	public void setBirthTime(Long birthTime) {
		this.birthTime = birthTime;
	}

}
