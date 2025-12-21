package data.fcData;

import constants.Constants;
import data.fchData.CashMask;
import data.fchData.Tx;
import org.jetbrains.annotations.NotNull;
import utils.DateUtils;
import ui.Shower;
import utils.FchUtils;

import java.util.List;
import java.util.ArrayList;

import static constants.IndicesNames.OPRETURN;

public class FidTxMask extends FcEntity{
    private String fid;
    private Long time;
    private Long height;
    private Integer index;
    private Double balance;
    private Double fee;
    private String to;
    private String from;
    private Integer in;
    private Integer out;

    @NotNull
    public static FidTxMask fromTxInfo(String fid, Tx txInfo) {
        FidTxMask fidTxMask = new FidTxMask();
        long sum = 0;
        for(CashMask issuedCash : txInfo.getIssuedCashes()){
            if(issuedCash.getOwner().equals(fid))
                sum += issuedCash.getValue();
        }
        for(CashMask spentCash : txInfo.getSpentCashes()){
            if(spentCash.getOwner().equals(fid))
                sum -= spentCash.getValue();
        }

        fidTxMask.setBalance(utils.FchUtils.satoshiToCoin(sum));
        if(txInfo.getFee()!=null)fidTxMask.setFee(FchUtils.satoshiToCoin(txInfo.getFee()));
        fidTxMask.setHeight(txInfo.getHeight());
        fidTxMask.setIndex(txInfo.getTxIndex());
        fidTxMask.setTime(txInfo.getBlockTime());
        fidTxMask.setId(txInfo.getId());
        fidTxMask.setIn(txInfo.getInCount());
        fidTxMask.setOut(txInfo.getOutCount());
        fidTxMask.setFid(fid);
        if(sum>0){
            fidTxMask.setTo(fid);
            if(txInfo.getSpentCashes().size()>0) {
                CashMask cashMask = txInfo.getSpentCashes().get(0);
                if (cashMask != null) fidTxMask.setFrom(cashMask.getOwner());
                else fidTxMask.setFrom(Constants.COINBASE);
            }else fidTxMask.setFrom(Constants.COINBASE);
        }else{
            fidTxMask.setFrom(fid);
            CashMask cashMask = txInfo.getIssuedCashes().get(0);
            if(cashMask !=null && cashMask.getOwner().equals(OPRETURN)) cashMask = txInfo.getIssuedCashes().get(1);
            if(cashMask !=null)fidTxMask.setTo(cashMask.getOwner());
            else fidTxMask.setTo(Constants.MINER);
        }
        return fidTxMask;
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

    public Integer getIn() {
        return in;
    }

    public void setIn(Integer in) {
        this.in = in;
    }

    public Integer getOut() {
        return out;
    }

    public void setOut(Integer out) {
        this.out = out;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
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
        Shower.showOrChooseList(title, fields, widths, valueListList, null);
    }
}
