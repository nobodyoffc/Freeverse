package api;

import server.ApipApiNames;
import constants.CodeMessage;
import constants.Strings;
import fcData.ReplyBody;
import feip.feipData.Service;
import feip.feipData.serviceParams.DiskParams;
import initial.Initiator;
import utils.JsonUtils;
import utils.http.AuthType;
import redis.clients.jedis.Jedis;
import server.HttpRequestChecker;
import appTools.Settings;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static constants.Strings.SERVICE;

@WebServlet(name = ApipApiNames.GET_SERVICE, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GET_SERVICE)
public class GetService extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ReplyBody replier = new ReplyBody(Initiator.settings);

        AuthType authType = AuthType.FREE;

        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(Initiator.settings, replier);
            httpRequestChecker.checkRequestHttp(request, response, authType);
            if (httpRequestChecker ==null){
                return;
            }
            Service service = doRequest();
            replier.setTotal(1L);
            replier.setGot(1L);
            replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
            String data = JsonUtils.toJson(service);
            replier.reply0SuccessHttp(data, response);
        }catch (Exception e){
            replier.replyOtherErrorHttp(e.getMessage(),e.getStackTrace(), response);
        }
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier =new ReplyBody(Initiator.settings);
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,response);
    }

    private Service doRequest()  {
        try(Jedis jedis = Initiator.jedisPool.getResource()) {
            String key = Settings.addSidBriefToName(Initiator.sid,SERVICE);
            Map<String, String> result = jedis.hgetAll(key);
            return Service.fromMap(result, DiskParams.class);
        }
    }
}
