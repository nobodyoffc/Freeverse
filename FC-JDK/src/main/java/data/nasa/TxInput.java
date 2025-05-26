package data.nasa;

public class TxInput {

    private byte[] prikey32;

    private String txId;

    private long amount;

    private int index;

    public byte[] getPrikey32() {
        return prikey32;
    }

    public void setPrikey32(byte[] prikey32) {
        this.prikey32 = prikey32;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

}
