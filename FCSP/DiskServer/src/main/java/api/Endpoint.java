package api;

import apip.apipData.WebhookPushBody;
import com.google.gson.Gson;
import constants.ApiNames;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import server.Settings;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static constants.Constants.UserDir;
import static constants.Strings.*;
import static fcData.Signature.symSign;

@WebServlet(name = "endpoint", value = "/"+ApiNames.Endpoint)
public class Endpoint extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
            String hookUserId = jedis.hget(webhookPushBody.getSessionName(),HOOK_USER_ID);
            String pushedHookUserId = webhookPushBody.getHookUserId();
            if(!hookUserId.equals(pushedHookUserId))return;
        }catch (Exception ignore){
            return;
        }

        String sign = symSign(webhookPushBody.getData(),sessionKey);
        if(!sign.equals(webhookPushBody.getSign()))return;

        String method = webhookPushBody.getMethod();
        switch (method){
            case ApiNames.NewCashByFids ->{
                int i = 0;
                File file;
                while(true) {
                    file = new File(System.getProperty(UserDir) + Settings.addSidBriefToName(Initiator.sid,method), method+i+DOT_JSON);
                    if(!file.exists())break;
                    i++;
                }
                try (FileOutputStream fos = new FileOutputStream(file)){
                    fos.write(webhookPushBody.toJson().getBytes());
                }
//                response.getWriter().write(String.valueOf(webhookPushBody.getSinceHeight()));
            }
            case ApiNames.OpReturnByFids ->{}
            default -> {}
        }
    }
}
