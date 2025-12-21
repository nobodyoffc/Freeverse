package server.reward;

import data.fchData.Cash;
import handlers.CashManager;
import data.fcData.Affair;
import data.fcData.DataSignTx;
import data.fcData.Op;
import core.fch.*;
import handlers.CashManager.SearchResult;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;
import data.feipData.Feip;
import utils.FchUtils;
import utils.JsonUtils;
import utils.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import config.Settings;

import java.util.*;

import static constants.Constants.*;
import static constants.Strings.REWARD;
import static constants.Strings.REWARD_PENDING_MAP;

public class AffairMaker {

    private RewardInfo rewardInfo;
    private String account;
    private RawTxInfo rawTxInfo;
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

        Feip feip = new Feip();
        feip.setType(FBBP);
        feip.setSn("1");
        feip.setVer("1");

        RewardData rewardData = new RewardData();

        rewardData.setOp(REWARD);
        rewardData.setSid(rewardInfo.getRewardId());
        feip.setData(rewardData);

        msg = gson.toJson(feip);

        long rewardT = rewardInfo.getRewardT();

        HashMap<String, Cash> sendToMap = makeSendToMap(rewardInfo);

        SearchResult<Cash> cashListReturn = CashManager.getValidCashes(account,rewardT, null, null, sendToMap.size(), msg.getBytes().length,null,esClient, null);

        if(cashListReturn.hasError()){
            log.debug(cashListReturn.getMessage());
            return null;
        }

        List<Cash> cashList = cashListReturn.getData();
        if (cashList==null || cashList.size()==0){
            return null;
        }

        pendingDust(sendToMap,jedisPool);

        addQualifiedPendingToPay(sendToMap);

        RawTxInfo rawTxInfo = new RawTxInfo();
        rawTxInfo.setSender(account);
        rawTxInfo.setOutputs(new ArrayList<>(sendToMap.values()));
        rawTxInfo.setOpReturn(msg);

        String rawTxStr = TxCreator.makeCsTxRequiredJsonV1(rawTxInfo,cashList);

        dataSignTx.setUnsignedTxCs(rawTxStr);
        dataSignTx.setAlg(ALG_SIGN_TX_BY_CRYPTO_SIGN);

        affairReward.setFid(account);
        affairReward.setOp(Op.SIGN);
        affairReward.setData(dataSignTx);

        return JsonUtils.toNiceJson(affairReward);
    }

    private void addQualifiedPendingToPay(HashMap<String, Cash> sendToMap) {
        for(String key: pendingMap.keySet()){
            double amount = utils.FchUtils.satoshiToCoin(pendingMap.get(key));
            Cash sendTo = new Cash();
            if (amount >= MinPayValue){
                if(sendToMap.get(key)!=null){
                    sendTo = sendToMap.get(key);
                    sendTo.setOwner(key);
                    sendTo.setAmount(sendTo.getAmount()+amount);
                }else {
                    sendTo.setOwner(key);
                    sendTo.setAmount(amount);
                }
                sendToMap.put(key,sendTo);
                pendingMap.remove(key);
            }
        }
    }

    public void pendingDust(HashMap<String, Cash> sendToMap, JedisPool jedisPool) {
        Iterator<Map.Entry<String, Cash>> iterator = sendToMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Cash> entry = iterator.next();
            Cash sendTo = entry.getValue();
            String fid = sendTo.getOwner();
            double amount = sendTo.getAmount();
            if (amount < MinPayValue) {
                addToPending(fid, utils.FchUtils.coinToSatoshi(amount),jedisPool);
                iterator.remove();
            }
        }
    }

    public static HashMap<String, Cash> makeSendToMap(RewardInfo rewardInfo) {
        HashMap<String, Cash> sendToMap= new HashMap<>();

        ArrayList<Payment> buildList = rewardInfo.getBuilderList();
        ArrayList<Payment> costList = rewardInfo.getCostList();
        ArrayList<Payment> consumeViaList = rewardInfo.getConsumeViaList();
        ArrayList<Payment> orderViaList = rewardInfo.getOrderViaList();

        makePayDetailListIntoSendToMap(sendToMap,orderViaList);
        makePayDetailListIntoSendToMap(sendToMap,consumeViaList);
        if(costList!=null && !costList.isEmpty())makePayDetailListIntoSendToMap(sendToMap,costList);
        makePayDetailListIntoSendToMap(sendToMap,buildList);

        return sendToMap;
    }

    public static void makePayDetailListIntoSendToMap(HashMap<String, Cash> sendToMap, ArrayList<Payment> payDetailList) {

        for(Payment payDetail:payDetailList){
            Cash sendTo = new Cash();
            String fid = payDetail.getFid();
            sendTo.setOwner(fid);
            long amount = payDetail.getAmount();
            if(sendToMap.get(fid)!=null){
                amount = amount+ sendToMap.get(fid).getValue();
                sendTo.setValue(amount);
            }else{
                sendTo.setValue(amount);
            }
            sendToMap.put(sendTo.getOwner(),sendTo);
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

    public RawTxInfo getDataForOffLineTx() {
        return rawTxInfo;
    }

    public void setDataForOffLineTx(RawTxInfo rawTxInfo) {
        this.rawTxInfo = rawTxInfo;
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
