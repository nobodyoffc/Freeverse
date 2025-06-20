package webhook;

import data.apipData.WebhookPushBody;
import data.fchData.Cash;
import data.fchData.OpReturn;
import handlers.WebhookManager;
import server.ApipApiNames;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.google.gson.Gson;
import constants.*;

import utils.FchUtils;
import utils.JsonUtils;
import utils.ObjectUtils;
import utils.http.HttpUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import config.Settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static data.fcData.FcSession.sign;
import static constants.FieldNames.IDS;
import static constants.Strings.*;

public class Pusher implements Runnable{

    private static final Logger log = LoggerFactory.getLogger(Pusher.class);
    private volatile AtomicBoolean running = new AtomicBoolean(true);
    private final String listenDir;
    private final ElasticsearchClient esClient;
    private Map<String,Map<String, WebhookManager.WebhookRequestBody>> methodFidEndpointInfoMapMap = new HashMap<>();
    private final String sid;

    public Pusher(String sid,String listenDir, ElasticsearchClient esClient) {
        this.esClient = esClient;
        this.listenDir = listenDir;
        this.sid = sid;
    }

    @Override
    public void run() {

        try(Jedis jedis = new Jedis()) {

            long bestHeight;
            try {
                bestHeight = Long.parseLong(jedis.get(BEST_HEIGHT));
            }catch (Exception e){
                bestHeight=0;
            }
            long sinceHeight = bestHeight;
            checkSubscriptions(jedis);

            while (running.get()) {
                jedis.select(Constants.RedisDb4Webhook);
                readMethodFidWebhookInfoMapMapFromRedis(jedis);
                utils.FchUtils.waitForChangeInDirectory(listenDir, running);
                TimeUnit.SECONDS.sleep(3);
                jedis.select(0);
                bestHeight = Long.parseLong(jedis.get(BEST_HEIGHT));
                if(sinceHeight <bestHeight) {
                    pushWebhooks(methodFidEndpointInfoMapMap, sinceHeight);
                    sinceHeight =bestHeight;
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            log.debug("Run pusher thread wrong.");
        }
    }

    private void checkSubscriptions(Jedis jedis) {
        List<WebhookManager.WebhookRequestBody> webhookInfoList;
        jedis.select(Constants.RedisDb4Webhook);
        Map<String, String> newCashByFidsSubscriptionMap = jedis.hgetAll(Settings.addSidBriefToName(sid, ApipApiNames.NEW_CASH_BY_FIDS));
        if(newCashByFidsSubscriptionMap ==null) {
            webhookInfoList = getWebhookInfoListFromEs(esClient);
            makeMethodFidEndpointMapMap(webhookInfoList);
            setWebhookSubscriptionIntoRedis(methodFidEndpointInfoMapMap, jedis);
        }
        //methodFidEndpointInfoMapMap structure is <method:<userId:webhookInfo>>
//        jedis.select(Constants.RedisDb4Webhook);
//        jedis.flushDB();
//        setWebhookSubscriptionIntoRedis(methodFidEndpointInfoMapMap,jedis);
    }

    private void readMethodFidWebhookInfoMapMapFromRedis(Jedis jedis) {
        Gson gson = new Gson();
        methodFidEndpointInfoMapMap = new HashMap<>();
        //Method: newCashByFids
        Map<String, String> newCashByFidsHookInfoStrMap = jedis.hgetAll(Settings.addSidBriefToName(sid, ApipApiNames.NEW_CASH_BY_FIDS));
        if(newCashByFidsHookInfoStrMap==null||newCashByFidsHookInfoStrMap.isEmpty())return;
        Map<String, WebhookManager.WebhookRequestBody> newCashByFidsHookInfoMap = new HashMap<>();
        for(String owner: newCashByFidsHookInfoStrMap.keySet()){
            String webhookInfoStr = newCashByFidsHookInfoStrMap.get(owner);
            newCashByFidsHookInfoMap.put(owner,gson.fromJson(webhookInfoStr, WebhookManager.WebhookRequestBody.class));
        }
        methodFidEndpointInfoMapMap.put(ApipApiNames.NEW_CASH_BY_FIDS,newCashByFidsHookInfoMap);
        //More method:
    }

    private void setWebhookSubscriptionIntoRedis(Map<String, Map<String, WebhookManager.WebhookRequestBody>> methodFidEndpointInfoMapMap, Jedis jedis) {
        if(methodFidEndpointInfoMapMap==null||methodFidEndpointInfoMapMap.size()==0)return;
        Gson gson = new Gson();
        for(String method:methodFidEndpointInfoMapMap.keySet()){
            Map<String, WebhookManager.WebhookRequestBody> ownerWebhookInfoMap = methodFidEndpointInfoMapMap.get(method);
            for(String userId:ownerWebhookInfoMap.keySet()){
                WebhookManager.WebhookRequestBody webhookInfo = ownerWebhookInfoMap.get(userId);
                jedis.hset(method,userId,gson.toJson(webhookInfo));
            }
        }
    }

    private void makeMethodFidEndpointMapMap(List<WebhookManager.WebhookRequestBody> webhookInfoList) {
        if(webhookInfoList==null || webhookInfoList.size()==0)return;
        Map<String, WebhookManager.WebhookRequestBody> newCashByFidsWebhookInfoMap = new HashMap<>();
        for (WebhookManager.WebhookRequestBody webhookInfo : webhookInfoList) {
            switch (webhookInfo.getMethod()) {
                case ApipApiNames.NEW_CASH_BY_FIDS -> newCashByFidsWebhookInfoMap.put(webhookInfo.getUserId(), webhookInfo);
            }
        }
        methodFidEndpointInfoMapMap.put(ApipApiNames.NEW_CASH_BY_FIDS, newCashByFidsWebhookInfoMap);
    }

    private List<WebhookManager.WebhookRequestBody> getWebhookInfoListFromEs(ElasticsearchClient esClient) {
        try {
            return EsUtils.getAllList(esClient, Settings.addSidBriefToName(sid,IndicesNames.WEBHOOK), Strings.HOOK_USER_ID,SortOrder.Asc, WebhookManager.WebhookRequestBody.class);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Read webhook info failed.");
            return null;
        }
    }

    private void pushWebhooks(Map<String, Map<String, WebhookManager.WebhookRequestBody>> methodFidWatchedFidsMapMap, long sinceHeight) {

        for(String method: methodFidWatchedFidsMapMap.keySet()){
            switch (method){
                case ApipApiNames.NEW_CASH_BY_FIDS -> putNewCashByFids(methodFidWatchedFidsMapMap.get(method),sinceHeight);
                //TODO untested
                case ApipApiNames.NEW_OP_RETURN_BY_FIDS -> putNewOpReturnByFids(methodFidWatchedFidsMapMap.get(method),sinceHeight);
            }
        }
    }

    private void putNewCashByFids(Map<String, WebhookManager.WebhookRequestBody> ownerWebhookInfoMap, long sinceHeight) {
        for(String owner:ownerWebhookInfoMap.keySet()){
            WebhookManager.WebhookRequestBody webhookInfo = ownerWebhookInfoMap.get(owner);
            ArrayList<Cash> newCashList = getNewCashList(webhookInfo,sinceHeight);
            if(newCashList==null)return;
            pushDataList(webhookInfo, ApipApiNames.NEW_CASH_BY_FIDS,newCashList);
        }
    }

    private void putNewOpReturnByFids(Map<String, WebhookManager.WebhookRequestBody> ownerWebhookInfoMap, long sinceHeight) {
        for(String owner:ownerWebhookInfoMap.keySet()){
            WebhookManager.WebhookRequestBody webhookInfo = ownerWebhookInfoMap.get(owner);
            ArrayList<OpReturn> newOpReturnList = getNewOpReturnList(webhookInfo,sinceHeight);
            if(newOpReturnList==null)return;
            pushDataList(webhookInfo, ApipApiNames.NEW_OP_RETURN_BY_FIDS,newOpReturnList);
        }
    }

    private <T> void pushDataList(WebhookManager.WebhookRequestBody webhookRequestBody, String method, ArrayList<T> dataList) {
        Gson gson = new Gson();
        String sessionName ;
        String sessionKey;
        String balance;
        String bestHeight;
        try(Jedis jedis = new Jedis()){
            jedis.select(0);
            balance = jedis.hget(Settings.addSidBriefToName(sid, FieldNames.BALANCE),webhookRequestBody.getUserId());
            if(balance==null)return;

            String nPrice = jedis.hget(Settings.addSidBriefToName(sid,Strings.N_PRICE), method);
            double nPriceF = Double.parseDouble(nPrice);

            String pricePerKB = jedis.hget(Settings.addSidBriefToName(sid,PARAMS), FieldNames.PRICE_PER_K_BYTES);
            Long price = FchUtils.coinStrToSatoshi(pricePerKB);
            if(price==null)price=0L;

            long balanceL = Long.parseLong(balance);
            bestHeight = jedis.get(BEST_HEIGHT);

            long newBalanceL = (long) (balanceL-(price*nPriceF));
            balance = String.valueOf(newBalanceL);
            jedis.hset(Settings.addSidBriefToName(sid, FieldNames.BALANCE),webhookRequestBody.getUserId(),balance);

            sessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.ID_SESSION_NAME),webhookRequestBody.getUserId());
            if(sessionName==null)return;
            jedis.select(1);
            sessionKey = jedis.hget(sessionName, FieldNames.SESSION_KEY);
        }catch (Exception e){
            log.error("Operate redis wrong when push {}.",method,e);
            return;
        }
        byte[] sessionKeyBytes = HexFormat.of().parseHex(sessionKey);

        Map<String,Object> dataMap = new HashMap<>();
        dataMap.put(FieldNames.BALANCE,balance);
        dataMap.put(BEST_HEIGHT,bestHeight);
        dataMap.put(Strings.DATA,dataList);

        String dataStr = gson.toJson(dataMap);
        byte[] dataBytes = dataStr.getBytes(StandardCharsets.UTF_8);

        String sign = sign(sessionKeyBytes, dataBytes);

        String endpoint = webhookRequestBody.getEndpoint();

        WebhookPushBody postBody = new WebhookPushBody();

        postBody.setData(dataStr);

        postBody.setHookUserId(webhookRequestBody.getHookUserId());
        postBody.setMethod(method);
        postBody.setSessionName(sessionName);
        postBody.setSign(sign);
        postBody.setBestHeight(Long.valueOf(bestHeight));

        System.out.println("Endpoint:"+endpoint);
        CloseableHttpResponse result = HttpUtils.post(endpoint, new HashMap<>(), HttpUtils.BodyType.STRING, gson.toJson(postBody).getBytes());

        if(result==null){
            log.debug("Failed to push webhook data.");
            return;
        }
        try {
            JsonUtils.printJson(new String(result.getEntity().getContent().readAllBytes()));
        } catch (IOException e) {
            System.out.println("Failed to get http response entity.");
            return;
        }
        if(result.getStatusLine().getStatusCode()==200)System.out.println("Pushed webhook data:"+postBody.getHookUserId()+":\n"+result.getStatusLine().getReasonPhrase());
        else System.out.println("Failed to push new cashes.");
    }

