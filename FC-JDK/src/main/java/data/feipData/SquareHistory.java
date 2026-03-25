package data.feipData;

import data.fcData.FcObject;

import java.util.List;
import java.util.Map;

public class SquareHistory extends FcObject {

	private Long height;
	private Integer index;
	private Long time;
	private String signer;
	
	private String squareId;
	private List<String> squareIds;
	private String op;
	private String name;
	private String desc;
	private Map<String, String> home;
	
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

	public String getSquareId() {
		return squareId;
	}

	public void setSquareId(String squareId) {
		this.squareId = squareId;
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

	public List<String> getSquareIds() {
		return squareIds;
	}

	public void setSquareIds(List<String> squareIds) {
		this.squareIds = squareIds;
	}

	public Map<String, String> getHome() {
		return home;
	}

	public void setHome(Map<String, String> home) {
		this.home = home;
	}
}
