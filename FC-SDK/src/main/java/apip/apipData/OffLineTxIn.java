package apip.apipData;

import fch.fchData.SendTo;

public class OffLineTxIn {
    private String fid;
    private String amount;
    private String cd;
    private String msg;
    private SendTo[] outputs;

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getCd() {
        return cd;
    }

    public void setCd(String cd) {
        this.cd = cd;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public SendTo[] getOutputs() {
        return outputs;
    }

    public void setOutputs(SendTo[] outputs) {
        this.outputs = outputs;
    }
}
