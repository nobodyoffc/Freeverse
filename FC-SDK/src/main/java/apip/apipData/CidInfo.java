package apip.apipData;

import fch.fchData.Address;
import feip.feipData.Cid;
import fch.WeightMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CidInfo {
    private String fid;    //fch address
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
    private String trxAddr;    //the doge address

    private Long birthHeight;    //the height where this address got its first fch
    private Long nameTime;
    private Long lastHeight;     //the height where this address info changed latest. If roll back happened, lastHei point to the lastHeight before fork.

    public static List<CidInfo> mergeCidInfoList(List<Address> meetAddrList, List<Cid> meetCidList) {
        List<CidInfo> cidInfoList = new ArrayList<>();

        Map<String, Cid> cidMap = new HashMap<>();

        if (meetCidList != null && !meetCidList.isEmpty()) {
            for (Cid cid : meetCidList) {
                cidMap.put(cid.getId(), cid);
            }
        }

        for (Address addr : meetAddrList) {
            CidInfo cidInfo = new CidInfo();
            String id = addr.getId();
            setAddrToCidInfo(addr, cidInfo);
            Cid cid = cidMap.get(id);
            if (cid != null) {
                setCidToCidInfo(cid, cidInfo);
                cidMap.remove(id);
            }
            cidInfoList.add(cidInfo);
        }

        for (String id : cidMap.keySet()) {
            CidInfo cidInfo = new CidInfo();
            Cid cid = cidMap.get(id);
            setCidToCidInfo(cid, cidInfo);
            cidInfoList.add(cidInfo);
        }

        return cidInfoList;
    }

    public static CidInfo mergeCidInfo(Cid cid, Address addr) {

        CidInfo cidInfo = new CidInfo();
        if (addr != null) {
            setAddrToCidInfo(addr, cidInfo);
        }
        if (cid != null) {
            setCidToCidInfo(cid, cidInfo);
        }
        if(cidInfo.getFid()==null)return null;
        return cidInfo;
    }

    private static void setAddrToCidInfo(Address addr, CidInfo cidInfo) {
        cidInfo.setFid(addr.getId());
        cidInfo.setBalance(addr.getBalance());
        cidInfo.setBirthHeight(addr.getBirthHeight());
        cidInfo.setBtcAddr(addr.getBtcAddr());
        cidInfo.setCd(addr.getCd());
        cidInfo.setCdd(addr.getCdd());
        cidInfo.setWeight(addr.getWeight());

        cidInfo.setDogeAddr(addr.getDogeAddr());
        cidInfo.setEthAddr(addr.getEthAddr());
        cidInfo.setExpend(addr.getExpend());
        cidInfo.setGuide(addr.getGuide());
        cidInfo.setPubKey(addr.getPubKey());
        cidInfo.setIncome(addr.getIncome());
        cidInfo.setLastHeight(addr.getLastHeight());
        cidInfo.setTrxAddr(addr.getTrxAddr());
        cidInfo.setCash(addr.getCash());
        cidInfo.setLtcAddr(addr.getLtcAddr());
    }

    private static void setCidToCidInfo(Cid cid, CidInfo cidInfo) {
        cidInfo.setFid(cid.getId());
        cidInfo.setCid(cid.getCid());
        cidInfo.setHomepages(cid.getHomepages());
        cidInfo.setHot(cid.getHot());
        cidInfo.setMaster(cid.getMaster());
        cidInfo.setNameTime(cid.getNameTime());
        cidInfo.setNoticeFee(cid.getNoticeFee());
        cidInfo.setPriKey(cid.getPriKey());
        cidInfo.setReputation(cid.getReputation());
        cidInfo.setUsedCids(cid.getUsedCids());
    }

    public void reCalcWeight() {
        if(reputation==null)reputation=0L;
        if(cdd == null)cdd =0L;
        if(cd == null)cd = 0L;
        this.weight = WeightMethod.calcWeight(this.cd, this.cdd, this.reputation);
    }

//    public static List<Address> getAddrList(List<String> addrIdList) throws IOException {
//
//        MgetResponse<Address> result = esClient.mget(m -> m
//                .index(ADDRESS)
//                .ids(addrIdList), Address.class);
//        List<MultiGetResponseItem<Address>> itemList = result.docs();
//
//        List<Address> addrList = new ArrayList<>();
//
//        for(MultiGetResponseItem<Address> item : itemList){
//            if(!item.isFailure()) {
//                if(item.result().found())
//                    addrList.add(item.result().source());
//            }
//        }
//        return addrList;
//    }
//
//    public static List<Cid> getCidList(List<String> addrIdList) throws IOException {
//
//        MgetResponse<Cid> result = esClient.mget(m -> m
//                .index(CID)
//                .ids(addrIdList), Cid.class);
//        List<MultiGetResponseItem<Cid>> itemList = result.docs();
//
//        List<Cid> cidList = new ArrayList<>();
//
//        for(MultiGetResponseItem<Cid> item : itemList){
//            if(!item.isFailure()) {
//                if(item.result().found())
//                    cidList.add(item.result().source());
//            }
//        }
//        return cidList;
//    }

    public Long getWeight() {
        return weight;
    }

    public void setWeight(Long weight) {
        this.weight = weight;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
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
}