    private ArrayList<Cash> getNewCashList(WebhookManager.WebhookRequestBody webhookInfo, long sinceHeight) {

        Map<String,Object> dataMap = ObjectUtils.objectToMap(webhookInfo.getData(),String.class, Object.class);
        if(dataMap==null)return null;
        Object idsObj = dataMap.get(IDS);
        List<String> idList = ObjectUtils.objectToList(idsObj,String.class);
        if(idList==null)return null;
        try {
            return EsUtils.getListByTermsSinceHeight(esClient,IndicesNames.CASH, FieldNames.OWNER,idList,sinceHeight, FieldNames.ID,SortOrder.Asc,Cash.class, null);
        } catch (IOException e) {
            log.error("Get new cash list for "+ ApipApiNames.NEW_CASH_BY_FIDS +" from ES wrong.",e);
            return null;
        }
    }

    private ArrayList<OpReturn> getNewOpReturnList(WebhookManager.WebhookRequestBody webhookInfo, long sinceHeight) {
        Object ids = webhookInfo.getData();
        List<String> idList = ObjectUtils.objectToList(ids,String.class);
        if(idList==null)return null;
        try {
            return EsUtils.getListByTermsSinceHeight(esClient,IndicesNames.OPRETURN, FieldNames.RECIPIENT,idList,sinceHeight, FieldNames.ID,SortOrder.Asc, OpReturn.class, null);
        } catch (IOException e) {
            log.error("Get new OpReturn list for "+ ApipApiNames.NEW_OP_RETURN_BY_FIDS +" from ES wrong.",e);
            return null;
        }
    }

    public void shutdown() {
        running.set(false);
    }
    public AtomicBoolean isRunning() {
        return running;
    }

    public void setRunning(AtomicBoolean running) {
        this.running = running;
    }
}
