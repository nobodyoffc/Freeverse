package fch;

import java.util.List;
import java.util.Map;

public class CashToInputsResult {
    private List<Map<String, Object>> inputs;
    private long valueSum;
    private long cdSum;

    public List<Map<String, Object>> getInputs() {
        return inputs;
    }

    public void setInputs(List<Map<String, Object>> inputs) {
        this.inputs = inputs;
    }

    public long getValueSum() {
        return valueSum;
    }

    public void setValueSum(long valueSum) {
        this.valueSum = valueSum;
    }

    public long getCdSum() {
        return cdSum;
    }

    public void setCdSum(long cdSum) {
        this.cdSum = cdSum;
    }
}
