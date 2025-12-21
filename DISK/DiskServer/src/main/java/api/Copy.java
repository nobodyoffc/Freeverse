package api;

import config.Settings;
import constants.CodeMessage;
import constants.Constants;
import data.fcData.ReplyBody;
import data.feipData.Service;
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
import java.util.Map;

@WebServlet(name = DiskApiNames.COPY, value = "/" + DiskApiNames.COPY + "/" + DiskApiNames.VER_1)
public class Copy extends HttpServlet {

    public static final int MAX_DATA_SIZE = 10 * Constants.M_BYTES;
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

        // Get parameters from URL
        String data = request.getParameter("data");
        String expiryStr = request.getParameter("expiry");

        if (data == null) {
            replier.replyOtherErrorHttp("The 'data' parameter is required.", response);
            return;
        }

        if (expiryStr == null) {
            replier.replyOtherErrorHttp("The 'expiry' parameter is required.", response);
            return;
        }

        int expiry;
        try {
            expiry = Integer.parseInt(expiryStr);
        } catch (NumberFormatException e) {
            replier.replyOtherErrorHttp("The 'expiry' parameter must be a valid integer.", response);
            return;
        }

        if (expiry <= 0) {
            replier.replyOtherErrorHttp("The 'expiry' parameter must be greater than 0.", response);
            return;
        }

        String sessionName = httpRequestChecker.getSessionName();
        if (sessionName == null) {
            replier.replyHttp(CodeMessage.Code1002SessionNameMissed, response);
            return;
        }

        // Save to Redis
        saveToRedis(sessionName, data, expiry, replier, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        AuthType authType = AuthType.SYMKEY_ENCRYPT;

        // Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }

        // Get data from request body
        if (httpRequestChecker.getRequestBody() == null ||
            httpRequestChecker.getRequestBody().getFcdsl() == null ||
            httpRequestChecker.getRequestBody().getFcdsl().getOther() == null) {
            replier.replyHttp(CodeMessage.Code1003BodyMissed, "Request body data is required.", response);
            return;
        }

        Map<String, String> other = httpRequestChecker.getRequestBody().getFcdsl().getOther();
        String data = other.get("data");
        String expiryStr = other.get("expiry");

        if (data == null) {
            replier.replyOtherErrorHttp("The 'data' field is required in request body.", response);
            return;
        }

        if (data.length()> MAX_DATA_SIZE) {
            replier.replyOtherErrorHttp("The data can not large than "+MAX_DATA_SIZE+" M", response);
            return;
        }

        if (expiryStr == null) {
            replier.replyOtherErrorHttp("The 'expiry' field is required in request body.", response);
            return;
        }

        int expiry;
        try {
            expiry = Integer.parseInt(expiryStr);
        } catch (NumberFormatException e) {
            replier.replyOtherErrorHttp("The 'expiry' field must be a valid integer.", response);
            return;
        }

        if (expiry <= 0) {
            replier.replyOtherErrorHttp("The 'expiry' field must be greater than 0.", response);
            return;
        }

        String sessionName = httpRequestChecker.getSessionName();
        if (sessionName == null) {
            replier.replyHttp(CodeMessage.Code1002SessionNameMissed, response);
            return;
        }

        // Save to Redis
        saveToRedis(sessionName, data, expiry, replier, response);
    }

    private void saveToRedis(String sessionName, String data, int expiry, ReplyBody replier, HttpServletResponse response) throws IOException {
        JedisPool jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
        if (jedisPool == null) {
            replier.replyOtherErrorHttp("Redis service is not available.", response);
            return;
        }

        String redisKey = settings.getSid() + "_clipboard_" + sessionName;

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(redisKey, expiry, data);
            replier.reply0SuccessHttp("Data copied to clipboard successfully.", response);
        } catch (Exception e) {
            replier.replyOtherErrorHttp("Failed to save data to Redis: " + e.getMessage(), response);
        }
    }
}
