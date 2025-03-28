package feip.feipData;

import fcData.FcObject;

import java.util.List;

public class ProofHistory extends FcObject {
	
	private Long height;
	private Integer index;
	private Long time;
	private String signer;
	private String recipient;

	private String proofId;
	private List<String> proofIds;
	private String op;
	private String title;
	private String content;
	private String[] cosigners;
	private Boolean transferable;
	private Boolean allSignsRequired;

	public String getRecipient() {
		return recipient;
	}

	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}

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

	public String getProofId() {
		return proofId;
	}

	public void setProofId(String proofId) {
		this.proofId = proofId;
	}

	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String[] getCosigners() {
		return cosigners;
	}

	public void setCosigners(String[] cosigners) {
		this.cosigners = cosigners;
	}

	public Boolean isTransferable() {
		return transferable;
	}

	public void setTransferable(Boolean transferable) {
		this.transferable = transferable;
	}

	public Boolean isAllSignsRequired() {
		return allSignsRequired;
	}

	public void setAllSignsRequired(Boolean allSignsRequired) {
		this.allSignsRequired = allSignsRequired;
	}

	public List<String> getProofIds() {
		return proofIds;
	}

	public void setProofIds(List<String> proofIds) {
		this.proofIds = proofIds;
	}
}
