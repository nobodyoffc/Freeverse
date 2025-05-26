package data.feipData;

import data.fcData.FcObject;

import java.util.List;

public class GroupHistory extends FcObject {

	private Long height;
	private Integer index;
	private Long time;
	private String signer;
	
	private String gid;
	private List<String> gids;
	private String op;
	private String name;
	private String desc;
	
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

	public String getGid() {
		return gid;
	}

	public void setGid(String gid) {
		this.gid = gid;
	}

	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
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

	public Long getCdd() {
		return cdd;
	}

	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}

	public List<String> getGids() {
		return gids;
	}

	public void setGids(List<String> gids) {
		this.gids = gids;
	}
}
