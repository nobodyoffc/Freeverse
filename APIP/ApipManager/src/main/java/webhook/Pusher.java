package webhook;

import constants.CodeMessage;
import data.apipData.WebhookPushBody;
import data.fchData.Cash;
import data.fchData.OpReturn;
import managers.AccountManager;
import managers.Manager;
import managers.WebhookManager;
import server.ApipApi;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.google.gson.Gson;
import constants.*;

import core.crypto.CryptoDataByte;
import core.crypto.Encryptor;
import data.fcData.AlgorithmId;
import utils.FchUtils;
import utils.Hex;
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

import static constants.FieldNames.IDS;
import static constants.Strings.*;

public class Pusher implements Runnable{

    private static final Logger log = LoggerFactory.getLogger(Pusher.class);
    private volatile AtomicBoolean running = new AtomicBoolean(true);
    private final Settings settings;
    private final String listenDir;
    private final ElasticsearchClient esClient;
    private final String sid;
    private final AccountManager accountManager;
    private Map<String,Map<String, WebhookManager.WebhookRequestBody>> methodFidEndpointInfoMapMap = new HashMap<>();

    public Pusher(Settings settings, String listenDir, ElasticsearchClient esClient) {
        this.settings = settings;
        this.esClient = esClient;
        this.listenDir = listenDir;
        this.sid = settings.getSid();
        this.accountManager = (AccountManager) settings.getManager(Manager.ManagerType.ACCOUNT);
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
        Map<String, String> newCashByFidsSubscriptionMap = jedis.hgetAll(Settings.addSidBriefToName(sid, ApipApi.HOOK_NEW_CASH_BY_FIDS.getName()));
        if(newCashByFidsSubscriptionMap ==null) {
            webhookInfoList = getWebhookInfoListFromEs(esClient);
            makeMethodFidEndpointMapMap(webhookInfoList);
            setWebhookSubscriptionIntoRedis(methodFidEndpointInfoMapMap, jedis);
        }
    }

