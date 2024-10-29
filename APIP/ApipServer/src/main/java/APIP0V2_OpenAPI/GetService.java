package APIP0V2_OpenAPI;

import clients.esClient.EsTools;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import constants.Strings;
import fcData.FcReplierHttp;
import feip.feipData.Service;
import initial.Initiator;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.Strings.SERVICE;

@WebServlet(name = ApiNames.GetService, value = "/"+ApiNames.Version1 +"/"+ApiNames.GetService)
public class GetService extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);

        AuthType authType = AuthType.FREE;

        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false);
            if (requestCheckResult==null){
                return;
            }
            Service service = doRequest(response,replier);
            if(service!=null) {
                replier.setTotal(1L);
                replier.setGot(1L);
                replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
                String data = JsonTools.toJson(service);
                replier.reply0SuccessHttp(data, response);
            }
        }catch (Exception e){
            replier.replyOtherErrorHttp(e.getMessage(),null,null);
        }
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplierHttp replier =new FcReplierHttp(Initiator.sid,response);
        replier.replyHttp(ReplyCodeMessage.Code1017MethodNotAvailable,null,null);
    }

    private Service doRequest(HttpServletResponse response, FcReplierHttp replier)  {
        try {
            return EsTools.getById(Initiator.esClient, SERVICE, Initiator.sid, Service.class);
        } catch (IOException e) {
            replier.replyOtherErrorHttp("EsClient wrong:"+e.getMessage(),response);
            return null;
        }
    }
}
