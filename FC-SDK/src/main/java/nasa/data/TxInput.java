package nasa.data;

public class TxInput {

    private byte[] priKey32;

    private String txId;

    private long amount;

    private int index;

    public byte[] getPriKey32() {
        return priKey32;
    }

    public void setPriKey32(byte[] priKey32) {
        this.priKey32 = priKey32;
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
