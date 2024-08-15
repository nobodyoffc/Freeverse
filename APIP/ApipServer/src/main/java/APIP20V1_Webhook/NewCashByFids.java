package APIP20V1_Webhook;

import apip.apipData.WebhookRequestBody;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import constants.*;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;
import server.Settings;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = ApiNames.NewCashByFids, value = "/"+ApiNames.SN_20+"/"+ApiNames.Version2 +"/"+ApiNames.NewCashByFids)
public class NewCashByFids extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request,response,authType, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request,response,authType, Initiator.jedisPool);
    }
    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid,response);
        try(Jedis jedis = jedisPool.getResource()) {
            //Do FCDSL other request
            Object other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis);
            if (other == null) return;
            //Do this request
            doNewCashByFidsRequest(replier, jedis, other);
        }
    }

    private void doNewCashByFidsRequest(FcReplier replier, Jedis jedis, Object other) {
        String addr =replier.getRequestCheckResult().getFid();

        Gson gson = new Gson();
        WebhookRequestBody webhookRequestBody;

        String hookUserId;

        try {
            webhookRequestBody = gson.fromJson(gson.toJson(other), WebhookRequestBody.class);

            webhookRequestBody.setUserName(addr);
            webhookRequestBody.setMethod(ApiNames.NewCashByFids);
            hookUserId = WebhookRequestBody.makeHookUserId(Initiator.sid, addr, ApiNames.NewCashByFids);
            webhookRequestBody.setHookUserId(hookUserId);
            Map<String, String> dataMap = new HashMap<>();
            switch (webhookRequestBody.getOp()) {
                case Strings.SUBSCRIBE -> {
                    saveSubscribe(webhookRequestBody);
                    dataMap.put(Strings.OP, Strings.SUBSCRIBE);
                    dataMap.put(Strings.HOOK_USER_ID, hookUserId);
                    replier.setData(dataMap);
                }
                case Strings.UNSUBSCRIBE -> {
                    deleteWebhook(webhookRequestBody);
                    dataMap.put(Strings.OP, Strings.UNSUBSCRIBE);
                    dataMap.put(Strings.HOOK_USER_ID, hookUserId);
                    replier.setData(dataMap);
                }
                case Strings.CHECK -> {
                    String subscription = getWebhookFromRedis(webhookRequestBody.getUserName());
                    dataMap.put(Strings.OP, Strings.CHECK);
                    if (subscription == null) {
                        dataMap.put(Strings.FOUND, Values.FALSE);
                    } else {
                        dataMap.put(Strings.FOUND, Values.TRUE);
                        dataMap.put(Strings.SUBSCRIBE, subscription);
                    }
                    replier.setData(dataMap);
                }
                default -> {
                    replier.replyOtherError("The op in request body is wrong.", null, jedis);
                    return;
                }
            }
        } catch (JsonSyntaxException e) {
            replier.replyOtherError(e.getMessage(), null, jedis);
            return;
        }
        replier.reply0Success(jedis);
    }

    private void deleteWebhook(WebhookRequestBody webhookInfo) {
        deleteWebhookFromRedis(webhookInfo.getUserName());
        deleteWebhookFromEs(webhookInfo.getHookUserId());
    }

    private void deleteWebhookFromRedis(String owner) {
        try(Jedis jedis = Initiator.jedisPool.getResource()){
            jedis.select(Constants.RedisDb4Webhook);
            jedis.hdel(ApiNames.NewCashByFids,owner);
            jedis.del(owner);
        }
    }

    private String getWebhookFromRedis(String owner) {
        try(Jedis jedis = Initiator.jedisPool.getResource()){
            jedis.select(Constants.RedisDb4Webhook);
            return jedis.hget(ApiNames.NewCashByFids, owner);
        }
    }

    private void saveSubscribe(WebhookRequestBody webhookInfo) {
        addSubscribeToRedis(webhookInfo);
        saveSubscribeToEs(webhookInfo);
    }

    private void saveSubscribeToEs(WebhookRequestBody webhookInfo) {
        try {
            Initiator.esClient.index(i -> i.index(Settings.addSidBriefToName(Initiator.sid,IndicesNames.WEBHOOK)).id(webhookInfo.getHookUserId()).document(webhookInfo));
        } catch (IOException e) {
            System.out.println("ES client wrong.");
        }
    }

    private void deleteWebhookFromEs(String hookId) {
        try {
            Initiator.esClient.delete(d -> d.index(IndicesNames.WEBHOOK).id(hookId));
        } catch (IOException e) {
            System.out.println("ES client wrong.");
        }
    }

    private void addSubscribeToRedis(WebhookRequestBody data) {
        Gson gson = new Gson();
        try(Jedis jedis = Initiator.jedisPool.getResource()){
            jedis.select(Constants.RedisDb4Webhook);
            String dataJson = gson.toJson(data);
            jedis.hset(data.getMethod(),data.getUserName(),dataJson);
        }
    }
}
