package api;

import apip.apipData.Sort;
import apip.apipData.WebhookPushBody;
import clients.fcspClient.DiskItem;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.gson.Gson;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import server.FcdslRequestHandler;
import server.RequestCheckResult;
import server.RequestChecker;
import server.Settings;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import static constants.ApiNames.LIST;
import static constants.FieldNames.*;
import static constants.Strings.*;
import static constants.Strings.DOT_JSON;
import static fcData.Signature.symSign;

@WebServlet(name = ApiNames.WebhookPoint, value ="/"+ApiNames.Version1 +"/"+ ApiNames.WebhookPoint)
public class WebhookPoint extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write("GET is not available for the API Endpoint.");
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        System.out.println("Endpoint active...");
        WebhookPushBody webhookPushBody;

        byte[] requestBodyBytes = request.getInputStream().readAllBytes();
        try {
            webhookPushBody = new Gson().fromJson(new String(requestBodyBytes), WebhookPushBody.class);
            if (webhookPushBody ==null) return;
        }catch (Exception ignore){
            return;
        }

        String sessionKey;
        try(Jedis jedis = Initiator.jedisPool.getResource()){
            jedis.select(1);
            sessionKey =jedis.hget(webhookPushBody.getSessionName(),SESSION_KEY);
            if(sessionKey==null)return;
            String hookUserId = jedis.hget(webhookPushBody.getSessionName(),webhookPushBody.getMethod()+"_"+HOOK_USER_ID);
            String pushedHookUserId = webhookPushBody.getHookUserId();
            if(!hookUserId.equals(pushedHookUserId))return;
        }catch (Exception ignore){
            return;
        }

        String sign = symSign(webhookPushBody.getData(),sessionKey);
        if(!sign.equals(webhookPushBody.getSign()))return;

        String method = webhookPushBody.getMethod();

        int i = 0;
        File file;
        while(true) {
            file = new File(Initiator.listenPath,method+i+DOT_JSON);//new File(System.getProperty(UserDir) + Settings.addSidBriefToName(Initiator.sid,method), method+i+DOT_JSON);
            if(!file.exists())break;
            i++;
        }
        try (FileOutputStream fos = new FileOutputStream(file)){
            System.out.println("* Write webhook data "+ webhookPushBody.getHookUserId()+" to file:"+file.getName());
            fos.write(webhookPushBody.toJson().getBytes());
            System.out.println("* Wrote.");
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
