package fchData;

import appTools.Inputer;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import static fch.Inputer.inputGoodFid;

public class SendTo {
    private String fid;
    private Double amount;

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
}
