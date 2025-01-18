package api;

import constants.ApiNames;
import constants.CodeMessage;
import constants.Strings;
import fcData.FcReplierHttp;
import feip.feipData.Service;
import feip.feipData.serviceParams.DiskParams;
import initial.Initiator;
import tools.JsonTools;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;
import appTools.Settings;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static constants.Strings.SERVICE;

@WebServlet(name = ApiNames.GetService, value = "/"+ApiNames.Version1 +"/"+ApiNames.GetService)
public class GetService extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);

        AuthType authType = AuthType.FREE;

        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult==null){
                return;
            }
            Service service = doRequest();
            replier.setTotal(1L);
            replier.setGot(1L);
            replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
            String data = JsonTools.toJson(service);
            replier.reply0SuccessHttp(data, response);
        }catch (Exception e){
            replier.replyOtherErrorHttp(e.getMessage(),e.getStackTrace(),null);
        }
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplierHttp replier =new FcReplierHttp(Initiator.sid,response);
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null,null);
    }

    private Service doRequest()  {
        try(Jedis jedis = Initiator.jedisPool.getResource()) {
            String key = Settings.addSidBriefToName(Initiator.sid,SERVICE);
            Map<String, String> result = jedis.hgetAll(key);
            return Service.fromMap(result, DiskParams.class);
        }
    }
}
