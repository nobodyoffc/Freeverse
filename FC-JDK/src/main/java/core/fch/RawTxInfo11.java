package core.fch;

import data.fcData.FcEntity;
import data.fchData.Cash;
import data.fchData.Multisig;
import data.fchData.RawTxForCsV1;
import utils.FchUtils;
import utils.JsonUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

public class RawTxInfo11 extends FcEntity {
    private String sender;
    private Double feeRate;
    private List<Cash> inputs;
    private List<Cash> outputs;
    private String msg;
    private String changeTo;
    private Long lockTime;
    private Long cd;
    private Multisig multisig;
    private String ver;

    public RawTxInfo11() {
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
    }

    public RawTxInfo11(String sender, List<Cash> cashList, List<Cash> sendToList, String msg, Long cd, Double feeRate, Multisig multisig, String ver) {
        super();
        this.sender = sender;
        this.setOutputs(sendToList);
        this.setMsg(msg);
        this.setCd(cd);
        this.setFeeRate(feeRate);
        this.setP2sh(multisig);
        this.setVer(ver);
        this.setInputs(Cash.makeCashListForPay(cashList));
    }

    public static RawTxInfo11 fromString(String offLineTx) {
        RawTxInfo11 rawTxInfo = new RawTxInfo11();
        if (offLineTx != null) {
            try {
                rawTxInfo = JsonUtils.fromJson(offLineTx, RawTxInfo11.class);
            } catch (Exception e) {
                List<RawTxForCsV1> rawTxForCsV1List = JsonUtils.listFromJson(offLineTx, RawTxForCsV1.class);
                rawTxInfo = fromRawTxForCs(rawTxForCsV1List);
                return rawTxInfo;
            }
        }
        return rawTxInfo;
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

    public List<Cash> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<Cash> outputs) {
        this.outputs = outputs;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Multisig getP2sh() {
        return multisig;
    }

    public void setP2sh(Multisig multisig) {
        this.multisig = multisig;
    }

    public static RawTxInfo11 fromUserInput(BufferedReader br, @Nullable String sender) {
        RawTxInfo11 rawTxInfo = new RawTxInfo11();
        if (sender == null) sender = Inputer.inputGoodFid(br, "Input the sender FID:");
        rawTxInfo.setSender(sender);
        System.out.println("Input the cashes to be spent...");
        do {
            Cash cash = new Cash();
            cash.setBirthTxId(Inputer.inputString(br, "Input the birth tx id:"));
            cash.setBirthIndex(Inputer.inputInt(br, "Input the birth index:", 0));
            Double amount = Inputer.inputDouble(br, "Input the value:");
            cash.setValue(FchUtils.coinToSatoshi(amount == null ? 0 : amount));
            rawTxInfo.getInputs().add(cash);
        } while (Inputer.askIfYes(br, "Input another input?"));

        do {
            Cash sendTo = new Cash();
            sendTo.setOwner(Inputer.inputString(br, "Input the fid you paying to:"));
            sendTo.setAmount(Inputer.inputDouble(br, "Input the amount:"));
            rawTxInfo.getOutputs().add(sendTo);
        } while (Inputer.askIfYes(br, "Input another output?"));

        rawTxInfo.setMsg(Inputer.inputString(br, "Input the message of OP_RETURN:"));
        Double feeRate = Inputer.inputDouble(br, "Input the feeRate rate. Enter for default rate of 1 satoshi/byte:");
        rawTxInfo.setFeeRate(feeRate == null ? TxCreator.DEFAULT_FEE_RATE : feeRate);

        return rawTxInfo;
    }

    /**
     * Converts a list of RawTxForCs objects to an OffLineTxData object
     *
     * @param rawTxForCsV1List List of RawTxForCs objects
     * @return A new OffLineTxData object
     */
    public static RawTxInfo11 fromRawTxForCs(List<RawTxForCsV1> rawTxForCsV1List) {
        if (rawTxForCsV1List == null || rawTxForCsV1List.isEmpty()) return null;

        RawTxInfo11 rawTxInfo = new RawTxInfo11();
        rawTxInfo.setSender(rawTxForCsV1List.get(0).getAddress());

        // Process inputs
        List<Cash> inputs = new ArrayList<>();

        // Process outputs
        List<Cash> outputs = new ArrayList<>();

        // Process message
        String msg = null;

        for (RawTxForCsV1 rawTx : rawTxForCsV1List) {
            switch (rawTx.getDealType()) {
                case 1 -> {
                    Cash cash = new Cash();
                    cash.setOwner(rawTx.getAddress());
                    cash.setValue(FchUtils.coinToSatoshi(rawTx.getAmount()));
                    cash.setBirthTxId(rawTx.getTxid());
                    cash.setBirthIndex(rawTx.getIndex());
                    inputs.add(cash);
                }
                case 2 -> {
                    Cash sendTo = new Cash();
                    sendTo.setOwner(rawTx.getAddress());
                    sendTo.setAmount(rawTx.getAmount());
                    outputs.add(sendTo);
                }
                case 3 -> msg = rawTx.getMsg();
            }
        }
        rawTxInfo.setInputs(inputs);
        rawTxInfo.setOutputs(outputs);
        rawTxInfo.setMsg(msg);

        return rawTxInfo;
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
                Cash sendTo = outputs.get(j);
                RawTxForCsV1 rawTx = RawTxForCsV1.newOutput(
                        sendTo.getOwner(),
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
