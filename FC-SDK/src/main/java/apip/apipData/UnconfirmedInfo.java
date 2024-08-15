package apip.apipData;

import java.util.Map;

public class UnconfirmedInfo {
    private String fid;
    private long net;
    private int spendCount;
    private long spendValue;
    private int incomeCount;
    private long incomeValue;
    private Map<String,Long> txValueMap;

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public long getNet() {
        return net;
    }

    public void setNet(long net) {
        this.net = net;
    }

    public int getSpendCount() {
        return spendCount;
    }

    public void setSpendCount(int spendCount) {
        this.spendCount = spendCount;
    }

    public long getSpendValue() {
        return spendValue;
    }

    public void setSpendValue(long spendValue) {
        this.spendValue = spendValue;
    }

    public int getIncomeCount() {
        return incomeCount;
    }

    public void setIncomeCount(int incomeCount) {
        this.incomeCount = incomeCount;
    }

    public long getIncomeValue() {
        return incomeValue;
    }

    public void setIncomeValue(long incomeValue) {
        this.incomeValue = incomeValue;
    }

    public Map<String, Long> getTxValueMap() {
        return txValueMap;
    }

    public void setTxValueMap(Map<String, Long> txValueMap) {
        this.txValueMap = txValueMap;
    }
}
