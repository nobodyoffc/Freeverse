package api;

import config.Settings;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.DiskApiNames;
import server.HttpRequestChecker;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = DiskApiNames.PASTE, value = "/" + DiskApiNames.PASTE + "/" + DiskApiNames.VER_1)
public class Paste extends HttpServlet {

    private final Settings settings = Initiator.settings;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        AuthType authType = AuthType.FC_SIGN_URL;

        // Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }

        String sessionName = httpRequestChecker.getSessionName();
        if (sessionName == null) {
            replier.replyHttp(CodeMessage.Code1002SessionNameMissed, response);
            return;
        }

        // Retrieve from Redis
        retrieveFromRedis(sessionName, replier, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        AuthType authType = AuthType.ENCRYPTED;

        // Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }

        String sessionName = httpRequestChecker.getSessionName();
        if (sessionName == null) {
            replier.replyHttp(CodeMessage.Code1002SessionNameMissed, response);
            return;
        }

        // Retrieve from Redis
        retrieveFromRedis(sessionName, replier, response);
    }

    private void retrieveFromRedis(String sessionName, ReplyBody replier, HttpServletResponse response) throws IOException {
        JedisPool jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);
        if (jedisPool == null) {
            replier.replyOtherErrorHttp("Redis service is not available.", response);
            return;
        }

        String redisKey = settings.getSid() + "_clipboard_" + sessionName;

        try (Jedis jedis = jedisPool.getResource()) {
            String data = jedis.get(redisKey);

            if (data == null) {
                replier.replyOtherErrorHttp("No clipboard data found or data has expired.", response);
                return;
            }

            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("data", data);

            // Get TTL (time to live) in seconds
            Long ttl = jedis.ttl(redisKey);
            if (ttl > 0) {
                dataMap.put("ttl", String.valueOf(ttl));
            }

            replier.reply0SuccessHttp(dataMap, response);
        } catch (Exception e) {
            replier.replyOtherErrorHttp("Failed to retrieve data from Redis: " + e.getMessage(), response);
        }
    }
}
