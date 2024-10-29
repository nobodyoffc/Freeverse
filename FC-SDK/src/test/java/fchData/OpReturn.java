package fchData;

public class OpReturn {

    private String txId;        //txid
    private long height;        //block height
    private long time;
    private int txIndex;        //tx index in the block
    private String opReturn;    //OP_RETURN text
    private String signer;    //address of the first input.
    private String recipient;    //address of the first output, but the first input address and opReturn output.
    private long cdd;

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public int getTxIndex() {
        return txIndex;
    }

    public void setTxIndex(int txIndex) {
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

    public long getCdd() {
        return cdd;
    }

    public void setCdd(long cdd) {
        this.cdd = cdd;
    }


}
