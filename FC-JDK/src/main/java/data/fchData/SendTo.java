package data.fchData;

import ui.Inputer;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;


import static core.fch.Inputer.inputGoodFid;

public class SendTo {
    public static final Double MIN_AMOUNT = 0.0001;
    private String fid;
    private Double amount;

    private Long lockTime;  // Block height or timestamp for CLTV (null = no lock)

    // For multisig outputs (regular or CLTV)
    private List<String> pubkeys;  // Public keys for multisig
    private Integer m;  // Required signatures
    private Integer n;  // Total signers
    public SendTo() {}

    public SendTo(String to, Double amount) {
        this.fid = to;
        this.amount = amount;
    }

    public static List<SendTo> inputSendToList(BufferedReader br) {
        List<SendTo> sendToList = new ArrayList<>();
        while (true) {
            SendTo sendTo = new SendTo();
            String fid = inputGoodFid(br, "Input the recipient's fid. Enter to end:");
            if ("".equals(fid)) return sendToList;
            if ("d".equals(fid)) {
                System.out.println("Wrong input. Try again.");
                continue;
            }
            Double amount = Inputer.inputDouble(br, "Input the amount. Enter to end:");
            if (amount == null) return sendToList;

            sendTo.setFid(fid);
            sendTo.setAmount(amount);
            sendToList.add(sendTo);
        }
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Long getLockTime() {
        return lockTime;
    }

    public void setLockTime(Long lockTime) {
        this.lockTime = lockTime;
    }

    public List<String> getPubkeys() {
        return pubkeys;
    }

    public void setPubkeys(List<String> pubkeys) {
        this.pubkeys = pubkeys;
    }

    public Integer getM() {
        return m;
    }

    public void setM(Integer m) {
        this.m = m;
    }

    public Integer getN() {
        return n;
    }

    public void setN(Integer n) {
        this.n = n;
    }

    /**
     * Check if this output is a multisig output
     */
    public boolean isMultisig() {
        return pubkeys != null && !pubkeys.isEmpty() && m != null && n != null;
    }

    /**
     * Check if this output has a time lock
     */
    public boolean hasLockTime() {
        return lockTime != null && lockTime > 0;
    }
}
