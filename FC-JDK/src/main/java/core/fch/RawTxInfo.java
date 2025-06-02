package core.fch;

import core.crypto.Hash;
import data.fcData.CidInfo;
import data.fcData.FcEntity;
import data.fchData.Cash;
import data.fchData.Multisign;
import data.fchData.RawTxForCsV1;
import data.fchData.SendTo;
import utils.FchUtils;
import utils.Hex;
import utils.JsonUtils;
import utils.ObjectUtils;
import com.google.gson.Gson;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RawTxInfo extends FcEntity {
    private String sender;
    private Double feeRate;
    private List<Cash> inputs;
    private List<SendTo> outputs;
    private String opReturn;
    private String changeTo;
    private Long lockTime;
    private Long cd;
    private Multisign multisign;
    private String ver;
    private CidInfo senderInfo;
    private Long cdd;
    private Map<String, List<String>> fidSigMap;

    public RawTxInfo() {
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();

    }

    public RawTxInfo(byte[] rawTx, Multisign multisign, List<Cash> inputs) {
//        this.rawTx = rawTx;
        this.multisign = multisign;
        this.inputs = inputs;
        this.id = Hex.toHex(Hash.sha256x2(rawTx));
    }

    public RawTxInfo(byte[] rawTx, RawTxInfo rawTxInfo) {
//        this.rawTx = rawTx;
        this.multisign = rawTxInfo.getMultisign();
        this.inputs = rawTxInfo.getInputs();
        this.id = Hex.toHex(Hash.sha256x2(rawTx));
        this.feeRate = rawTxInfo.getFeeRate();
        this.changeTo = rawTxInfo.getChangeTo();
        this.ver = rawTxInfo.getVer();
        this.lockTime = rawTxInfo.getLockTime();
    }

    public RawTxInfo(String multisignJson, String cashListJson) {
        this.multisign = new Gson().fromJson(multisignJson, Multisign.class);
        this.inputs = ObjectUtils.objectToList(cashListJson,Cash.class);//DataGetter.getCashList(cashList);
    }


    public RawTxInfo(String sender, List<Cash> cashList, List<SendTo> sendToList, String opReturn, Long cd, Double feeRate, Multisign multisign, String ver) {
        super();
        this.sender = sender;
        this.setOutputs(sendToList);
        this.setOpReturn(opReturn);
        this.setCd(cd);
        this.setFeeRate(feeRate);
        this.setMultisign(multisign);
        this.setVer(ver);
        this.setInputs(Cash.makeCashListForPay(cashList));
    }

    @Override
    public String toJson() {
        this.senderInfo = null;
        return super.toJson();
    }

    @Override
    public String toNiceJson() {
        this.senderInfo = null;
        return super.toNiceJson();
    }

    public String toJsonWithSenderInfo() {
        return super.toJson();
    }

    public static RawTxInfo fromString(String offLineTx) {
        RawTxInfo rawTxInfo = new RawTxInfo();
        if (offLineTx != null) {
            try {
                rawTxInfo = JsonUtils.fromJson(offLineTx, RawTxInfo.class);
            } catch (Exception e) {
                List<RawTxForCsV1> rawTxForCsV1List = JsonUtils.listFromJson(offLineTx, RawTxForCsV1.class);
                rawTxInfo = fromRawTxForCs(rawTxForCsV1List);
                return rawTxInfo;
            }
        }
        return rawTxInfo;
    }


//    public static RawTxInfo createMultisignTx(RawTxInfo rawTxInfo) {
//        Transaction tx = TxCreator.createUnsignedTx(rawTxInfo, FchMainNetwork.get());
//        if(tx ==null)return null;
//        byte[] rawTx = tx.bitcoinSerialize();
//
//        return new RawTxInfo(rawTx, rawTxInfo);
//    }

    public static Transaction createMultisignTx(RawTxInfo rawTxInfo, Multisign multisign, MainNetParams mainNetwork) {
        rawTxInfo.setMultisign(multisign);
        return TxCreator.createUnsignedTx(rawTxInfo, mainNetwork);
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

    public String getOpReturn() {
        return opReturn;
    }

    public void setOpReturn(String opReturn) {
        this.opReturn = opReturn;
    }

    public Multisign getMultisign() {
        return multisign;
    }

    public void setMultisign(Multisign multisign) {
        this.multisign = multisign;
    }

    public static RawTxInfo fromUserInput(BufferedReader br, @Nullable String sender) {
        RawTxInfo rawTxInfo = new RawTxInfo();
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
            SendTo sendTo = new SendTo();
            sendTo.setFid(Inputer.inputString(br, "Input the fid you paying to:"));
            sendTo.setAmount(Inputer.inputDouble(br, "Input the amount:"));
            rawTxInfo.getOutputs().add(sendTo);
        } while (Inputer.askIfYes(br, "Input another output?"));

        rawTxInfo.setOpReturn(Inputer.inputString(br, "Input the message of OP_RETURN:"));
        Double feeRate = Inputer.inputDouble(br, "Input the feeRate rate. Enter for default rate of 1 satoshi/byte:");
        rawTxInfo.setFeeRate(feeRate == null ? TxCreator.DEFAULT_FEE_RATE : feeRate);

        return rawTxInfo;
    }

    public static RawTxInfo fromRawTxForCs(String csTxJson)  {
        List<RawTxForCsV1> csTxList = JsonUtils.listFromJson(csTxJson, RawTxForCsV1.class);
        if(csTxList.isEmpty())return null;
        return RawTxInfo.fromRawTxForCs(csTxList);
    }

    /**
     * Converts a list of RawTxForCs objects to an OffLineTxData object
     *
     * @param rawTxForCsV1List List of RawTxForCs objects
     * @return A new OffLineTxData object
     */
    public static RawTxInfo fromRawTxForCs(List<RawTxForCsV1> rawTxForCsV1List) {
        if (rawTxForCsV1List == null || rawTxForCsV1List.isEmpty()) return null;

        RawTxInfo rawTxInfo = new RawTxInfo();
        rawTxInfo.setSender(rawTxForCsV1List.get(0).getAddress());

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
                    cash.setValue(FchUtils.coinToSatoshi(rawTx.getAmount()));
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
        rawTxInfo.setInputs(inputs);
        rawTxInfo.setOutputs(outputs);
        rawTxInfo.setOpReturn(msg);

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
        if (opReturn != null && !opReturn.isEmpty()) {
            RawTxForCsV1 rawTx = RawTxForCsV1.newOpReturn(opReturn, j);
            result.add(rawTx);
        }

        return result;
    }

    public byte[] getRawTx() {
        Transaction tx = TxCreator.createUnsignedTx(this, FchMainNetwork.MAINNETWORK);
        if(tx==null)return null;
        return tx.bitcoinSerialize();
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

    public CidInfo getSenderInfo() {
        return senderInfo;
    }

    public void setSenderInfo(CidInfo senderInfo) {
        this.senderInfo = senderInfo;
    }

    public Long getCdd() {
        return cdd;
    }

    public void setCdd(Long cdd) {
        this.cdd = cdd;
    }

    public Map<String, List<String>> getFidSigMap() {
        return fidSigMap;
    }

    public void setFidSigMap(Map<String, List<String>> fidSigMap) {
        this.fidSigMap = fidSigMap;
    }

}
