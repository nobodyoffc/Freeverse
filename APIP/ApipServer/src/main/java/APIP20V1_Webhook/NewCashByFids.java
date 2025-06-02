package APIP20V1_Webhook;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import constants.*;
import data.fcData.ReplyBody;
import handlers.Manager;
import handlers.WebhookManager;
import initial.Initiator;
import server.ApipApiNames;
import server.HttpRequestChecker;
import utils.http.AuthType;
import config.Settings;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = ApipApiNames.NEW_CASH_BY_FIDS, value = "/"+ ApipApiNames.SN_20+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.NEW_CASH_BY_FIDS)
public class NewCashByFids extends HttpServlet {
    private final Settings settings = Initiator.settings;
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType,settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType,settings);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);

        //Do FCDSL other request
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        Map<String, String> other = httpRequestChecker.checkOtherRequestHttp(request, response, authType);
        if (other == null) return;
        //Do this request
        doNewCashByFidsRequest(replier,other,settings, response);

    }

    private void doNewCashByFidsRequest(ReplyBody replier, Map<String, String> otherMap, Settings settings, HttpServletResponse response) {
        String addr =replier.getRequestChecker().getFid();

        Gson gson = new Gson();
        WebhookManager.WebhookRequestBody webhookRequestBody;

        String hookUserId=null;

        WebhookManager webhookHandler = (WebhookManager)settings.getManager(Manager.ManagerType.WEBHOOK);

        try {
            webhookRequestBody = gson.fromJson(otherMap.get(FieldNames.WEBHOOK_REQUEST_BODY), WebhookManager.WebhookRequestBody.class);
            webhookRequestBody.setUserId(addr);
            webhookRequestBody.setMethod(ApipApiNames.NEW_CASH_BY_FIDS);
            webhookRequestBody.makeHookUserId(settings.getSid());

            Map<String, String> dataMap = new HashMap<>();
            switch (webhookRequestBody.getOp()) {
                case Strings.SUBSCRIBE -> {
//                    saveSubscribe(webhookRequestBody,settings);
                    webhookHandler.putWebhookRequestBody(hookUserId, webhookRequestBody);

                    dataMap.put(Strings.OP, Strings.SUBSCRIBE);
                    dataMap.put(Strings.HOOK_USER_ID, hookUserId);
                    replier.setData(dataMap);
                }
                case Strings.UNSUBSCRIBE -> {
//                    deleteWebhook(webhookRequestBody,settings);
                    webhookHandler.remove(hookUserId);
                    dataMap.put(Strings.OP, Strings.UNSUBSCRIBE);
                    dataMap.put(Strings.HOOK_USER_ID, hookUserId);
                    replier.setData(dataMap);
                }
                case Strings.CHECK -> {
//                    String subscription = getWebhookFromRedis(webhookRequestBody.getUserId());
                    WebhookManager.WebhookRequestBody subscription = webhookHandler.getWebhookRequestBody(webhookRequestBody.getUserId());
                    dataMap.put(Strings.OP, Strings.CHECK);
                    if (subscription == null) {
                        dataMap.put(Strings.FOUND, Values.FALSE);
                    } else {
                        dataMap.put(Strings.FOUND, Values.TRUE);
                        dataMap.put(Strings.SUBSCRIBE, subscription.toJson());
                    }
                    replier.setData(dataMap);
                }
                default -> {
                    replier.replyOtherErrorHttp("The op in request body is wrong.", response);
                    return;
                }
            }

            replier.reply0SuccessHttp(response);
        } catch (JsonSyntaxException e) {
            replier.replyOtherErrorHttp(e.getMessage(), response);
        }
    }

//    private void deleteWebhook(WebhookHandler.WebhookRequestBody webhookInfo, Settings settings) {
//        deleteWebhookFromRedis(webhookInfo.getUserId(),settings);
//        deleteWebhookFromEs(webhookInfo.getHookUserId());
//    }

//    private void deleteWebhookFromRedis(String userId) {
//        try(Jedis jedis = jedisPool.getResource()){
//            jedis.select(Constants.RedisDb4Webhook);
//            jedis.hdel(ApipApiNames.NEW_CASH_BY_FIDS,userId);
//            jedis.del(userId);
//        }
//    }

//    private String getWebhookFromRedis(String owner) {
//        try(Jedis jedis = jedisPool.getResource()){
//            jedis.select(Constants.RedisDb4Webhook);
//            return jedis.hget(ApipApiNames.NEW_CASH_BY_FIDS, owner);
//        }
//    }

//    private void saveSubscribe(WebhookHandler.WebhookRequestBody webhookInfo,Settings settings) {
//        addSubscribeToRedis(webhookInfo,settings);
//        saveSubscribeToEs(webhookInfo,settings);
//    }
//
//    private void saveSubscribeToEs(WebhookHandler.WebhookRequestBody webhookInfo,Settings settings) {
//        try {
//            IndexResponse result = esClient.index(i -> i.index(Settings.addSidBriefToName(settings.getSid(), IndicesNames.WEBHOOK)).id(webhookInfo.getHookUserId()).document(webhookInfo));
//            System.out.println("Save webhook subscription into ES: "+result.result().jsonValue());
//        } catch (IOException e) {
//            System.out.println("ES client wrong.");
//        }
//    }
//
//    private void deleteWebhookFromEs(String hookId) {
//        try {
//            esClient.delete(d -> d.index(IndicesNames.WEBHOOK).id(hookId));
//        } catch (IOException e) {
//            System.out.println("ES client wrong.");
//        }
//    }
//
//    private void addSubscribeToRedis(WebhookHandler.WebhookRequestBody webhookRequestBody,Settings settings) {
//        Gson gson = new Gson();
//        String sid = settings.getSid();
//        JedisPool jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
//        try(Jedis jedis = jedisPool.getResource()){
//            jedis.select(Constants.RedisDb4Webhook);
//            String dataJson = gson.toJson(webhookRequestBody);
//            jedis.hset(Settings.addSidBriefToName(sid,webhookRequestBody.getMethod()),webhookRequestBody.getUserId(),dataJson);
//        }
//    }
}
