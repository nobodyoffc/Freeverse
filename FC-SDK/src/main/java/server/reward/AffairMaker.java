package server.reward;

import fcData.Affair;
import fcData.DataSignTx;
import fcData.Op;
import fch.CashListReturn;
import fch.CryptoSign;
import fch.DataForOffLineTx;
import fch.Wallet;
import fch.fchData.Cash;
import fch.fchData.SendTo;
import feip.feipData.DataOnChain;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;
import javaTools.JsonTools;
import javaTools.NumberTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.util.*;

import static constants.Constants.*;
import static constants.Strings.REWARD;
import static constants.Strings.REWARD_PENDING_MAP;

public class AffairMaker {

    private RewardInfo rewardInfo;
    private String account;
    private DataForOffLineTx dataForOffLineTx;
    private List<Cash> meetCashList;
    private String msg;
    private final String sid;
    private final Gson gson = new Gson();

    private Affair affairReward = new Affair();
    private DataSignTx dataSignTx = new DataSignTx();

    private final ElasticsearchClient esClient;
    private final JedisPool jedisPool;

    private Map<String, Long> pendingMap = new HashMap<>();
    private static final Logger log = LoggerFactory.getLogger(AffairMaker.class);

    public AffairMaker(String sid,String account, RewardInfo rewardInfo, ElasticsearchClient esClient,JedisPool jedisPool) {
        this.sid = sid;
        this.rewardInfo = rewardInfo;
        this.account = account;
        this.esClient = esClient;
        this.jedisPool = jedisPool;

        getPendingMapFromRedis(jedisPool);
    }
    public Map<String, Long> getPendingMapFromRedis(JedisPool jedisPool) {
        try(Jedis jedis = jedisPool.getResource()) {
            Map<String, String> pendingStrMap = jedis.hgetAll(Settings.addSidBriefToName(sid,REWARD_PENDING_MAP));
            for (String key : pendingStrMap.keySet()) {
                Long amount = Long.parseLong(pendingStrMap.get(key));
                pendingMap.put(key, amount);
            }
        }
        return pendingMap;
    }

    public String makeAffair(){

        DataOnChain feip = new DataOnChain();
        feip.setType(FBBP);
        feip.setSn("1");
        feip.setVer("1");

        RewardData rewardData = new RewardData();

        rewardData.setOp(REWARD);
        rewardData.setSid(rewardInfo.getRewardId());
        feip.setData(rewardData);

        msg = gson.toJson(feip);

        long rewardT = rewardInfo.getRewardT();

        CashListReturn cashListReturn = Wallet.getCashListForPay(rewardT,account,esClient);

        if(cashListReturn.getCode()>0){
            log.debug(cashListReturn.getMsg());
            return null;
        }

        List<Cash> cashList = cashListReturn.getCashList();
        if (cashList==null || cashList.size()==0){
            return null;
        }

        HashMap<String, SendTo> sendToMap = makeSendToMap(rewardInfo);

        pendingDust(sendToMap,jedisPool);

        addQualifiedPendingToPay(sendToMap);

        DataForOffLineTx dataForOffLineTx = new DataForOffLineTx();
        dataForOffLineTx.setFromFid(account);
        dataForOffLineTx.setSendToList(new ArrayList<>(sendToMap.values()));
        dataForOffLineTx.setMsg(msg);

        String rawTxStr = CryptoSign.makeRawTxForCs(dataForOffLineTx,cashList);

        dataSignTx.setUnsignedTxCs(rawTxStr);
        dataSignTx.setAlg(ALG_SIGN_TX_BY_CRYPTO_SIGN);

        affairReward.setFid(account);
        affairReward.setOp(Op.sign);
        affairReward.setData(dataSignTx);

        return JsonTools.toNiceJson(affairReward);
    }

