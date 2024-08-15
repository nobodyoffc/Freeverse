package server.balance;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import constants.Strings;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;
import static clients.redisClient.RedisTools.readLong;
import static server.Settings.addSidBriefToName;

public class BalanceInfo {
    private String userBalanceMapStr;
    private Long bestHeight;
    private String consumeViaMapStr;
    private String orderViaMapStr;
    private String rewardPendingMapStr;


    private static final Logger log = LoggerFactory.getLogger(BalanceInfo.class);
//    public static final String BALANCE_BACKUP_JSON = "balanceBackup.json";

    public static final String MAPPINGS = "{\"mappings\":{\"properties\":{\"userBalanceMapStr\":{\"type\":\"keyword\",\"ignore_above\":256},\"bestHeight\":{\"type\":\"long\"},\"consumeViaMapStr\":{\"type\":\"keyword\",\"ignore_above\":256},\"orderViaMapStr\":{\"type\":\"keyword\",\"ignore_above\":256},\"rewardPendingMapStr\":{\"type\":\"keyword\",\"ignore_above\":256}}}}";
    public static void recoverUserBalanceFromFile(String fileName,JedisPool jedisPool) {
        try(Jedis jedis = jedisPool.getResource()) {
            BalanceInfo balanceInfo = JsonTools.readObjectFromJsonFile(null,fileName, BalanceInfo.class);
            if(balanceInfo==null)return;
            recoverBalanceToRedis(balanceInfo, jedis);
        } catch (IOException e) {
            log.debug("Failed to recoverUserBalanceFromFile: "+fileName);
        }
    }

    public String getRewardPendingMapStr() {
        return rewardPendingMapStr;
    }

    public void setRewardPendingMapStr(String rewardPendingMapStr) {
        this.rewardPendingMapStr = rewardPendingMapStr;
    }

    public static void deleteOldBalance(String sid,ElasticsearchClient esClient) {
        String index = addSidBriefToName(sid,BALANCE);
        long BALANCE_BACKUP_KEEP_MINUTES=144000;
        long height = readLong(BEST_HEIGHT)-BALANCE_BACKUP_KEEP_MINUTES;
        try {
            esClient.deleteByQuery(d -> d.index(index).query(q -> q.range(r -> r.field(BEST_HEIGHT).lt(JsonData.of(height)))));
        }catch (Exception e){
            log.error("Delete old balances in ES error",e);
        }
    }
    public String getConsumeViaMapStr() {
        return consumeViaMapStr;
    }

    public void setConsumeViaMapStr(String consumeViaMapStr) {
        this.consumeViaMapStr = consumeViaMapStr;
    }

    public static void recoverUserBalanceFromEs(String sid, ElasticsearchClient esClient, JedisPool jedisPool) {

        String index = addSidBriefToName(sid,BALANCE);

        BalanceInfo balanceInfo = null;
        try(Jedis jedis = jedisPool.getResource()) {
            try {
                SearchResponse<BalanceInfo> result = esClient.search(s -> s.index(index).size(1).sort(so -> so.field(f -> f.field(BEST_HEIGHT).order(SortOrder.Desc))), BalanceInfo.class);
                if (result.hits().hits().size() == 0) {
                    System.out.println("No backup found in ES.");
                    return;
                }
                balanceInfo = result.hits().hits().get(0).source();
                if(balanceInfo==null)return;
            } catch (Exception e) {
                log.error("Get balance from ES error when recovering balances:{}", e.getMessage());
            }


            if (balanceInfo != null) {
                recoverBalanceToRedis(balanceInfo, jedis);
            } else {
                log.debug("Failed recovered balances from ES.");
            }
//
//            if (viaTStr != null) {
//                Map<String, String> viaTMap = gson.fromJson(viaTStr, new TypeToken<HashMap<String, String>>() {
//                }.getType());
//                for (String id : viaTMap.keySet()) {
//                    jedis.hset(BalanceManager.sidBrief + "_" + CONSUME_VIA, id, viaTMap.get(id));
//                }
//                log.debug("Consuming ViaT recovered from ES.");
//            } else {
//                log.debug("Failed recovered consuming ViaT from ES.");
//            }
        }
    }

