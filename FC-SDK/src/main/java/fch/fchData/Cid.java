package fch.fchData;

import fcData.FcObject;
import fch.WeightMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Cid extends FcObject {
    private String cid;
    private String[] usedCids;
    private String pubKey;        //public key
    private String priKey;

    private Long balance;        //value of fch in satoshi
    private Long cash;        //Count of UTXO
    private Long income;        //total amount of fch received in satoshi
    private Long expend;        //total amount of fch pay in satoshi

    private Long cd;        //CoinDays
    private Long cdd;        //the total amount of coindays destroyed
    private Long reputation;
    private Long hot;
    private Long weight;

    private String master;
    private String guide;    //the address of the address which sent the first fch to this address
    private String noticeFee;
    private String[] homepages;

    private String btcAddr;    //the btc address
    private String ethAddr;    //the eth address
    private String ltcAddr;    //the ltc address
    private String dogeAddr;    //the doge address
    private String trxAddr;    //the trx address
    private String bchAddr;    //the bch address

    private Long birthHeight;    //the height where this address got its first fch
    private Long nameTime;
    private Long lastHeight;     //the height where this address info changed latest. If roll back happened, lastHei point to the lastHeight before fork.

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

    public String getCid() {
        return cid;
    }

    public void setCid(String cid) {
        this.cid = cid;
    }

    public String[] getUsedCids() {
        return usedCids;
    }

    public void setUsedCids(String[] usedCids) {
        this.usedCids = usedCids;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
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

    public String[] getHomepages() {
        return homepages;
    }

    public void setHomepages(String[] homepages) {
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