package handlers;

import apip.apipData.Fcdsl;
import apip.apipData.WebhookPushBody;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.google.gson.Gson;
import constants.Constants;
import constants.FieldNames;
import constants.IndicesNames;
import constants.Strings;
import crypto.Hash;
import fch.FchUtils;
import fch.fchData.Cash;
import fch.fchData.OpReturn;
import feip.feipData.Service;
import nasa.NaSaRpcClient;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.ApipApiNames;
import utils.EsUtils;
import utils.ObjectUtils;
import utils.http.AuthType;
import utils.http.HttpUtils;
import utils.http.RequestMethod;
import appTools.Menu;
import appTools.Settings;
import clients.ApipClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NewWebhookHandler extends Handler<WebhookHandler.WebhookRequestBody> {
    private static final Logger log = LoggerFactory.getLogger(NewWebhookHandler.class);
    private final String sid;
    private final String listenPath;
    private final ElasticsearchClient esClient;
    private final NaSaRpcClient nasaClient;
    private final ApipClient apipClient;
    private final JedisPool jedisPool;
    private final AtomicBoolean running;
    private final List<String> methods;
    private long bestHeight;
    // private static final String METHOD_FID_ENDPOINT_INFO_MAP_MAP = "methodFidEndpointInfoMapMap";


    public NewWebhookHandler(Settings settings, List<String> methods, HandlerType handlerType, db.LocalDB.SortType sortType) {
        super(settings, handlerType, sortType, WebhookHandler.WebhookRequestBody.class, true, false);
        this.sid = settings.getSid();
        this.listenPath = (String) settings.getSettingMap().get(Settings.LISTEN_PATH);
        this.esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        this.nasaClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.jedisPool = (JedisPool)settings.getClient(Service.ServiceType.REDIS);
        this.running = new AtomicBoolean(true);
        this.methods = methods;
    }

    public void startPusherThread() {
        Thread pusherThread = new Thread(() -> {
            while (running.get()) {
                // readMethodFidWebhookInfoMapMapFromDB();
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
            lastPushHeight = (Long) localDB.getState(lastPushHeightKey);
        } catch (Exception e) {
            log.error("Error pushing webhook data", e);
            lastPushHeight = 0;
        }
        // checkSubscriptions();
        updateBestHeight();
        if (lastPushHeight < bestHeight) {
            pushWebhooks(lastPushHeight);
            lastPushHeight = bestHeight;
            localDB.putState(lastPushHeightKey, String.valueOf(lastPushHeight));
        }
    }

    public void subscribe(WebhookHandler.WebhookRequestBody webhookRequestBody) {
        try {
            localDB.putInMap(webhookRequestBody.getMethod(), webhookRequestBody.getUserId(), webhookRequestBody);
            // Save to ES
            if(esClient != null){
                esClient.index(i -> i
                    .index(Settings.addSidBriefToName(sid, IndicesNames.WEBHOOK))
                    .id(webhookRequestBody.getHookUserId())
                    .document(webhookRequestBody)
                );
            }
        } catch (Exception e) {
            log.error("Failed to save subscription", e);
        }
    }

    public void unsubscribe(WebhookHandler.WebhookRequestBody webhookInfo) {
        try {
            localDB.removeFromMap(webhookInfo.getMethod(), webhookInfo.getUserId());
            
            // Delete from ES
            if(esClient != null){
                esClient.delete(d -> d
                    .index(IndicesNames.WEBHOOK)
                    .id(webhookInfo.getHookUserId())
                );
            }
        } catch (Exception e) {
            log.error("Failed to delete subscription", e);
        }
    }

    private void pushWebhooks(long sinceHeight) {
        for (String method : methods) {
            Map<String, WebhookHandler.WebhookRequestBody> fidWebhookInfoMap = localDB.getAllFromMap(method);
            
            switch (method) {
                case ApipApiNames.NEW_CASH_BY_FIDS -> pushNewCashByFids(fidWebhookInfoMap, sinceHeight);
                case ApipApiNames.NEW_OP_RETURN_BY_FIDS -> pushNewOpReturnByFids(fidWebhookInfoMap, sinceHeight);
                default -> log.warn("Unknown webhook method: {}", method);
            }
        }
    }

    private void pushNewCashByFids(Map<String, WebhookHandler.WebhookRequestBody> fidWebhookInfoMap, long sinceHeight) {
        for (WebhookHandler.WebhookRequestBody webhookInfo : fidWebhookInfoMap.values()) {
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

    private void pushNewOpReturnByFids(Map<String, WebhookHandler.WebhookRequestBody> fidWebhookInfoMap, long sinceHeight) {
        for (WebhookHandler.WebhookRequestBody webhookInfo : fidWebhookInfoMap.values()) {
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

    private <T> void pushDataList(WebhookHandler.WebhookRequestBody webhookInfo, String method, List<T> dataList) {
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
            try (CloseableHttpResponse response = HttpUtils.post(webhookInfo.getEndpoint(), headers, HttpUtils.BodyType.STRING, jsonBody.getBytes())) {
                if (response == null || response.getStatusLine().getStatusCode() != 200) {
                    log.error("Failed to push webhook data to {}: {}", 
                        webhookInfo.getEndpoint(),
                        response == null ? "" : response.getStatusLine().getStatusCode());
                }
            }
        } catch (Exception e) {
            log.error("Error pushing webhook data: {}", e.getMessage());
        }
    }

    private List<Cash> getNewCashList(WebhookHandler.WebhookRequestBody webhookInfo, long sinceHeight) {
        Map<String, Object> dataMap = ObjectUtils.objectToMap(webhookInfo.getData(), String.class, Object.class);
        if (dataMap == null) return null;
        Object idsObj = dataMap.get(FieldNames.IDS);
        List<String> idList = ObjectUtils.objectToList(idsObj, String.class);
        if (idList == null) return null;
        return CashHandler.getAllCashListByFids(idList, true, sinceHeight, Constants.DEFAULT_DISPLAY_LIST_SIZE, null, null, apipClient, nasaClient, esClient);
    }

    private List<OpReturn> getNewOpReturnList(WebhookHandler.WebhookRequestBody webhookInfo, Long sinceHeight, List<String> last) {
        Map<String, Object> dataMap = ObjectUtils.objectToMap(webhookInfo.getData(), String.class, Object.class);
        if (dataMap == null) return null;
        Object idsObj = dataMap.get(FieldNames.IDS);
        List<String> idList = ObjectUtils.objectToList(idsObj, String.class);
        if (idList == null) return null;
        List<OpReturn> opReturnList = new ArrayList<>();
        if (apipClient != null) {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addSize(Constants.DEFAULT_DISPLAY_LIST_SIZE);
            fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.SIGNER).addNewValues(idList.toArray(new String[0]));
            if (sinceHeight != null) {
                fcdsl.getQuery().addNewRange().addNewFields(FieldNames.HEIGHT).addGt(String.valueOf(sinceHeight));
            }
            if (last != null) {
                fcdsl.setAfter(last);
            }
            opReturnList = apipClient.opReturnSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        } else if (esClient != null) {
            try {
                opReturnList = EsUtils.getListByTermsSinceHeight(esClient, Settings.addSidBriefToName(sid, IndicesNames.OPRETURN), FieldNames.SIGNER, idList, sinceHeight, FieldNames.HEIGHT, SortOrder.Desc, OpReturn.class, last);
            } catch (IOException e) {
                log.error("Error getting op return list from es: {}", e.getMessage());
            }
        }
        return opReturnList;
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

    @Override
    public void menu(BufferedReader br, boolean withSettings) {
        Menu menu = new Menu("Webhook Management", this::close);
        menu.add("Start Pusher Thread", this::startPusherThread);
        menu.add("Push Webhook Data", this::pushWebhookData);
        menu.add("Subscribe", () -> {
            // Example usage, replace with actual WebhookRequestBody
            WebhookHandler.WebhookRequestBody requestBody = new WebhookHandler.WebhookRequestBody();
            subscribe(requestBody);
        });
        menu.add("Unsubscribe", () -> {
            // Example usage, replace with actual WebhookRequestBody
            WebhookHandler.WebhookRequestBody requestBody = new WebhookHandler.WebhookRequestBody();
            unsubscribe(requestBody);
        });
        menu.add("Show Webhook Requests", () -> showOrChooseItemList("Showing Webhook Requests...",null, 20, br,true,false));

        addBasicMenuItems(br, menu);
        
        menu.showAndSelect(br);
    }

    public static class WebhookRequestBody {
        private String hookUserId;
        private String userId;
        private String method;
        private String endpoint;
        private Object data;
        private String op;

        

        public static String makeHookUserId(String sid, String newCashByFidsAPI, String userId) {
            return Hash.sha256x2(sid+newCashByFidsAPI+userId);
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