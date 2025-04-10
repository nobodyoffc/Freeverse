package server.order;

import appTools.Shower;
import constants.FieldNames;
import feip.feipData.serviceParams.Params;
import appTools.Inputer;
import clients.ApipClient;
import configure.ApiAccount;
import server.ApipApiNames;
import constants.Strings;
import utils.Hex;
import utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import appTools.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static appTools.Inputer.askIfYes;
import static server.ApipApiNames.NEW_ORDER;
import static constants.Strings.*;

public class Order {
    private String orderId;//cash id
    private String fromFid;
    private String toFid;
    private String via;
    private long amount;
    private long time;
    private String txId;
    private long txIndex;
    private long height;
    final static Logger log = LoggerFactory.getLogger(Order.class);
    public static final String MAPPINGS = "{\"mappings\":{\"properties\":{\"orderId\":{\"type\":\"keyword\"},\"fromFid\":{\"type\":\"keyword\"},\"toFid\":{\"type\":\"keyword\"},\"via\":{\"type\":\"keyword\"},\"amount\":{\"type\":\"long\"},\"time\":{\"type\":\"long\"},\"txId\":{\"type\":\"keyword\"},\"txIndex\":{\"type\":\"long\"},\"height\":{\"type\":\"long\"}}}}";
    public static OrderOpReturn getJsonBuyOrder(){
        return getJsonBuyOrder(null);
    }
    public static OrderOpReturn getJsonBuyOrder(String sid){
        OrderOpReturn orderOpReturn = new OrderOpReturn();
        OrderOpReturnData data = new OrderOpReturnData();
        if(sid!=null){
            data.setOp("buy");
            data.setSid(sid);
        }
        data.setVia("The via FID");
        orderOpReturn.setData(data);
        orderOpReturn.setType("apip");
        orderOpReturn.setSn("0");
        orderOpReturn.setName("OpenAPI");
        orderOpReturn.setVer("2");

        return orderOpReturn;
    }

    public static boolean checkWebhook(String hookMethod, Params params, ApiAccount apipAccount, BufferedReader br, JedisPool jedisPool) {
        System.out.println("Check webhook...");
        String urlHead = params.getUrlHead();
        if(!urlHead.endsWith("/"))urlHead=urlHead+"/";
        String endpoint = urlHead+ ApipApiNames.VERSION_1 +"/"+ NEW_ORDER;
        ApipClient apipClient = (ApipClient) apipAccount.getClient();

        Map<String, String> dataMap = apipClient.checkSubscription(hookMethod, endpoint);
        if(dataMap==null || dataMap.get(FOUND)==null){
            return false;
        }
        String result = dataMap.get(FOUND);
        String webhookRequestDataStr;
        if(result.equalsIgnoreCase("true")){
            webhookRequestDataStr = dataMap.get(Strings.SUBSCRIBE);
            Shower.printUnderline(10);
            System.out.println(JsonUtils.strToNiceJson(webhookRequestDataStr));
            Shower.printUnderline(10);
            if(!askIfYes(br,"Here is your subscription. Change it?")){
                return true;
            }
        }
        List<String> fidList = new ArrayList<>();
        fidList.add(params.getDealer());
        Map<String,Object> map = new HashMap<>();
        map.put(FieldNames.IDS,fidList);
        String hookUserId = apipClient.subscribeWebhook(hookMethod, map, endpoint);
        if(hookUserId!=null){
            try(Jedis jedis = jedisPool.getResource()){
                jedis.select(1);
                String sessionName = apipAccount.getSession().getId();
                jedis.hset(sessionName, FieldNames.SESSION_KEY, Hex.toHex(apipClient.getSessionKey()));
                jedis.hset(sessionName, hookMethod+"_"+HOOK_USER_ID,hookUserId);
            }
            System.out.println("Webhook is subscribed:"+hookUserId);
            return true;
        }
        else System.out.println("Failed to subscribe the webhook from "+ apipAccount.getApiUrl());
        return false;
    }

