package data.fchData;

import core.crypto.KeyTools;
import core.fch.RawTxInfo;
import utils.FchUtils;

import java.util.ArrayList;
import java.util.List;

public class RawTxForCsV1 {
    private String address;
    private Double amount;
    private String txid;
    private int dealType;
    private Integer index;
    private Integer seq;
    private String msg;

    public static RawTxForCsV1 newInput(String address, Double amount, String txid, Integer index, Integer seq) {
        RawTxForCsV1 rawTxForCsV1 = new RawTxForCsV1();
        rawTxForCsV1.dealType = DealType.INPUT.getValue();
        
        rawTxForCsV1.address = address;
        rawTxForCsV1.amount = amount;
        rawTxForCsV1.txid = txid;
        rawTxForCsV1.index = index;
        rawTxForCsV1.seq = seq;
        return rawTxForCsV1;
    }

    public static RawTxForCsV1 newOutput(String address, Double amount, Integer seq) {
        RawTxForCsV1 rawTxForCsV1 = new RawTxForCsV1();
        rawTxForCsV1.dealType = DealType.OUTPUT.value;
        if(!KeyTools.isGoodFid(address))return null;
        rawTxForCsV1.address = address;
        rawTxForCsV1.amount = amount;
        rawTxForCsV1.seq = seq;
        return rawTxForCsV1;
    }

    public static RawTxForCsV1 newOpReturn(String msg, Integer seq) {
        if(msg==null||msg.equals(""))return null;
        RawTxForCsV1 rawTxForCsV1 = new RawTxForCsV1();
        rawTxForCsV1.dealType = DealType.OP_RETURN.getValue();
        rawTxForCsV1.msg = msg;
        rawTxForCsV1.seq = seq;
        return rawTxForCsV1;
    }

    public static List<RawTxForCsV1> fromV2(RawTxInfo rawTxInfo) {
        return makeRawTxForCsList(rawTxInfo.getSender(), rawTxInfo.getInputs(), rawTxInfo.getOutputs(), rawTxInfo.getOpReturn());
    }

    public static List<RawTxForCsV1> makeRawTxForCsList(String sender, List<Cash> cashList, List<SendTo> outputs, String opReturn) {
        List<RawTxForCsV1> rawTxForCsV1List = new ArrayList<>();

        if(!cashList.isEmpty())
            for(int i = 0; i < cashList.size(); i++){
                Cash cash = cashList.get(i);
                rawTxForCsV1List.add(newInput(sender, FchUtils.satoshiToCoin(cash.getValue()), cash.getBirthTxId(), cash.getBirthIndex(), i));
            }

        int j=0;
        for(; j < outputs.size(); j++){
            SendTo output = outputs.get(j);
            RawTxForCsV1 rawTxForCsV1 = newOutput(output.getFid(), output.getAmount(), j);
            if(rawTxForCsV1 !=null) rawTxForCsV1List.add(rawTxForCsV1);
        }
        if(opReturn !=null){
            RawTxForCsV1 rawTxForCsV1 = newOpReturn(opReturn, j);
            if(rawTxForCsV1 !=null) rawTxForCsV1List.add(rawTxForCsV1);
        }
        return rawTxForCsV1List;
    }

    public enum DealType {
        INPUT(1),
        OUTPUT(2),
        OP_RETURN(3);

        private int value;

        DealType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static DealType fromValue(int value) {
            for (DealType type : DealType.values()) {
                if (type.value == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid deal type value: " + value);
        }
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
    }

    public int getDealType() {
        return dealType;
    }

    public void setDealType(DealType dealType) {
        this.dealType = dealType.value;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
