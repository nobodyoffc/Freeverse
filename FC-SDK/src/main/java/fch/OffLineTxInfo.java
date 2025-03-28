package fch;

import java.util.List;
import java.io.BufferedReader;
import java.util.ArrayList;

import fcData.FcEntity;
import fch.fchData.Cash;
import fch.fchData.P2SH;
import fch.fchData.SendTo;
import fch.fchData.RawTxForCs;
import utils.JsonUtils;

import javax.annotation.Nullable;

public class OffLineTxInfo extends FcEntity {
    private String sender;
    private double feeRate;
    private List<Cash> inputs;
    private List<SendTo> outputs;
    private String msg;
    private P2SH p2sh;

    public OffLineTxInfo() {
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
    }

    public static OffLineTxInfo fromString(String offLineTx) {
        OffLineTxInfo offLineTxInfo = new OffLineTxInfo();
        if(offLineTx !=null){
            try{
                offLineTxInfo = JsonUtils.fromJson(offLineTx, OffLineTxInfo.class);
            }catch (Exception e){
                List<RawTxForCs> rawTxForCsList = JsonUtils.listFromJson(offLineTx,RawTxForCs.class);
                offLineTxInfo = fromRawTxForCs(rawTxForCsList);
                return offLineTxInfo;
            }
        }
        return offLineTxInfo;
    }

    public double getFeeRate() {
        return feeRate;
    }
    public void setFeeRate(double feeRate) {
        this.feeRate = feeRate;
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

    public static OffLineTxInfo fromUserInput(BufferedReader br, @Nullable String sender)  {
        OffLineTxInfo offLineTxInfo = new OffLineTxInfo();
        if(sender==null)sender = Inputer.inputGoodFid(br,"Input the sender FID:");
        offLineTxInfo.setSender(sender);
        System.out.println("Input the cashes to be spent...");
        do {
            Cash cash = new Cash();
            cash.setBirthTxId(Inputer.inputString(br, "Input the birth tx id:"));
            cash.setBirthIndex(Inputer.inputInt(br, "Input the birth index:",0));
            Double amount = Inputer.inputDouble(br, "Input the value:");
            cash.setValue(FchUtils.coinToSatoshi(amount==null?0:amount));
            offLineTxInfo.getInputs().add(cash);
        } while (Inputer.askIfYes(br, "Input another input?"));

        do{
            SendTo sendTo = new SendTo();
            sendTo.setFid(Inputer.inputString(br, "Input the fid you paying to:"));
            sendTo.setAmount(Inputer.inputDouble(br, "Input the amount:"));
            offLineTxInfo.getOutputs().add(sendTo);
        } while (Inputer.askIfYes(br, "Input another output?"));

        offLineTxInfo.setMsg(Inputer.inputString(br, "Input the message of OP_RETURN:"));
        Double feeRate = Inputer.inputDouble(br, "Input the feeRate rate. Enter for default rate of 1 satoshi/byte:");
        offLineTxInfo.setFeeRate(feeRate ==null?TxCreator.DEFAULT_FEE_RATE:feeRate);

        return offLineTxInfo;
    }
    
    /**
     * Converts a list of RawTxForCs objects to an OffLineTxData object
     * @param rawTxForCsList List of RawTxForCs objects
     * @return A new OffLineTxData object
     */
    public static OffLineTxInfo fromRawTxForCs(List<RawTxForCs> rawTxForCsList) {
        if(rawTxForCsList == null || rawTxForCsList.isEmpty()) return null;

        OffLineTxInfo offLineTxInfo = new OffLineTxInfo();
        offLineTxInfo.setSender(rawTxForCsList.get(0).getAddress());
        
        // Process inputs
        List<Cash> inputs = new ArrayList<>();
        
        // Process outputs
        List<SendTo> outputs = new ArrayList<>();
        
        // Process message
        String msg = null;
        
        for (RawTxForCs rawTx : rawTxForCsList) {
            switch (rawTx.getDealType()) {
                case INPUT -> {
                    Cash cash = new Cash();
                    cash.setOwner(rawTx.getAddress());
                    cash.setValue(FchUtils.coinToSatoshi(rawTx.getAmount()));
                    cash.setBirthTxId(rawTx.getTxid());
                    cash.setBirthIndex(rawTx.getIndex());
                    inputs.add(cash);
                }
                case OUTPUT -> {
                    SendTo sendTo = new SendTo();
                    sendTo.setFid(rawTx.getAddress());
                    sendTo.setAmount(rawTx.getAmount());
                    outputs.add(sendTo);
                }
                case OP_RETURN -> msg = rawTx.getMsg();
            }
        }
        offLineTxInfo.setInputs(inputs);
        offLineTxInfo.setOutputs(outputs);
        offLineTxInfo.setMsg(msg);
        
        return offLineTxInfo;
    }
    
    /**
     * Converts this OffLineTxData object to a list of RawTxForCs objects
     * @return List of RawTxForCs objects
     */
    public List<RawTxForCs> toRawTxForCsList() {
        List<RawTxForCs> result = new ArrayList<>();
        
        // Convert inputs
        if (inputs != null) {
            for (int i = 0; i < inputs.size(); i++) {
                Cash cash = inputs.get(i);
                RawTxForCs rawTx = RawTxForCs.newInput(
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
                RawTxForCs rawTx = RawTxForCs.newOutput(
                    sendTo.getFid(),
                    sendTo.getAmount(),
                        j
                );
                result.add(rawTx);
            }
        }
        
        // Add OP_RETURN message if present
        if (msg != null && !msg.isEmpty()) {
            RawTxForCs rawTx = RawTxForCs.newOpReturn(msg, j);
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
}

