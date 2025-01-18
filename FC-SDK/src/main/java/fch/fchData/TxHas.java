package fch.fchData;

import java.util.ArrayList;

public class TxHas {
	private String txId;			//txid
	private String rawTx;
	private Long height;		//height
	private ArrayList<CashMark> inMarks;
	private  ArrayList<CashMark> outMarks;
	
	public String getTxId() {
		return txId;
	}
	public void setTxId(String txId) {
		this.txId = txId;
	}
	public Long getHeight() {
		return height;
	}
	public void setHeight(Long height) {
		this.height = height;
	}
	public ArrayList<CashMark> getInMarks() {
		return inMarks;
	}
	public void setInMarks(ArrayList<CashMark> inMarks) {
		this.inMarks = inMarks;
	}
	public ArrayList<CashMark> getOutMarks() {
		return outMarks;
	}
	public void setOutMarks(ArrayList<CashMark> outMarks) {
		this.outMarks = outMarks;
	}

	public String getRawTx() {
		return rawTx;
	}

	public void setRawTx(String rawTx) {
		this.rawTx = rawTx;
	}
}
