package fch.fchData;

import crypto.KeyTools;

public class RawTxForCs {
    private String address;
    private Double amount;
    private String txid;
    private DealType dealType;
    private Integer index;
    private Integer seq;
    private String msg;

    public static RawTxForCs newInput(String address, Double amount, String txid, Integer index, Integer seq) {
        RawTxForCs rawTxForCs = new RawTxForCs();
        rawTxForCs.dealType = DealType.INPUT;
        
        rawTxForCs.address = address;
        rawTxForCs.amount = amount;
        rawTxForCs.txid = txid;
        rawTxForCs.index = index;
        rawTxForCs.seq = seq;
        return rawTxForCs;
    }

    public static RawTxForCs newOutput(String address, Double amount, Integer seq) {
        RawTxForCs rawTxForCs = new RawTxForCs();
        rawTxForCs.dealType = DealType.OUTPUT;
        if(!KeyTools.isGoodFid(address))return null;
        rawTxForCs.address = address;
        rawTxForCs.amount = amount;
        rawTxForCs.seq = seq;
        return rawTxForCs;
    }

    public static RawTxForCs newOpReturn(String msg, Integer seq) {
        if(msg==null||msg.equals(""))return null;
        RawTxForCs rawTxForCs = new RawTxForCs();
        rawTxForCs.dealType = DealType.OP_RETURN;
        rawTxForCs.msg = msg;
        rawTxForCs.seq = seq;
        return rawTxForCs;
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

    public DealType getDealType() {
        return dealType;
    }

    public void setDealType(DealType dealType) {
        this.dealType = dealType;
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
