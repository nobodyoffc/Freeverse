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

import javaTools.http.HttpTools;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import server.Settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static apip.apipData.Session.getSessionKeySign;
import static constants.Strings.*;

public class Pusher implements Runnable{

    private static final Logger log = LoggerFactory.getLogger(Pusher.class);
    private volatile AtomicBoolean running = new AtomicBoolean(true);
    private final String listenDir;
    private final ElasticsearchClient esClient;
    private Map<String,Map<String, WebhookRequestBody>> methodFidEndpointInfoMapMap = new HashMap<>();
    private long sinceHeight;
    private String sid;

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
            List<WebhookRequestBody> webhookInfoList;
            webhookInfoList = getWebhookInfoListFromEs(esClient);
            makeMethodFidEndpointMapMap(webhookInfoList);

            // <method:<owner:webhookInfo>>

            jedis.select(Constants.RedisDb4Webhook);

            //TODO
            jedis.flushDB();
            setNewCashByFidsMapMapIntoRedis(methodFidEndpointInfoMapMap,jedis);

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

    private void readMethodFidWebhookInfoMapMapFromRedis(Jedis jedis) {
        Gson gson = new Gson();
        methodFidEndpointInfoMapMap = new HashMap<>();

        //Method: newCashByFids
        Map<String, String> newCashByFidsHookInfoStrMap = jedis.hgetAll(ApiNames.NewCashByFids);
        Map<String, WebhookRequestBody> newCashByFidsHookInfoMap = new HashMap<>();
        for(String owner: newCashByFidsHookInfoStrMap.keySet()){
            String webhookInfoStr = newCashByFidsHookInfoStrMap.get(owner);
            newCashByFidsHookInfoMap.put(owner,gson.fromJson(webhookInfoStr, WebhookRequestBody.class));
        }
        methodFidEndpointInfoMapMap.put(ApiNames.NewCashByFids,newCashByFidsHookInfoMap);
        //More method:
    }

    private void setNewCashByFidsMapMapIntoRedis(Map<String, Map<String, WebhookRequestBody>> methodFidEndpointInfoMapMap, Jedis jedis) {
        if(methodFidEndpointInfoMapMap==null||methodFidEndpointInfoMapMap.size()==0)return;
        Gson gson = new Gson();
        for(String method:methodFidEndpointInfoMapMap.keySet()){
            Map<String, WebhookRequestBody> ownerWebhookInfoMap = methodFidEndpointInfoMapMap.get(method);
            for(String owner:ownerWebhookInfoMap.keySet()){
                WebhookRequestBody webhookInfo = ownerWebhookInfoMap.get(owner);
                jedis.hset(method,owner,gson.toJson(webhookInfo));
            }
        }
    }

    private void makeMethodFidEndpointMapMap(List<WebhookRequestBody> webhookInfoList) {
        if(webhookInfoList==null || webhookInfoList.size()==0)return;
        Map<String, WebhookRequestBody> newCashByFidsWebhookInfoMap = new HashMap<>();
        for (WebhookRequestBody webhookInfo : webhookInfoList) {
            switch (webhookInfo.getMethod()) {
                case ApiNames.NewCashByFids -> newCashByFidsWebhookInfoMap.put(webhookInfo.getUserName(), webhookInfo);
            }
        }
        methodFidEndpointInfoMapMap.put(ApiNames.NewCashByFids, newCashByFidsWebhookInfoMap);
    }

    private List<WebhookRequestBody> getWebhookInfoListFromEs(ElasticsearchClient esClient) {
        try {
            return EsTools.getAllList(esClient,	Settings.addSidBriefToName(sid,IndicesNames.WEBHOOK), Strings.HOOK_USER_ID,SortOrder.Asc, WebhookRequestBody.class);
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

    private <T> void pushDataList(WebhookRequestBody webhookInfo, String method, ArrayList<T> dataList) {
        Gson gson = new Gson();
        String sessionName ;
        String sessionKey;
        String balance;
        String bestHeight;
        try(Jedis jedis = new Jedis()){
            jedis.select(0);
            balance = jedis.hget(Settings.addSidBriefToName(sid,Strings.BALANCE),webhookInfo.getUserName());
            if(balance==null)return;
            String nPrice = jedis.hget(Settings.addSidBriefToName(sid,Strings.N_PRICE), method);
            long price = (long)(Double.parseDouble(jedis.hget(CONFIG,PRICE))*Constants.FchToSatoshi);
            float nPriceF = Float.parseFloat(nPrice);
            long balanceL = Long.parseLong(balance);
            bestHeight = jedis.get(BEST_HEIGHT);
            boolean isPricePerRequest = Boolean.parseBoolean(jedis.hget(CONFIG, IS_PRICE_PER_REQUEST));
            if(isPricePerRequest){
                long newBalanceL = (long) (balanceL-(price*nPriceF));
                balance = String.valueOf(newBalanceL);
                jedis.hset(Settings.addSidBriefToName(sid, BALANCE),webhookInfo.getUserName(),balance);
            }

            sessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME),webhookInfo.getUserName());
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

        String endpoint = webhookInfo.getEndpoint();

        WebhookPushBody postBody = new WebhookPushBody();

        postBody.setData(dataStr);

        postBody.setHookUserId(webhookInfo.getHookUserId());
        postBody.setMethod(method);
        postBody.setSessionName(sessionName);
        postBody.setSign(sign);
        postBody.setSinceHeight(webhookInfo.getLastHeight());

        CloseableHttpResponse result = HttpTools.post(endpoint, new HashMap<>(), "string", gson.toJson(postBody).getBytes());
        if(result==null)log.debug("Failed to push webhook data.");
    }

    private ArrayList<Cash> getNewCashList(WebhookRequestBody webhookInfo, long sinceHeight) {
        Gson gson = new Gson();

        DataOfIds data = gson.fromJson(gson.toJson(webhookInfo.getData()), DataOfIds.class);
        String[] fids = data.getFids();
        try {
            return EsTools.getListByTermsSinceHeight(esClient,IndicesNames.CASH, FieldNames.OWNER,fids,sinceHeight, FieldNames.CASH_ID,SortOrder.Asc,Cash.class);
        } catch (IOException e) {
            log.error("Get new cash list for "+ApiNames.NewCashByFids +" from ES wrong.",e);
            return null;
        }
    }

    private ArrayList<OpReturn> getNewOpReturnList(WebhookRequestBody webhookInfo, long sinceHeight) {
        Gson gson = new Gson();

        DataOfIds data = gson.fromJson(gson.toJson(webhookInfo.getData()), DataOfIds.class);
        String[] fids = data.getFids();
        try {
            return EsTools.getListByTermsSinceHeight(esClient,IndicesNames.OPRETURN, FieldNames.RECIPIENT,fids,sinceHeight, FieldNames.TX_ID,SortOrder.Asc, OpReturn.class);
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
