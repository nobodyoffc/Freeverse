package data.fchData;

import data.fcData.FcObject;

import java.util.ArrayList;

public class BlockHas extends FcObject {
	private Long height;		//height
	private ArrayList<TxMark> txMarks;
	
	public Long getHeight() {
		return height;
	}
	public void setHeight(Long height) {
		this.height = height;
	}
	public ArrayList<TxMark> getTxMarks() {
		return txMarks;
	}
	public void setTxMarks(ArrayList<TxMark> txMarks) {
		this.txMarks = txMarks;
	}
	
}
