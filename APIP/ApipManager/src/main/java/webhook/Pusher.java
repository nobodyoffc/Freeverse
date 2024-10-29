package webhook;

import apip.apipData.WebhookPushBody;
import apip.apipData.WebhookRequestBody;
import fch.ParseTools;
import fch.fchData.Cash;
import fch.fchData.OpReturn;
import clients.esClient.EsTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.google.gson.Gson;
import constants.*;

import javaTools.JsonTools;
import javaTools.ObjectTools;
import javaTools.http.HttpTools;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import appTools.Settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static apip.apipData.Session.getSessionKeySign;
import static constants.FieldNames.IDS;
import static constants.Strings.*;

public class Pusher implements Runnable{

    private static final Logger log = LoggerFactory.getLogger(Pusher.class);
    private volatile AtomicBoolean running = new AtomicBoolean(true);
    private final String listenDir;
    private final ElasticsearchClient esClient;
    private Map<String,Map<String, WebhookRequestBody>> methodFidEndpointInfoMapMap = new HashMap<>();
    private long sinceHeight;
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
            sinceHeight=bestHeight;
            checkSubscriptions(jedis);

            while (running.get()) {
                jedis.select(Constants.RedisDb4Webhook);
                readMethodFidWebhookInfoMapMapFromRedis(jedis);
                ParseTools.waitForChangeInDirectory(listenDir, running);
                TimeUnit.SECONDS.sleep(3);
                jedis.select(0);
                bestHeight = Long.parseLong(jedis.get(BEST_HEIGHT));
                if(sinceHeight<bestHeight) {
                    pushWebhooks(methodFidEndpointInfoMapMap, sinceHeight);
                    sinceHeight=bestHeight;
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            log.debug("Run pusher thread wrong.");
        }
    }

    private void checkSubscriptions(Jedis jedis) {
        List<WebhookRequestBody> webhookInfoList;
        jedis.select(Constants.RedisDb4Webhook);
        Map<String, String> newCashByFidsSubscriptionMap = jedis.hgetAll(Settings.addSidBriefToName(sid, ApiNames.NewCashByFids));
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
        Map<String, String> newCashByFidsHookInfoStrMap = jedis.hgetAll(Settings.addSidBriefToName(sid,ApiNames.NewCashByFids));
        if(newCashByFidsHookInfoStrMap==null||newCashByFidsHookInfoStrMap.isEmpty())return;
        Map<String, WebhookRequestBody> newCashByFidsHookInfoMap = new HashMap<>();
        for(String owner: newCashByFidsHookInfoStrMap.keySet()){
            String webhookInfoStr = newCashByFidsHookInfoStrMap.get(owner);
            newCashByFidsHookInfoMap.put(owner,gson.fromJson(webhookInfoStr, WebhookRequestBody.class));
        }
        methodFidEndpointInfoMapMap.put(ApiNames.NewCashByFids,newCashByFidsHookInfoMap);
        //More method:
    }

    private void setWebhookSubscriptionIntoRedis(Map<String, Map<String, WebhookRequestBody>> methodFidEndpointInfoMapMap, Jedis jedis) {
        if(methodFidEndpointInfoMapMap==null||methodFidEndpointInfoMapMap.size()==0)return;
        Gson gson = new Gson();
        for(String method:methodFidEndpointInfoMapMap.keySet()){
            Map<String, WebhookRequestBody> ownerWebhookInfoMap = methodFidEndpointInfoMapMap.get(method);
            for(String userId:ownerWebhookInfoMap.keySet()){
                WebhookRequestBody webhookInfo = ownerWebhookInfoMap.get(userId);
                jedis.hset(method,userId,gson.toJson(webhookInfo));
            }
        }
    }

    private void makeMethodFidEndpointMapMap(List<WebhookRequestBody> webhookInfoList) {
        if(webhookInfoList==null || webhookInfoList.size()==0)return;
        Map<String, WebhookRequestBody> newCashByFidsWebhookInfoMap = new HashMap<>();
        for (WebhookRequestBody webhookInfo : webhookInfoList) {
            switch (webhookInfo.getMethod()) {
                case ApiNames.NewCashByFids -> newCashByFidsWebhookInfoMap.put(webhookInfo.getUserId(), webhookInfo);
            }
        }
        methodFidEndpointInfoMapMap.put(ApiNames.NewCashByFids, newCashByFidsWebhookInfoMap);
    }

    private List<WebhookRequestBody> getWebhookInfoListFromEs(ElasticsearchClient esClient) {
        try {
            return EsTools.getAllList(esClient, Settings.addSidBriefToName(sid,IndicesNames.WEBHOOK), Strings.HOOK_USER_ID,SortOrder.Asc, WebhookRequestBody.class);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Read webhook info failed.");
            return null;
        }
    }

    private void pushWebhooks(Map<String, Map<String, WebhookRequestBody>> methodFidWatchedFidsMapMap, long sinceHeight) {

        for(String method: methodFidWatchedFidsMapMap.keySet()){
            switch (method){
                case ApiNames.NewCashByFids -> putNewCashByFids(methodFidWatchedFidsMapMap.get(method),sinceHeight);
                //TODO untested
                case ApiNames.NewOpReturnByFids -> putNewOpReturnByFids(methodFidWatchedFidsMapMap.get(method),sinceHeight);
            }
        }
    }

    private void putNewCashByFids(Map<String, WebhookRequestBody> ownerWebhookInfoMap, long sinceHeight) {
        for(String owner:ownerWebhookInfoMap.keySet()){
            WebhookRequestBody webhookInfo = ownerWebhookInfoMap.get(owner);
            ArrayList<Cash> newCashList = getNewCashList(webhookInfo,sinceHeight);
            if(newCashList==null)return;
            pushDataList(webhookInfo,ApiNames.NewCashByFids,newCashList);
        }
    }

