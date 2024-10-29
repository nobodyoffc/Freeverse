package fchData;

public class Tx {

    //from block;
    private String txId;        //txid,hash of tx
    private int version;        //version
    private long lockTime;    //lockTime
    private long blockTime;        //blockTime
    private String blockId;        //block ID, hash of block head
    private int txIndex;        //the index of this tx in the block
    private String coinbase;    //string of the coinbase script
    private int outCount;        //number of outputs
    private int inCount;        //number of inputs
    private long height;        //block height of the block

    private String opReBrief;    //Former 30 bytes of OP_RETURN data in String.

    //calculated
    private long inValueT;        //total amount of inputs
    private long outValueT;        //total amount of outputs
    private long fee;        //tx fee

    private long cdd;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public long getLockTime() {
        return lockTime;
    }

    public void setLockTime(long lockTime) {
        this.lockTime = lockTime;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public long getBlockTime() {
        return blockTime;
    }

    public void setBlockTime(long blockTime) {
        this.blockTime = blockTime;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public int getTxIndex() {
        return txIndex;
    }

    public void setTxIndex(int txIndex) {
        this.txIndex = txIndex;
    }

    public String getCoinbase() {
        return coinbase;
    }

    public void setCoinbase(String coinbase) {
        this.coinbase = coinbase;
    }

    public int getOutCount() {
        return outCount;
    }

    public void setOutCount(int outCount) {
        this.outCount = outCount;
    }

    public int getInCount() {
        return inCount;
    }

    public void setInCount(int inCount) {
        this.inCount = inCount;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public String getOpReBrief() {
        return opReBrief;
    }

    public void setOpReBrief(String opReBrief) {
        this.opReBrief = opReBrief;
    }

    public long getInValueT() {
        return inValueT;
    }

    public void setInValueT(long inValueT) {
        this.inValueT = inValueT;
    }

    public long getOutValueT() {
        return outValueT;
    }

    public void setOutValueT(long outValueT) {
        this.outValueT = outValueT;
    }

    public long getFee() {
        return fee;
    }

    public void setFee(long fee) {
        this.fee = fee;
    }

    public long getCdd() {
        return cdd;
    }

    public void setCdd(long cdd) {
        this.cdd = cdd;
    }

}
