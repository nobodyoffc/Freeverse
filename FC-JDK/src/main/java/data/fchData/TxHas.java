package data.fchData;

import data.fcData.FcObject;

import java.util.ArrayList;

public class TxHas extends FcObject {
	private String rawTx;
	private Long height;		//height
	private Integer txIndex;
	private ArrayList<CashMask> inMarks;
	private  ArrayList<CashMask> outMarks;

	public Long getHeight() {
		return height;
	}
	public void setHeight(Long height) {
		this.height = height;
	}
	public ArrayList<CashMask> getInMarks() {
		return inMarks;
	}
	public void setInMarks(ArrayList<CashMask> inMarks) {
		this.inMarks = inMarks;
	}
	public ArrayList<CashMask> getOutMarks() {
		return outMarks;
	}
	public void setOutMarks(ArrayList<CashMask> outMarks) {
		this.outMarks = outMarks;
	}

	public String getRawTx() {
		return rawTx;
	}

	public void setRawTx(String rawTx) {
		this.rawTx = rawTx;
	}

	public Integer getTxIndex() {
		return txIndex;
	}

	public void setTxIndex(Integer txIndex) {
		this.txIndex = txIndex;
	}
}