    public static void recoverBalanceToRedis(BalanceInfo balanceInfo, Jedis jedis) {
        Gson gson = new Gson();
        Map<String, String> balanceMap = gson.fromJson(balanceInfo.getUserBalanceMapStr(), new TypeToken<HashMap<String, String>>() {
        }.getType());

        Map<String, String> orderViaMap = gson.fromJson(balanceInfo.getOrderViaMapStr(), new TypeToken<HashMap<String, String>>() {
        }.getType());

        Map<String, String> consumeViaMap = gson.fromJson(balanceInfo.getConsumeViaMapStr(), new TypeToken<HashMap<String, String>>() {
        }.getType());

        Map<String, String> rewardPendingMap = gson.fromJson(balanceInfo.getRewardPendingMapStr(), new TypeToken<HashMap<String, String>>() {
        }.getType());


        for (String id : balanceMap.keySet()) {
            jedis.hset(BalanceManager.sidBrief + "_" + Strings.BALANCE, id, balanceMap.get(id));
        }
        for (String id : orderViaMap.keySet()) {
            jedis.hset(BalanceManager.sidBrief + "_" + ORDER_VIA, id, orderViaMap.get(id));
        }
        for (String id : consumeViaMap.keySet()) {
            jedis.hset(BalanceManager.sidBrief + "_" + CONSUME_VIA, id, consumeViaMap.get(id));
        }
        for (String id : rewardPendingMap.keySet()) {
            jedis.hset(BalanceManager.sidBrief + "_" + REWARD_PENDING_MAP, id, rewardPendingMap.get(id));
        }
        jedis.hset(BalanceManager.sidBrief + "_" + CONSUME_VIA, BEST_HEIGHT, String.valueOf(balanceInfo.getBestHeight()));

        log.debug("Balances recovered from ES.");
    }

    public static void backupBalance(String sid,ElasticsearchClient esClient,JedisPool jedisPool)  {
        try(Jedis jedis0Common = jedisPool.getResource()) {
            Map<String, String> balanceMap = jedis0Common.hgetAll(Settings.addSidBriefToName(sid,Strings.BALANCE));
            Map<String, String> consumeViaMap = jedis0Common.hgetAll(Settings.addSidBriefToName(sid,CONSUME_VIA));
            Map<String, String> orderViaMap = jedis0Common.hgetAll(Settings.addSidBriefToName(sid,ORDER_VIA));
            Map<String, String> pendingStrMap = jedis0Common.hgetAll(Settings.addSidBriefToName(sid,REWARD_PENDING_MAP));
            Gson gson = new Gson();

            String balanceStr = gson.toJson(balanceMap);
            String consumeViaStr = gson.toJson(consumeViaMap);
            String orderViaStr = gson.toJson(orderViaMap);
            String pendingStr = gson.toJson(pendingStrMap);

            BalanceInfo balanceInfo = new BalanceInfo();

            balanceInfo.setUserBalanceMapStr(balanceStr);
            balanceInfo.setConsumeViaMapStr(consumeViaStr);
            balanceInfo.setOrderViaMapStr(orderViaStr);
            balanceInfo.setRewardPendingMapStr(pendingStr);

            long bestHeight = readLong(BEST_HEIGHT);

            balanceInfo.setBestHeight(bestHeight);

            backupBalanceToEx(sid,esClient, balanceInfo, bestHeight);

            String fileName = Settings.getLocalDataDir(sid)+BALANCE;

            backupBalanceToFile(balanceInfo, fileName);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void backupBalanceToEx(String sid,ElasticsearchClient esClient, BalanceInfo balanceInfo, long bestHeight) throws IOException {
        String index = addSidBriefToName(sid,BALANCE).toLowerCase();
        IndexResponse result = null;
        try {
            result = esClient.index(i -> i.index(index).id(String.valueOf(bestHeight)).document(balanceInfo));
        } catch (IOException e) {
            log.error("Read ES wrong.", e);
        }

        if (result != null) {
            log.debug("User balance backup to ElasticSearch: " + result.result().toString());
        }
    }

    private static void backupBalanceToFile(BalanceInfo balanceInfo, String fileName) {
        String finalFileName = null;

        try {
            // Check if balance29.json exists
            File oldestFile = new File(fileName + 29 + DOT_JSON);
            if (oldestFile.exists()) {
                // Delete the oldest file
                oldestFile.delete();
            }

            // Rename the files from balance28.json to balance0.json
            for (int i = 28; i >=0; i--) {
                File currentFile = new File(fileName + i + DOT_JSON);
                if (currentFile.exists()) {
                    File newFile = new File(fileName + (i + 1) + DOT_JSON);
                    currentFile.renameTo(newFile);
                }
            }

            // Write the new balance info to balance0.json
            JsonTools.writeObjectToJsonFile(balanceInfo, fileName + 0 + DOT_JSON, false);
            finalFileName = fileName + 0 + DOT_JSON;

        } catch (Exception e) {
            log.error("Failed to backup user balance to file", e);
        }

        log.debug("User balance is backed up to " + finalFileName);
    }

    public Long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }

    public String getUserBalanceMapStr() {
        return userBalanceMapStr;
    }

    public void setUserBalanceMapStr(String userBalanceMapStr) {
        this.userBalanceMapStr = userBalanceMapStr;
    }

    public String getOrderViaMapStr() {
        return orderViaMapStr;
    }

    public void setOrderViaMapStr(String orderViaMapStr) {
        this.orderViaMapStr = orderViaMapStr;
    }

}
