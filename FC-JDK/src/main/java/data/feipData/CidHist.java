package data.feipData;

import data.fcData.FcObject;

import java.util.List;

public class CidHist extends FcObject {
	//txId
	private Long height;
	private Integer index;
	private Long time;
	private String type;
	private String sn;
	private String ver;
	private String signer;
	private String op;
	private String name;
	private String prikey;
	private String master;
	private String cipherPrikey;
	private String alg;
	private List<String> homepages;
	private String noticeFee;

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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSn() {
		return sn;
	}

	public void setSn(String sn) {
		this.sn = sn;
	}

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

	public String getSigner() {
		return signer;
	}

	public void setSigner(String signer) {
		this.signer = signer;
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

	public String getPrikey() {
		return prikey;
	}

	public void setPrikey(String prikey) {
		this.prikey = prikey;
	}

	public String getMaster() {
		return master;
	}

	public void setMaster(String master) {
		this.master = master;
	}

	public String getCipherPrikey() {
		return cipherPrikey;
	}

	public void setCipherPrikey(String cipherPrikey) {
		this.cipherPrikey = cipherPrikey;
	}

	public String getAlg() {
		return alg;
	}

	public void setAlg(String alg) {
		this.alg = alg;
	}

	public List<String> getHomepages() {
		return homepages;
	}

	public void setHomepages(List<String> homepages) {
		this.homepages = homepages;
	}

	public String getNoticeFee() {
		return noticeFee;
	}

	public void setNoticeFee(String noticeFee) {
		this.noticeFee = noticeFee;
	}
}
