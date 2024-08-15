package fchData;

import java.util.ArrayList;

public class BlockHas {
    private long height;        //height
    private String blockId;        //block hash
    private ArrayList<TxMark> txMarks;

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public ArrayList<TxMark> getTxMarks() {
        return txMarks;
    }

    public void setTxMarks(ArrayList<TxMark> txMarks) {
        this.txMarks = txMarks;
    }

}
