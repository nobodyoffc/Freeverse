package apip.apipData;

import fch.fchData.CashMark;
import fch.fchData.Tx;
import fch.fchData.TxHas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.DateTools;
import fch.ParseTools;
import appTools.Shower;

public class TxInfo {
    private String txId;        //txid,hash of tx
    private Integer version;        //version
    private Long lockTime;    //locktime
    private Long blockTime;        //blockTime
    private String blockId;        //block ID, hash of block head
    private Integer txIndex;        //the index of this tx in the block
    private String coinbase;    //string of the coinbase script
    private Integer outCount;        //number of outputs
    private Integer inCount;        //number of inputs
    private Long height;        //block height of the block

    private String opReBrief;    //Former 30 bytes of OP_RETURN data in String.

    //calculated
    private Long inValueT;        //total amount of inputs
    private Long outValueT;        //total amount of outputs
    private Long fee;        //tx fee

    private Long cdd;

    private ArrayList<CashMark> spentCashes;
    private ArrayList<CashMark> issuedCashes;

    public static List<TxInfo> mergeTxAndTxHas(List<Tx> txList, List<TxHas> txHasList) {
        List<TxInfo> result = new ArrayList<>();
        Map<String, Tx> txMap = new HashMap<>();

        for (Tx tx : txList) {
            txMap.put(tx.getId(), tx);
        }

        for (TxHas txHas : txHasList) {
            Tx tx = txMap.get(txHas.getId());

            if (tx != null) {
                TxInfo txInfo = new TxInfo();
                txInfo.setTxId(tx.getId());
                txInfo.setHeight(txHas.getHeight());
                txInfo.setSpentCashes(txHas.getInMarks());
                txInfo.setIssuedCashes(txHas.getOutMarks());
                txInfo.setVersion(tx.getVersion());
                txInfo.setLockTime(tx.getLockTime());
                txInfo.setBlockTime(tx.getBlockTime());
                txInfo.setBlockId(tx.getBlockId());
                txInfo.setTxIndex(tx.getTxIndex());
                txInfo.setCoinbase(tx.getCoinbase());
                txInfo.setOutCount(tx.getOutCount());
                txInfo.setInCount(tx.getInCount());
                txInfo.setOpReBrief(tx.getOpReBrief());
                txInfo.setInValueT(tx.getInValueT());
                txInfo.setOutValueT(tx.getOutValueT());
                txInfo.setFee(tx.getFee());
                txInfo.setCdd(tx.getCdd());
                result.add(txInfo);
            }
        }

        return result;
    }

    public static void showTxInfoList(List<TxInfo> txInfoList, String title, int totalDisplayed) {
        String[] fields = new String[]{"Time", "TxID", "Inputs", "Outputs", "Total Out(FCH)"};
        int[] widths = new int[]{10, 15, 8, 8, 16};
        List<List<Object>> valueListList = new ArrayList<>();

        for (TxInfo txInfo : txInfoList) {
            List<Object> showList = new ArrayList<>();
            showList.add(DateTools.longToTime(txInfo.getBlockTime()*1000, "yyyy-MM-dd"));
            showList.add(txInfo.getTxId());
            showList.add(txInfo.getInCount());
            showList.add(txInfo.getOutCount());
            showList.add(String.valueOf(ParseTools.satoshiToCoin(txInfo.getOutValueT())));
            valueListList.add(showList);
        }
        Shower.showDataTable(title, fields, widths, valueListList, totalDisplayed, true);
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getLockTime() {
        return lockTime;
    }

    public void setLockTime(Long lockTime) {
        this.lockTime = lockTime;
    }

    public Long getBlockTime() {
        return blockTime;
    }

    public void setBlockTime(Long blockTime) {
        this.blockTime = blockTime;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public Integer getTxIndex() {
        return txIndex;
    }

    public void setTxIndex(Integer txIndex) {
        this.txIndex = txIndex;
    }

    public String getCoinbase() {
        return coinbase;
    }

    public void setCoinbase(String coinbase) {
        this.coinbase = coinbase;
    }

    public Integer getOutCount() {
        return outCount;
    }

    public void setOutCount(Integer outCount) {
        this.outCount = outCount;
    }

    public Integer getInCount() {
        return inCount;
    }

    public void setInCount(Integer inCount) {
        this.inCount = inCount;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }

    public String getOpReBrief() {
        return opReBrief;
    }

    public void setOpReBrief(String opReBrief) {
        this.opReBrief = opReBrief;
    }

    public Long getInValueT() {
        return inValueT;
    }

    public void setInValueT(Long inValueT) {
        this.inValueT = inValueT;
    }

    public Long getOutValueT() {
        return outValueT;
    }

    public void setOutValueT(Long outValueT) {
        this.outValueT = outValueT;
    }

    public Long getFee() {
        return fee;
    }

    public void setFee(Long fee) {
        this.fee = fee;
    }

    public Long getCdd() {
        return cdd;
    }

    public void setCdd(Long cdd) {
        this.cdd = cdd;
    }

    public ArrayList<CashMark> getSpentCashes() {
        return spentCashes;
    }

    public void setSpentCashes(ArrayList<CashMark> spentCashes) {
        this.spentCashes = spentCashes;
    }

    public ArrayList<CashMark> getIssuedCashes() {
        return issuedCashes;
    }

    public void setIssuedCashes(ArrayList<CashMark> issuedCashes) {
        this.issuedCashes = issuedCashes;
    }
}
