package handlers;

import data.apipData.Fcdsl;
import data.apipData.WebhookPushBody;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;

import com.google.gson.Gson;
import constants.Constants;
import constants.FieldNames;
import constants.IndicesNames;
import constants.Strings;

import core.crypto.Hash;
import db.LocalDB;
import data.fcData.FcEntity;
import data.fchData.Cash;
import data.fchData.OpReturn;
import data.feipData.Service;
import clients.NaSaClient.NaSaRpcClient;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.ApipApiNames;
import utils.EsUtils;
import utils.FchUtils;
import utils.ObjectUtils;
import utils.http.AuthType;
import utils.http.HttpUtils;
import utils.http.RequestMethod;
import config.Settings;
import clients.ApipClient;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebhookManager extends Manager<WebhookManager.WebhookRequestBody> {
    private static final Logger log = LoggerFactory.getLogger(WebhookManager.class);
    private final String sid;
    private final String listenPath;
    private final ElasticsearchClient esClient;
    private final NaSaRpcClient nasaClient;
    private final ApipClient apipClient;
    private final JedisPool jedisPool;
    private AtomicBoolean running;
    private Map<String, Map<String, WebhookRequestBody>> methodFidEndpointInfoMapMap;
    private long bestHeight;

    public WebhookManager(Settings settings) {
        super(settings, ManagerType.WEBHOOK, LocalDB.SortType.UPDATE_ORDER, WebhookRequestBody.class,
               true, false);
        this.sid = settings.getSid();
        this.listenPath = (String) settings.getSettingMap().get(Settings.LISTEN_PATH);
        this.esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        this.nasaClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.jedisPool = (JedisPool)settings.getClient(Service.ServiceType.REDIS);
        this.running = new AtomicBoolean(false);
        this.methodFidEndpointInfoMapMap = new HashMap<>();
    }

    public void putWebhookRequestBody(String id, WebhookRequestBody item) {
        localDB.put(id, item);
    }

    public WebhookRequestBody getWebhookRequestBody(String id) {
        return localDB.get(id);
    }

    public void startPusherThread() {
        Thread pusherThread = new Thread(() -> {
            running = new AtomicBoolean(true);
            while (running.get()) {
                readMethodFidWebhookInfoMapMapFromDB();
                FchUtils.waitForChangeInDirectory(listenPath, running);
                try {
                    TimeUnit.SECONDS.sleep(3);
                    pushWebhookData();
                } catch (InterruptedException e) {
                    log.error("Pusher thread interrupted", e);
                }
            }
        });
        pusherThread.setDaemon(true);
        pusherThread.start();
    }

    public void pushWebhookData() {
        String lastPushHeightKey = "lastPushHeight";
        long lastPushHeight;
        try {
            Object lastHeightObj = localDB.getMeta(lastPushHeightKey);
            lastPushHeight = lastHeightObj != null ? Long.parseLong(lastHeightObj.toString()) : bestHeight;
        } catch (Exception e) {
            log.error("Error pushing webhook data", e);
            lastPushHeight = 0;
        }
        checkSubscriptions();
        updateBestHeight();
        if (lastPushHeight < bestHeight) {
            pushWebhooks(methodFidEndpointInfoMapMap, lastPushHeight);
            lastPushHeight = bestHeight;
            localDB.putMeta(lastPushHeightKey, String.valueOf(lastPushHeight));
        }
//        getIsRunning().set(true);
    }

    // Subscription Management Methods
    public void subscribe(WebhookRequestBody webhookRequestBody) {
        try {
            String key = makeSubscriptionKey(webhookRequestBody.getMethod(), webhookRequestBody.getUserId());
            localDB.put(key, webhookRequestBody);
            
            // Save to ES
            esClient.index(i -> i
                .index(Settings.addSidBriefToName(sid, IndicesNames.WEBHOOK))
                .id(webhookRequestBody.getHookUserId())
                .document(webhookRequestBody)
            );
        } catch (Exception e) {
            log.error("Failed to save subscription", e);
        }
    }

    public void unsubscribe(WebhookRequestBody webhookInfo) {
        try {
            String key = makeSubscriptionKey(webhookInfo.getMethod(), webhookInfo.getUserId());
            localDB.remove(key);
            
            // Delete from ES
            esClient.delete(d -> d
                .index(IndicesNames.WEBHOOK)
                .id(webhookInfo.getHookUserId())
            );
        } catch (Exception e) {
            log.error("Failed to delete subscription", e);
        }
    }

    public WebhookRequestBody checkSubscription(String userId, String method) {
        String key = makeSubscriptionKey(method, userId);
        return localDB.get(key);
    }

    private String makeSubscriptionKey(String method, String userId) {
        return Settings.addSidBriefToName(sid, method) + ":" + userId;
    }

    private void checkSubscriptions() {
        readMethodFidWebhookInfoMapMapFromDB();
    }

    private void readMethodFidWebhookInfoMapMapFromDB() {
        Map<String, Map<String, WebhookRequestBody>> newMap = new HashMap<>();
        
        // Get all items from LevelDB
        Map<String, WebhookRequestBody> allItems = localDB.getItemMap();
        
        // Group subscriptions by method
        for (Map.Entry<String, WebhookRequestBody> entry : allItems.entrySet()) {
            String key = entry.getKey();
            if (!key.contains(":")) continue;
            
            String[] parts = key.split(":");
            if (parts.length != 2) continue;
            
            String method = parts[0];
            String userId = parts[1];
            WebhookRequestBody webhookInfo = entry.getValue();
            
            newMap.computeIfAbsent(method, k -> new HashMap<>())
                 .put(userId, webhookInfo);
        }
        
        methodFidEndpointInfoMapMap = newMap;
    }

    private void pushWebhooks(Map<String, Map<String, WebhookRequestBody>> methodFidWebhookInfoMapMap, long sinceHeight) {
        for (Map.Entry<String, Map<String, WebhookRequestBody>> entry : methodFidWebhookInfoMapMap.entrySet()) {
            String method = entry.getKey();
            Map<String, WebhookRequestBody> fidWebhookInfoMap = entry.getValue();
            
            switch (method) {
                case ApipApiNames.NEW_CASH_BY_FIDS -> pushNewCashByFids(fidWebhookInfoMap, sinceHeight);
                case ApipApiNames.NEW_OP_RETURN_BY_FIDS -> pushNewOpReturnByFids(fidWebhookInfoMap, sinceHeight);
                default -> log.warn("Unknown webhook method: {}", method);
            }
        }
    }

    private void pushNewCashByFids(Map<String, WebhookRequestBody> fidWebhookInfoMap, long sinceHeight) {
        for (WebhookRequestBody webhookInfo : fidWebhookInfoMap.values()) {
            try {
                List<Cash> newCashList = getNewCashList(webhookInfo, sinceHeight);
                if (newCashList != null && !newCashList.isEmpty()) {
                    pushDataList(webhookInfo, ApipApiNames.NEW_CASH_BY_FIDS, newCashList);
                }
            } catch (Exception e) {
                log.error("Error pushing new cash for {}: {}", webhookInfo.getUserId(), e.getMessage());
            }
        }
    }

    private void pushNewOpReturnByFids(Map<String, WebhookRequestBody> fidWebhookInfoMap, long sinceHeight) {
        for (WebhookRequestBody webhookInfo : fidWebhookInfoMap.values()) {
            try {
                List<OpReturn> newOpReturnList = getNewOpReturnList(webhookInfo, sinceHeight, null);
                if (newOpReturnList != null && !newOpReturnList.isEmpty()) {
                    pushDataList(webhookInfo, ApipApiNames.NEW_OP_RETURN_BY_FIDS, newOpReturnList);
                }
            } catch (Exception e) {
                log.error("Error pushing new op returns for {}: {}", webhookInfo.getUserId(), e.getMessage());
            }
        }
    }

    private <T> void pushDataList(WebhookRequestBody webhookInfo, String method, List<T> dataList) {
        if (dataList == null || dataList.isEmpty() || webhookInfo.getEndpoint() == null) {
            return;
        }

        try {
            WebhookPushBody pushBody = new WebhookPushBody();
            pushBody.setHookUserId(webhookInfo.getHookUserId());
            pushBody.setMethod(method);
            pushBody.setData(new Gson().toJson(dataList));
            
            // Get current best height
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.select(0);
                String bestHeightStr = jedis.get(Strings.BEST_HEIGHT);
                if (bestHeightStr != null) {
                    pushBody.setBestHeight(Long.parseLong(bestHeightStr));
                }
            }

            // Send the push notification
            String jsonBody = new Gson().toJson(pushBody);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            try (CloseableHttpResponse response = HttpUtils.post(webhookInfo.getEndpoint(),headers, HttpUtils.BodyType.STRING, jsonBody.getBytes())) {
                if(response==null||response.getStatusLine().getStatusCode() != 200) {
                    log.error("Failed to push webhook data to {}: {}", 
                        webhookInfo.getEndpoint(),
                        response==null?"":response.getStatusLine().getStatusCode());
                }
            }
        } catch (Exception e) {
            log.error("Error pushing webhook data: {}", e.getMessage());
        }
    }

    // Helper methods for getting new data
    private List<Cash> getNewCashList(WebhookRequestBody webhookInfo, long sinceHeight) {
        Map<String,Object> dataMap = ObjectUtils.objectToMap(webhookInfo.getData(),String.class, Object.class);
        if(dataMap==null)return null;
        Object idsObj = dataMap.get(FieldNames.IDS);
        List<String> idList = ObjectUtils.objectToList(idsObj,String.class);
        if(idList==null)return null;
        return CashManager.getAllCashListByFids(idList, true, sinceHeight, Constants.DEFAULT_DISPLAY_LIST_SIZE, null, null, apipClient, nasaClient, esClient);
    }

    private List<OpReturn> getNewOpReturnList(WebhookRequestBody webhookInfo, Long sinceHeight,List<String> last) {
        Map<String,Object> dataMap = ObjectUtils.objectToMap(webhookInfo.getData(),String.class, Object.class);
        if(dataMap==null)return null;
        Object idsObj = dataMap.get(FieldNames.IDS);
        List<String> idList = ObjectUtils.objectToList(idsObj,String.class);
        if(idList==null)return null;
        List<OpReturn> opReturnList = new ArrayList<>();
        if(apipClient!=null){
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addSize(Constants.DEFAULT_DISPLAY_LIST_SIZE);
            fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.SIGNER).addNewValues(idList.toArray(new String[0]));
            if(sinceHeight!=null){
                fcdsl.getQuery().addNewRange().addNewFields(FieldNames.HEIGHT).addGt(String.valueOf(sinceHeight));
            }
            if(last!=null){
                fcdsl.setAfter(last);
            }
            opReturnList = apipClient.opReturnSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        }else if(esClient!=null){
            try {
                opReturnList = EsUtils.getListByTermsSinceHeight(esClient, Settings.addSidBriefToName(sid, IndicesNames.OPRETURN), FieldNames.SIGNER, idList, sinceHeight, FieldNames.HEIGHT, SortOrder.Desc, OpReturn.class, last);
            } catch (IOException e) {
                log.error("Error getting op return list from es: {}", e.getMessage());
            }
        }
        return opReturnList;
    }

    public void shutdown() {
        running.set(false);
    }

    private void updateBestHeight() {
        if (apipClient != null) {
            Long height = apipClient.bestHeight();
            if (height != null) {
                this.bestHeight = height;
                return;
            }
        }
        if (nasaClient != null) {
            this.bestHeight = nasaClient.getBestHeight();
        }
    }

    public void checkForUpdates() {
        updateBestHeight();
        
        Map<String, WebhookRequestBody> allItems = localDB.getItemMap();
        for (Map.Entry<String, WebhookRequestBody> entry : allItems.entrySet()) {
            String key = entry.getKey();
            if (!key.contains(":")) continue;
            
            String[] parts = key.split(":");
            if (parts.length != 2) continue;
            
            String method = parts[0];
            String userId = parts[1];
            WebhookRequestBody webhookInfo = entry.getValue();

            if (method.equals("NEW_CASH_BY_FIDS")) {
                List<Cash> newCashes = CashManager.getCashListFromApip(
                    userId, true, 50, null, null, bestHeight, apipClient);
                
                if (newCashes != null && !newCashes.isEmpty()) {
                    // Process new cashes here
                    // Update last processed height
                    localDB.put(key, webhookInfo);
                }
            }
            // Add other method handlers as needed
        }
    }

    public static class WebhookRequestBody extends FcEntity {
        private String hookUserId;
        private String userId;
        private String method;
        private String endpoint;
        private Object data;
        private String op;

        public static String makeHookUserId(String sid, String newCashByFidsAPI, String userId) {
            return Hash.sha256x2(sid+newCashByFidsAPI+userId);
        }

        public String makeHookUserId(String sid) {
            this.hookUserId = Hash.sha256x2(sid+method+userId);
            return this.hookUserId;
        }

        public String getOp() {
            return op;
        }

        public void setOp(String op) {
            this.op = op;
        }

        public String getHookUserId() {
            return hookUserId;
        }

        public void setHookUserId(String hookUserId) {
            this.hookUserId = hookUserId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }
    }
}