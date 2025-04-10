package fch;

import fcData.FcEntity;
import fch.fchData.Cash;
import fch.fchData.P2SH;
import fch.fchData.RawTxForCsV1;
import fch.fchData.SendTo;
import utils.FchUtils;
import utils.JsonUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

public class OffLineTxInfo extends FcEntity {
    private String sender;
    private Double feeRate;
    private List<Cash> inputs;
    private List<SendTo> outputs;
    private String msg;
    private String changeTo;
    private Long lockTime;
    private Long cd;
    private P2SH p2sh;
    private String ver;

    public OffLineTxInfo() {
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
    }

    public OffLineTxInfo(String sender, List<Cash> cashList, List<SendTo> sendToList, String msg, Long cd, Double feeRate, P2SH p2sh, String ver) {
        super();
        this.sender = sender;
        this.setOutputs(sendToList);
        this.setMsg(msg);
        this.setCd(cd);
        this.setFeeRate(feeRate);
        this.setP2sh(p2sh);
        this.setVer(ver);
        this.setInputs(Cash.makeCashListForPay(cashList));
    }

    public static OffLineTxInfo fromString(String offLineTx) {
        OffLineTxInfo offLineTxInfo = new OffLineTxInfo();
        if (offLineTx != null) {
            try {
                offLineTxInfo = JsonUtils.fromJson(offLineTx, OffLineTxInfo.class);
            } catch (Exception e) {
                List<RawTxForCsV1> rawTxForCsV1List = JsonUtils.listFromJson(offLineTx, RawTxForCsV1.class);
                offLineTxInfo = fromRawTxForCs(rawTxForCsV1List);
                return offLineTxInfo;
            }
        }
        return offLineTxInfo;
    }

    public Double getFeeRate() {
        return feeRate;
    }

    public List<Cash> getInputs() {
        return inputs;
    }

    public void setInputs(List<Cash> inputs) {
        this.inputs = inputs;
    }

    public List<SendTo> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<SendTo> outputs) {
        this.outputs = outputs;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public P2SH getP2sh() {
        return p2sh;
    }

    public void setP2sh(P2SH p2sh) {
        this.p2sh = p2sh;
    }

    public static OffLineTxInfo fromUserInput(BufferedReader br, @Nullable String sender) {
        OffLineTxInfo offLineTxInfo = new OffLineTxInfo();
        if (sender == null) sender = Inputer.inputGoodFid(br, "Input the sender FID:");
        offLineTxInfo.setSender(sender);
        System.out.println("Input the cashes to be spent...");
        do {
            Cash cash = new Cash();
            cash.setBirthTxId(Inputer.inputString(br, "Input the birth tx id:"));
            cash.setBirthIndex(Inputer.inputInt(br, "Input the birth index:", 0));
            Double amount = Inputer.inputDouble(br, "Input the value:");
            cash.setValue(utils.FchUtils.coinToSatoshi(amount == null ? 0 : amount));
            offLineTxInfo.getInputs().add(cash);
        } while (Inputer.askIfYes(br, "Input another input?"));

        do {
            SendTo sendTo = new SendTo();
            sendTo.setFid(Inputer.inputString(br, "Input the fid you paying to:"));
            sendTo.setAmount(Inputer.inputDouble(br, "Input the amount:"));
            offLineTxInfo.getOutputs().add(sendTo);
        } while (Inputer.askIfYes(br, "Input another output?"));

        offLineTxInfo.setMsg(Inputer.inputString(br, "Input the message of OP_RETURN:"));
        Double feeRate = Inputer.inputDouble(br, "Input the feeRate rate. Enter for default rate of 1 satoshi/byte:");
        offLineTxInfo.setFeeRate(feeRate == null ? TxCreator.DEFAULT_FEE_RATE : feeRate);

        return offLineTxInfo;
    }

    /**
     * Converts a list of RawTxForCs objects to an OffLineTxData object
     *
     * @param rawTxForCsV1List List of RawTxForCs objects
     * @return A new OffLineTxData object
     */
    public static OffLineTxInfo fromRawTxForCs(List<RawTxForCsV1> rawTxForCsV1List) {
        if (rawTxForCsV1List == null || rawTxForCsV1List.isEmpty()) return null;

        OffLineTxInfo offLineTxInfo = new OffLineTxInfo();
        offLineTxInfo.setSender(rawTxForCsV1List.get(0).getAddress());

        // Process inputs
        List<Cash> inputs = new ArrayList<>();

        // Process outputs
        List<SendTo> outputs = new ArrayList<>();

        // Process message
        String msg = null;

        for (RawTxForCsV1 rawTx : rawTxForCsV1List) {
            switch (rawTx.getDealType()) {
                case 1 -> {
                    Cash cash = new Cash();
                    cash.setOwner(rawTx.getAddress());
                    cash.setValue(utils.FchUtils.coinToSatoshi(rawTx.getAmount()));
                    cash.setBirthTxId(rawTx.getTxid());
                    cash.setBirthIndex(rawTx.getIndex());
                    inputs.add(cash);
                }
                case 2 -> {
                    SendTo sendTo = new SendTo();
                    sendTo.setFid(rawTx.getAddress());
                    sendTo.setAmount(rawTx.getAmount());
                    outputs.add(sendTo);
                }
                case 3 -> msg = rawTx.getMsg();
            }
        }
        offLineTxInfo.setInputs(inputs);
        offLineTxInfo.setOutputs(outputs);
        offLineTxInfo.setMsg(msg);

        return offLineTxInfo;
    }

    /**
     * Converts this OffLineTxData object to a list of RawTxForCs objects
     *
     * @return List of RawTxForCs objects
     */
    public List<RawTxForCsV1> toRawTxForCsList() {
        List<RawTxForCsV1> result = new ArrayList<>();

        // Convert inputs
        if (inputs != null) {
            for (int i = 0; i < inputs.size(); i++) {
                Cash cash = inputs.get(i);
                RawTxForCsV1 rawTx = RawTxForCsV1.newInput(
                        cash.getOwner(),
                        FchUtils.satoshiToCoin(cash.getValue()),
                        cash.getBirthTxId(),
                        cash.getBirthIndex(),
                        i
                );
                result.add(rawTx);
            }
        }

        // Convert outputs
        int j = 0;
        if (outputs != null) {
            for (j = 0; j < outputs.size(); j++) {
                SendTo sendTo = outputs.get(j);
                RawTxForCsV1 rawTx = RawTxForCsV1.newOutput(
                        sendTo.getFid(),
                        sendTo.getAmount(),
                        j
                );
                result.add(rawTx);
            }
        }

        // Add OP_RETURN message if present
        if (msg != null && !msg.isEmpty()) {
            RawTxForCsV1 rawTx = RawTxForCsV1.newOpReturn(msg, j);
            result.add(rawTx);
        }

        return result;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getVer() {
        return ver;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public void setFeeRate(Double feeRate) {
        this.feeRate = feeRate;
    }

    public Long getCd() {
        return cd;
    }

    public void setCd(Long cd) {
        this.cd = cd;
    }

    public String getChangeTo() {
        return changeTo;
    }

    public void setChangeTo(String changeTo) {
        this.changeTo = changeTo;
    }

    public Long getLockTime() {
        return lockTime;
    }

    public void setLockTime(Long lockTime) {
        this.lockTime = lockTime;
    }
}
