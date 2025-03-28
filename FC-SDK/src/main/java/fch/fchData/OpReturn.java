package fch.fchData;

import fcData.FcObject;

public class OpReturn extends FcObject {

	private Long height;		//block height
	private Long time;
	private Integer txIndex;		//tx index in the block
	private String opReturn;	//OP_RETURN text
	private String signer;	//address of the first input.
	private String recipient;	//address of the first output, but the first input address and opReturn output.
	private Long cdd;

	public Long getHeight() {
		return height;
	}
	public void setHeight(Long height) {
		this.height = height;
	}
	public Long getTime() {
		return time;
	}
	public void setTime(Long time) {
		this.time = time;
	}
	public Integer getTxIndex() {
		return txIndex;
	}
	public void setTxIndex(Integer txIndex) {
		this.txIndex = txIndex;
	}
	public String getOpReturn() {
		return opReturn;
	}
	public void setOpReturn(String opReturn) {
		this.opReturn = opReturn;
	}
	public String getSigner() {
		return signer;
	}
	public void setSigner(String signer) {
		this.signer = signer;
	}
	public String getRecipient() {
		return recipient;
	}
	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}
	public Long getCdd() {
		return cdd;
	}
	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}

	
	
}
