package data.fchData;

import data.fcData.FcObject;

public class TxMark extends FcObject {
	private Long outValue;
	private Long fee;
	private Long cdd;
	

	public Long getOutValue() {
		return outValue;
	}
	public void setOutValue(Long outValue) {
		this.outValue = outValue;
	}
	public Long getFee() {
		return fee;
	}
	public void setFee(Long fee) {
		this.fee = fee;
	}
	public Long getCdd() {
		return cdd;
	}
	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}

}
