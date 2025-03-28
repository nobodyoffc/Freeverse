package fch.fchData;

import appTools.Shower;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fcData.FcSubject;
import fch.WeightMethod;

import java.io.BufferedReader;
import java.util.List;

import static constants.FieldNames.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Cid extends FcSubject {
    private String priKey;

    protected Long balance;        //value of fch in satoshi
    protected Long cash;        //Count of UTXO
    protected Long income;        //total amount of fch received in satoshi
    protected Long expend;        //total amount of fch pay in satoshi

    protected Long cd;        //CoinDays
    protected Long cdd;        //the total amount of coindays destroyed
    protected Long reputation;
    protected Long hot;
    protected Long weight;

    protected String master;
    protected String guide;    //the address of the address which sent the first fch to this address
    protected String noticeFee;
    protected List<String> homepages;

    protected String btcAddr;    //the btc address
    protected String ethAddr;    //the eth address
    protected String ltcAddr;    //the ltc address
    protected String dogeAddr;    //the doge address
    protected String trxAddr;    //the trx address
    protected String bchAddr;    //the bch address

    protected Long birthHeight;    //the height where this address got its first fch
    protected Long nameTime;
    protected Long lastHeight;     //the height where this address info changed latest. If roll back happened, lastHei point to the lastHeight before fork.

    public static String[] DefaultShowField = {
        ID,
        CID,
        CASH,
        BALANCE,
        CD,
        LAST_HEIGHT
    };
    public static Integer[] DefaultShowWidth = {
        ID_DEFAULT_SHOW_SIZE,
        TEXT_SHORT_DEFAULT_SHOW_SIZE,
        AMOUNT_DEFAULT_SHOW_SIZE,
        AMOUNT_DEFAULT_SHOW_SIZE,
        CD_DEFAULT_SHOW_SIZE,
        TIME_DEFAULT_SHOW_SIZE
    };

    public static List<Cid> showCidList(String title, List<Cid> list, Integer maxFieldWidth, boolean choose, BufferedReader br) {
        return Shower.showOrChooseListInPages("FID Info", list, br,choose, Cid.class);
    }
        public void reCalcWeight() {
        if(reputation==null)reputation=0L;
        if(cdd == null)cdd =0L;
        if(cd == null)cd = 0L;
        this.weight = WeightMethod.calcWeight(this.cd, this.cdd, this.reputation);
    }

    public Long getWeight() {
        return weight;
    }

    public void setWeight(Long weight) {
        this.weight = weight;
    }

    public String getPriKey() {
        return priKey;
    }

    public void setPriKey(String priKey) {
        this.priKey = priKey;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public Long getCash() {
        return cash;
    }

    public void setCash(Long cash) {
        this.cash = cash;
    }

    public Long getIncome() {
        return income;
    }

    public void setIncome(Long income) {
        this.income = income;
    }

    public Long getExpend() {
        return expend;
    }

    public void setExpend(Long expend) {
        this.expend = expend;
    }

    public Long getCd() {
        return cd;
    }

    public void setCd(Long cd) {
        this.cd = cd;
    }

    public Long getCdd() {
        return cdd;
    }

    public void setCdd(Long cdd) {
        this.cdd = cdd;
    }

    public Long getReputation() {
        return reputation;
    }

    public void setReputation(Long reputation) {
        this.reputation = reputation;
    }

    public Long getHot() {
        return hot;
    }

    public void setHot(Long hot) {
        this.hot = hot;
    }

    public String getMaster() {
        return master;
    }

    public void setMaster(String master) {
        this.master = master;
    }

    public String getGuide() {
        return guide;
    }

    public void setGuide(String guide) {
        this.guide = guide;
    }

    public String getNoticeFee() {
        return noticeFee;
    }

    public void setNoticeFee(String noticeFee) {
        this.noticeFee = noticeFee;
    }

    public List<String> getHomepages() {
        return homepages;
    }

    public void setHomepages(List<String> homepages) {
        this.homepages = homepages;
    }

    public String getBtcAddr() {
        return btcAddr;
    }

    public void setBtcAddr(String btcAddr) {
        this.btcAddr = btcAddr;
    }

    public String getEthAddr() {
        return ethAddr;
    }

    public void setEthAddr(String ethAddr) {
        this.ethAddr = ethAddr;
    }

    public String getLtcAddr() {
        return ltcAddr;
    }

    public void setLtcAddr(String ltcAddr) {
        this.ltcAddr = ltcAddr;
    }

    public String getDogeAddr() {
        return dogeAddr;
    }

    public void setDogeAddr(String dogeAddr) {
        this.dogeAddr = dogeAddr;
    }

    public String getTrxAddr() {
        return trxAddr;
    }

    public void setTrxAddr(String trxAddr) {
        this.trxAddr = trxAddr;
    }

    public Long getBirthHeight() {
        return birthHeight;
    }

    public void setBirthHeight(Long birthHeight) {
        this.birthHeight = birthHeight;
    }

    public Long getNameTime() {
        return nameTime;
    }

    public void setNameTime(Long nameTime) {
        this.nameTime = nameTime;
    }

    public Long getLastHeight() {
        return lastHeight;
    }

    public void setLastHeight(Long lastHeight) {
        this.lastHeight = lastHeight;
    }

    public String getBchAddr() {
        return bchAddr;
    }

    public void setBchAddr(String bchAddr) {
        this.bchAddr = bchAddr;
    }
}