    private void readMethodFidWebhookInfoMapMapFromRedis(Jedis jedis) {
        Gson gson = new Gson();
        methodFidEndpointInfoMapMap = new HashMap<>();
        //Method: newCashByFids
        Map<String, String> newCashByFidsHookInfoStrMap = jedis.hgetAll(Settings.addSidBriefToName(sid, ApipApi.HOOK_NEW_CASH_BY_FIDS.getName()));
        if(newCashByFidsHookInfoStrMap==null||newCashByFidsHookInfoStrMap.isEmpty())return;
        Map<String, WebhookManager.WebhookRequestBody> newCashByFidsHookInfoMap = new HashMap<>();
        for(String owner: newCashByFidsHookInfoStrMap.keySet()){
            String webhookInfoStr = newCashByFidsHookInfoStrMap.get(owner);
            newCashByFidsHookInfoMap.put(owner,gson.fromJson(webhookInfoStr, WebhookManager.WebhookRequestBody.class));
        }
        methodFidEndpointInfoMapMap.put(ApipApi.HOOK_NEW_CASH_BY_FIDS.getName(),newCashByFidsHookInfoMap);
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
                case ApipApiNames.HOOK_NEW_CASH_BY_FIDS -> newCashByFidsWebhookInfoMap.put(webhookInfo.getUserId(), webhookInfo);
            }
        }
        methodFidEndpointInfoMapMap.put(ApipApi.HOOK_NEW_CASH_BY_FIDS.getName(), newCashByFidsWebhookInfoMap);
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
                case ApipApiNames.HOOK_NEW_CASH_BY_FIDS -> putNewCashByFids(methodFidWatchedFidsMapMap.get(method),sinceHeight);
                case ApipApiNames.HOOK_NEW_OP_RETURN_BY_FIDS -> putNewOpReturnByFids(methodFidWatchedFidsMapMap.get(method),sinceHeight);
            }
        }
    }

    private void putNewCashByFids(Map<String, WebhookManager.WebhookRequestBody> ownerWebhookInfoMap, long sinceHeight) {
        for(String owner:ownerWebhookInfoMap.keySet()){
            WebhookManager.WebhookRequestBody webhookInfo = ownerWebhookInfoMap.get(owner);
            ArrayList<Cash> newCashList = getNewCashList(webhookInfo,sinceHeight);
            if(newCashList==null)return;
            pushDataList(webhookInfo, ApipApi.HOOK_NEW_CASH_BY_FIDS.getName(),newCashList);
        }
    }

    private void putNewOpReturnByFids(Map<String, WebhookManager.WebhookRequestBody> ownerWebhookInfoMap, long sinceHeight) {
        for(String owner:ownerWebhookInfoMap.keySet()){
            WebhookManager.WebhookRequestBody webhookInfo = ownerWebhookInfoMap.get(owner);
            ArrayList<OpReturn> newOpReturnList = getNewOpReturnList(webhookInfo,sinceHeight);
            if(newOpReturnList==null)return;
            pushDataList(webhookInfo, ApipApi.NEW_OP_RETURN_HOOK_BY_FIDS.getName(),newOpReturnList);
        }
    }

    private <T> void pushDataList(WebhookManager.WebhookRequestBody webhookRequestBody, String method, ArrayList<T> dataList) {
        Gson gson = new Gson();
        String bestHeight;
        String endpoint = webhookRequestBody.getEndpoint();
        String subscriberPubkey = webhookRequestBody.getPubkey();

        if (subscriberPubkey == null) {
            log.error("No pubkey found for webhook subscriber {}. Skipping push.", webhookRequestBody.getUserId());
            return;
        }

        try (Jedis jedis = new Jedis()) {
            jedis.select(0);
            bestHeight = jedis.get(BEST_HEIGHT);
        } catch (Exception e) {
            log.error("Failed to get bestHeight from redis for push {}.", method, e);
            return;
        }

        // Check balance and deduct cost
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(FieldNames.BALANCE, null);
        dataMap.put(BEST_HEIGHT, bestHeight);
        dataMap.put(Strings.DATA, dataList);
        String dataStr = gson.toJson(dataMap);
        long length = dataStr.length();

        Long newBalance = accountManager.userSpend(webhookRequestBody.getUserId(), method, length, null);

        if (newBalance != null && newBalance < -accountManager.getMinCredit()) {
            // Insufficient balance: send 1004 error and unsubscribe
            log.warn("Insufficient balance for webhook subscriber {}. Sending 1004 and unsubscribing.", webhookRequestBody.getUserId());
            pushInsufficientBalanceError(webhookRequestBody, method, bestHeight);
            unsubscribeWebhook(webhookRequestBody);
            return;
        }

        // Update balance in data
        dataMap.put(FieldNames.BALANCE, newBalance);
        dataStr = gson.toJson(dataMap);

        // Encrypt data with AsyTwoWay
        byte[] serverPrikey = settings.decryptPrikey();
        byte[] subscriberPubkeyBytes = Hex.fromHex(subscriberPubkey);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesGcm256_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptByAsyTwoWay(dataStr.getBytes(StandardCharsets.UTF_8), serverPrikey, subscriberPubkeyBytes);
        if (encrypted == null) {
            log.error("Failed to encrypt webhook push data for {}.", webhookRequestBody.getUserId());
            return;
        }

        WebhookPushBody pushBody = new WebhookPushBody();
        pushBody.setHookUserId(webhookRequestBody.getHookUserId());
        pushBody.setMethod(method);
        pushBody.setBestHeight(Long.valueOf(bestHeight));
        pushBody.setCode(CodeMessage.Code0Success);
        pushBody.setEncryptedData(encrypted.toJson());

        log.info("Pushing webhook to endpoint: {}", endpoint);
        CloseableHttpResponse result = HttpUtils.post(endpoint, new HashMap<>(), HttpUtils.BodyType.STRING, gson.toJson(pushBody).getBytes());

        if (result == null) {
            log.debug("Failed to push webhook data to {}.", endpoint);
            return;
        }
        try {
            if (result.getStatusLine().getStatusCode() == 200) {
                log.info("Pushed webhook data for {}: {}", pushBody.getHookUserId(), result.getStatusLine().getReasonPhrase());
            } else {
                log.warn("Failed to push webhook data. Status: {}", result.getStatusLine().getStatusCode());
            }
            result.close();
        } catch (IOException e) {
            log.warn("Failed to read http response for webhook push.", e);
        }
    }

    private void pushInsufficientBalanceError(WebhookManager.WebhookRequestBody webhookRequestBody, String method, String bestHeight) {
        Gson gson = new Gson();
        String subscriberPubkey = webhookRequestBody.getPubkey();
        String endpoint = webhookRequestBody.getEndpoint();

        // Build error data and encrypt it
        Map<String, Object> errorData = new HashMap<>();
        errorData.put(FieldNames.BALANCE, accountManager.getUserBalance(webhookRequestBody.getUserId()));
        errorData.put(BEST_HEIGHT, bestHeight);
        String errorDataStr = gson.toJson(errorData);

        byte[] serverPrikey = settings.decryptPrikey();
        byte[] subscriberPubkeyBytes = Hex.fromHex(subscriberPubkey);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesGcm256_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptByAsyTwoWay(errorDataStr.getBytes(StandardCharsets.UTF_8), serverPrikey, subscriberPubkeyBytes);

        WebhookPushBody pushBody = new WebhookPushBody();
        pushBody.setHookUserId(webhookRequestBody.getHookUserId());
        pushBody.setMethod(method);
        pushBody.setBestHeight(Long.valueOf(bestHeight));
        pushBody.setCode(CodeMessage.Code1004InsufficientBalance);
        pushBody.setMessage(CodeMessage.Msg1004InsufficientBalance);
        if (encrypted != null) {
            pushBody.setEncryptedData(encrypted.toJson());
        }

        CloseableHttpResponse result = HttpUtils.post(endpoint, new HashMap<>(), HttpUtils.BodyType.STRING, gson.toJson(pushBody).getBytes());
        if (result != null) {
            try {
                result.close();
            } catch (IOException ignore) {}
        }
    }

    private void unsubscribeWebhook(WebhookManager.WebhookRequestBody webhookRequestBody) {
        WebhookManager webhookManager = (WebhookManager) settings.getManager(Manager.ManagerType.WEBHOOK);
        if (webhookManager != null) {
            webhookManager.remove(webhookRequestBody.getHookUserId());
            log.info("Unsubscribed webhook for user {} due to insufficient balance.", webhookRequestBody.getUserId());
        }
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
            log.error("Get new cash list for "+ ApipApi.HOOK_NEW_CASH_BY_FIDS +" from ES wrong.",e);
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
            log.error("Get new OpReturn list for "+ ApipApi.NEW_OP_RETURN_HOOK_BY_FIDS +" from ES wrong.",e);
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