    private void putNewOpReturnByFids(Map<String, WebhookRequestBody> ownerWebhookInfoMap, long sinceHeight) {
        for(String owner:ownerWebhookInfoMap.keySet()){
            WebhookRequestBody webhookInfo = ownerWebhookInfoMap.get(owner);
            ArrayList<OpReturn> newOpReturnList = getNewOpReturnList(webhookInfo,sinceHeight);
            if(newOpReturnList==null)return;
            pushDataList(webhookInfo,ApiNames.NewOpReturnByFids,newOpReturnList);
        }
    }

    private <T> void pushDataList(WebhookRequestBody webhookRequestBody, String method, ArrayList<T> dataList) {
        Gson gson = new Gson();
        String sessionName ;
        String sessionKey;
        String balance;
        String bestHeight;
        try(Jedis jedis = new Jedis()){
            jedis.select(0);
            balance = jedis.hget(Settings.addSidBriefToName(sid,Strings.BALANCE),webhookRequestBody.getUserId());
            if(balance==null)return;

            String nPrice = jedis.hget(Settings.addSidBriefToName(sid,Strings.N_PRICE), method);
            double nPriceF = Double.parseDouble(nPrice);

            String pricePerKB = jedis.hget(Settings.addSidBriefToName(sid,PARAMS), PRICE_PER_K_BYTES);
            long price = ParseTools.coinStrToSatoshi(pricePerKB);


            long balanceL = Long.parseLong(balance);
            bestHeight = jedis.get(BEST_HEIGHT);

            long newBalanceL = (long) (balanceL-(price*nPriceF));
            balance = String.valueOf(newBalanceL);
            jedis.hset(Settings.addSidBriefToName(sid, BALANCE),webhookRequestBody.getUserId(),balance);


//            boolean isPricePerRequest = Boolean.parseBoolean(jedis.hget(CONFIG, IS_PRICE_PER_REQUEST));
//            if(isPricePerRequest){
//                long newBalanceL = (long) (balanceL-(price*nPriceF));
//                balance = String.valueOf(newBalanceL);
//                jedis.hset(Settings.addSidBriefToName(sid, BALANCE),webhookRequestBody.getUserId(),balance);
//            }


            sessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME),webhookRequestBody.getUserId());
            if(sessionName==null)return;
            jedis.select(1);
            sessionKey = jedis.hget(sessionName,Strings.SESSION_KEY);
        }catch (Exception e){
            log.error("Operate redis wrong when push {}.",method,e);
            return;
        }
        byte[] sessionKeyBytes = HexFormat.of().parseHex(sessionKey);

        Map<String,Object> dataMap = new HashMap<>();
        dataMap.put(BALANCE,balance);
        dataMap.put(BEST_HEIGHT,bestHeight);
        dataMap.put(Strings.DATA,dataList);

        String dataStr = gson.toJson(dataMap);
        byte[] dataBytes = dataStr.getBytes(StandardCharsets.UTF_8);

        String sign = getSessionKeySign(sessionKeyBytes, dataBytes);

        String endpoint = webhookRequestBody.getEndpoint();

        WebhookPushBody postBody = new WebhookPushBody();

        postBody.setData(dataStr);

        postBody.setHookUserId(webhookRequestBody.getHookUserId());
        postBody.setMethod(method);
        postBody.setSessionName(sessionName);
        postBody.setSign(sign);
        postBody.setBestHeight(Long.valueOf(bestHeight));

        System.out.println("Endpoint:"+endpoint);
        CloseableHttpResponse result = HttpTools.post(endpoint, new HashMap<>(), "string", gson.toJson(postBody).getBytes());

        if(result==null){
            log.debug("Failed to push webhook data.");
            return;
        }
        try {
            JsonTools.printJson(new String(result.getEntity().getContent().readAllBytes()));
        } catch (IOException e) {
            System.out.println("Failed to get http response entity.");
            return;
        }
        if(result.getStatusLine().getStatusCode()==200)System.out.println("Pushed webhook data:"+postBody.getHookUserId()+":\n"+result.getStatusLine().getReasonPhrase());
        else System.out.println("Failed to push new cashes.");
    }

    private ArrayList<Cash> getNewCashList(WebhookRequestBody webhookInfo, long sinceHeight) {

        Map<String,Object> dataMap = ObjectTools.objectToMap(webhookInfo.getData(),String.class, Object.class);
        if(dataMap==null)return null;
        Object idsObj = dataMap.get(IDS);
        List<String> idList = ObjectTools.objectToList(idsObj,String.class);
        if(idList==null)return null;
        try {
            return EsTools.getListByTermsSinceHeight(esClient,IndicesNames.CASH, FieldNames.OWNER,idList,sinceHeight, FieldNames.CASH_ID,SortOrder.Asc,Cash.class);
        } catch (IOException e) {
            log.error("Get new cash list for "+ApiNames.NewCashByFids +" from ES wrong.",e);
            return null;
        }
    }

    private ArrayList<OpReturn> getNewOpReturnList(WebhookRequestBody webhookInfo, long sinceHeight) {
        Object ids = webhookInfo.getData();
        List<String> idList = ObjectTools.objectToList(ids,String.class);
        if(idList==null)return null;
        try {
            return EsTools.getListByTermsSinceHeight(esClient,IndicesNames.OPRETURN, FieldNames.RECIPIENT,idList,sinceHeight, FieldNames.TX_ID,SortOrder.Asc, OpReturn.class);
        } catch (IOException e) {
            log.error("Get new OpReturn list for "+ApiNames.NewOpReturnByFids +" from ES wrong.",e);
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