    public static void setNPrices(String sid, List<String> apiList, JedisPool jedisPool, BufferedReader br, boolean reset) {
        try(Jedis jedis = jedisPool.getResource()) {
            if(jedis.exists(Settings.addSidBriefToName(sid, Strings.N_PRICE)))
                if(!reset)return;
            Map<Integer, String> apiMap = apiListToMap(apiList);
            showAllAPIs(apiMap);
            while (true) {
                System.out.println("""
                        Set nPrices:
                        \t'a' to set all nPrices,
                        \t'one' to set all nPrices by 1,
                        \t'zero' to set all nPrices by 0,
                        \tan integer to set the corresponding API,
                        \tor 'q' to quit.\s""");
                String str = null;
                try {
                    str = br.readLine();
                    if ("".equals(str)) str = br.readLine();
                    if (str.equals("q")) return;
                    if (str.equals("a")) {
                        setAllNPrices(apiMap, br, sid, jedisPool);
                        System.out.println("Done.");
                        return;
                    }
                } catch (Exception e) {
                    log.error("Set nPrice wrong. ", e);
                }
                if (str == null) {
                    log.error("Set nPrice failed. ");
                    continue;
                }

                if (str.equals("one")) {
                    for (int i = 0; i < apiMap.size(); i++) {
                        jedis.hset(Settings.addSidBriefToName(sid, Strings.N_PRICE), apiMap.get(i + 1), "1");
                    }
                    System.out.println("Done.");
                    return;
                }
                if (str.equals("zero")) {
                    for (int i = 0; i < apiMap.size(); i++) {
                        jedis.hset(Settings.addSidBriefToName(sid, Strings.N_PRICE), apiMap.get(i + 1), "0");
                    }
                    System.out.println("Done.");
                    return;
                }
                try {
                    int i = Integer.parseInt(str);
                    if (i > apiMap.size()) {
                        System.out.println("The integer should be no bigger than " + apiMap.size());
                    } else {
                        setNPrice(i, apiMap, br, sid, jedisPool);
                        System.out.println("Done.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Wrong input.");
                }
            }
        }
    }

    public static void setAllNPrices(Map<Integer, String> apiMap, BufferedReader br, String sid, JedisPool jedisPool) throws IOException {
        for (int i : apiMap.keySet()) {
            setNPrice(i, apiMap,  br, sid, jedisPool);
        }
    }

    public static void setNPrice(int i, Map<Integer, String> apiMap, BufferedReader br, String sid, JedisPool jedisPool) throws IOException {
        String apiName = apiMap.get(i);
        while (true) {
            System.out.println("Input the multiple number of API " + apiName + ":");
            String str = br.readLine();
            try(Jedis jedis = jedisPool.getResource()) {
                int n = Integer.parseInt(str);
                jedis.hset(Settings.addSidBriefToName(sid,Strings.N_PRICE), apiName, String.valueOf(n));
                return;
            } catch (Exception e) {
                System.out.println("Wong input.");
            }
        }
    }

    public static void showAllAPIs(Map<Integer, String> apiMap) {
        System.out.println("API list:");
        for (int i = 1; i <= apiMap.size(); i++) {
            System.out.println(i + ". " + apiMap.get(i));
        }
    }

    public static Map<Integer, String> apiListToMap(List<String> apiList) {

        Map<Integer, String> apiMap = new HashMap<>();
        for (int i = 0; i < apiList.size(); i++) apiMap.put(i + 1, apiList.get(i));
        return apiMap;
    }

    public static void resetNPrices(BufferedReader br, String sid, JedisPool jedisPool) {
        try(Jedis jedis = jedisPool.getResource()) {
            Map<String, String> nPriceMap = jedis.hgetAll(Settings.addSidBriefToName(sid,Strings.N_PRICE));
            for (String name : nPriceMap.keySet()) {
                String ask = "The price multiplier of " + name + " is " + nPriceMap.get(name) + ". Reset it? y/n";
                int input = Inputer.inputInt(br, ask, 0);
                if (input != 0)
                    jedis.hset(Settings.addSidBriefToName(sid,Strings.N_PRICE), name, String.valueOf(input));
            }
        }
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public long getTxIndex() {
        return txIndex;
    }

    public void setTxIndex(long txIndex) {
        this.txIndex = txIndex;
    }


    public String getFromFid() {
        return fromFid;
    }

    public void setFromFid(String fromFid) {
        this.fromFid = fromFid;
    }

    public String getToFid() {
        return toFid;
    }

    public void setToFid(String toFid) {
        this.toFid = toFid;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }
}
