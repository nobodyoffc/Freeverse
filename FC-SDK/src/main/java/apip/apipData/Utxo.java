package apip.apipData;

public class Utxo {
    private String addr;
    private String txId;
    private int index;
    private double amount;
    private String issuer;
    private long birthTime;

    public long getBirthTime() {
        return birthTime;
    }

    public void setBirthTime(long birthTime) {
        this.birthTime = birthTime;
    }

    public static Utxo cashToUtxo(fch.fchData.Cash cash) {
        Utxo utxo = new Utxo();
        utxo.setAddr(cash.getOwner());
        utxo.setTxId(cash.getBirthTxId());
        utxo.setIndex(cash.getBirthIndex());
        utxo.setAmount((double) cash.getValue()/100000000);
//        utxo.setCd(cash.getCd());
        utxo.setIssuer(cash.getIssuer());
        utxo.setBirthTime(cash.getBirthTime());
        return utxo;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
