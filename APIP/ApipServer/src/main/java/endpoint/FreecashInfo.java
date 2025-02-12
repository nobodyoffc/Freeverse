package endpoint;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import fcData.FcReplierHttp;
import fch.fchData.FchChainInfo;
import initial.Initiator;
import tools.JsonTools;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@WebServlet(name = ApiNames.FreecashInfo, value = "/"+ApiNames.FreecashInfo)
public class FreecashInfo extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FREE;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws ServletException, IOException {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult == null) {
                return;
            }
            FchChainInfo freecashInfo = new FchChainInfo();
            freecashInfo.infoBest(Initiator.naSaRpcClient);
            response.getWriter().write(JsonTools.toNiceJson(freecashInfo));
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}