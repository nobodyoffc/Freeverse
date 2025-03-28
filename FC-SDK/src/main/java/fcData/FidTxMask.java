package fcData;

import apip.apipData.TxInfo;
import constants.Constants;
import fch.FchUtils;
import fch.fchData.CashMark;
import org.jetbrains.annotations.NotNull;
import utils.DateUtils;
import appTools.Shower;
import java.util.List;
import java.util.ArrayList;

import static constants.IndicesNames.OPRETURN;

public class FidTxMask {
    private String fid;
    private String txId;
    private Long time;
    private Long height;
    private Double balance;
    private Double fee;
    private String to;
    private String from;

    @NotNull
    public static FidTxMask fromTxInfo(String fid, TxInfo txInfo) {
        FidTxMask fidTxMask = new FidTxMask();
        long sum = 0;
        for(CashMark issuedCash : txInfo.getIssuedCashes()){
            if(issuedCash.getOwner().equals(fid))
                sum += issuedCash.getValue();
        }
        for(CashMark spentCash : txInfo.getSpentCashes()){
            if(spentCash.getOwner().equals(fid))
                sum -= spentCash.getValue();
        }

        fidTxMask.setBalance(FchUtils.satoshiToCoin(sum));
        if(txInfo.getFee()!=null)fidTxMask.setFee(FchUtils.satoshiToCoin(txInfo.getFee()));
        fidTxMask.setHeight(txInfo.getHeight());
        fidTxMask.setTime(txInfo.getBlockTime());
        fidTxMask.setTxId(txInfo.getId());
        fidTxMask.setFid(fid);
        if(sum>0){
            fidTxMask.setTo(fid);
            if(txInfo.getSpentCashes().size()>0) {
                CashMark cashMark = txInfo.getSpentCashes().get(0);
                if (cashMark != null) fidTxMask.setFrom(cashMark.getOwner());
                else fidTxMask.setFrom(Constants.COINBASE);
            }else fidTxMask.setFrom(Constants.COINBASE);
        }else{
            fidTxMask.setFrom(fid);
            CashMark cashMark = txInfo.getIssuedCashes().get(0);
            if(cashMark!=null && cashMark.getOwner().equals(OPRETURN))cashMark = txInfo.getIssuedCashes().get(1);
            if(cashMark!=null)fidTxMask.setTo(cashMark.getOwner());
            else fidTxMask.setTo(Constants.MINER);
        }
        return fidTxMask;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public Double getFee() {
        return fee;
    }

    public void setFee(Double fee) {
        this.fee = fee;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public static void showFidTxMaskList(List<FidTxMask> fidTxMaskList, String title, int totalDisplayed) {
        String[] fields = new String[]{"Time", "From", "To", "Balance(FCH)", "Fee(cash)"};
        int[] widths = new int[]{10, 15, 15, 12, 6};
        List<List<Object>> valueListList = new ArrayList<>();

        for (FidTxMask mask : fidTxMaskList) {
            List<Object> showList = new ArrayList<>();
            showList.add(DateUtils.longToTime(mask.getTime()*1000, "yyyy-MM-dd"));
            showList.add(mask.getFrom());
            showList.add(mask.getTo());
            showList.add(String.format("%.8f", mask.getBalance()));
            showList.add(String.format("%.2f", mask.getFee()*1000000));
            valueListList.add(showList);
        }
        Shower.showDataTable(title, fields, widths, valueListList, null);
    }
}