    private void addQualifiedPendingToPay(HashMap<String, SendTo> sendToMap) {
        for(String key: pendingMap.keySet()){
            double amount = (double) pendingMap.get(key) /FchToSatoshi;
            SendTo sendTo = new SendTo();
            if (amount >= MinPayValue){
                if(sendToMap.get(key)!=null){
                    sendTo = sendToMap.get(key);
                    sendTo.setFid(key);
                    sendTo.setAmount(sendTo.getAmount()+amount);
                }else {
                    sendTo.setFid(key);
                    sendTo.setAmount(amount);
                }
                sendToMap.put(key,sendTo);
                pendingMap.remove(key);
            }
        }
    }

    public void pendingDust(HashMap<String, SendTo> sendToMap,JedisPool jedisPool) {
        Iterator<Map.Entry<String, SendTo>> iterator = sendToMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SendTo> entry = iterator.next();
            String key = entry.getKey();
            SendTo sendTo = entry.getValue();
            String fid = sendTo.getFid();
            double amount = sendTo.getAmount();
            if (amount < MinPayValue) {
                addToPending(fid, (long) (amount*FchToSatoshi),jedisPool);
                iterator.remove();
            }
        }
    }

    public static HashMap<String,SendTo> makeSendToMap(RewardInfo rewardInfo) {
        HashMap<String,SendTo> sendToMap= new HashMap<>();

        ArrayList<Payment> buildList = rewardInfo.getBuilderList();
        ArrayList<Payment> costList = rewardInfo.getCostList();
        ArrayList<Payment> consumeViaList = rewardInfo.getConsumeViaList();
        ArrayList<Payment> orderViaList = rewardInfo.getOrderViaList();

        makePayDetailListIntoSendToMap(sendToMap,orderViaList);
        makePayDetailListIntoSendToMap(sendToMap,consumeViaList);
        makePayDetailListIntoSendToMap(sendToMap,costList);
        makePayDetailListIntoSendToMap(sendToMap,buildList);

        return sendToMap;
    }

    public static void makePayDetailListIntoSendToMap(HashMap<String,SendTo> sendToMap,ArrayList<Payment> payDetailList) {

        for(Payment payDetail:payDetailList){
            SendTo sendTo = new SendTo();
            String fid = payDetail.getFid();
            sendTo.setFid(fid);
            double amount = (double) payDetail.getAmount() /FchToSatoshi;
            if(sendToMap.get(fid)!=null){
                amount = amount+ sendToMap.get(fid).getAmount();
                sendTo.setAmount(NumberTools.roundDouble8(amount));
            }else{
                sendTo.setAmount(NumberTools.roundDouble8(amount));
            }
            sendToMap.put(sendTo.getFid(),sendTo);
        }
    }

    public  void addToPending(String fid, Long amount,JedisPool jedisPool) {
        Long pendingValue = 0L;
        try(Jedis jedis = jedisPool.getResource()) {
            pendingValue = Long.parseLong(jedis.hget(REWARD_PENDING_MAP, fid));
        }catch (Exception ignore){}
        if(pendingMap.get(fid)!=null) pendingValue += pendingMap.get(fid);
        pendingMap.put(fid,pendingValue+ amount);
    }

    public RewardInfo getRewardInfo() {
        return rewardInfo;
    }

    public void setRewardInfo(RewardInfo rewardInfo) {
        this.rewardInfo = rewardInfo;
    }

    public Affair getAffairReward() {
        return affairReward;
    }

    public void setAffairReward(Affair affairReward) {
        this.affairReward = affairReward;
    }

    public DataSignTx getDataSignTx() {
        return dataSignTx;
    }

    public void setDataSignTx(DataSignTx dataSignTx) {
        this.dataSignTx = dataSignTx;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public DataForOffLineTx getDataForOffLineTx() {
        return dataForOffLineTx;
    }

    public void setDataForOffLineTx(DataForOffLineTx dataForOffLineTx) {
        this.dataForOffLineTx = dataForOffLineTx;
    }

    public List<Cash> getMeetCashList() {
        return meetCashList;
    }

    public void setMeetCashList(List<Cash> meetCashList) {
        this.meetCashList = meetCashList;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Map<String, Long> getPendingMap() {
        return pendingMap;
    }

    public void setPendingMap(Map<String, Long> pendingMap) {
        this.pendingMap = pendingMap;
    }
}
