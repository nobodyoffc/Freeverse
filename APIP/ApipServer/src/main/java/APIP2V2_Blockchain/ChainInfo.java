package APIP2V2_Blockchain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import constants.Strings;
import fcData.FcReplier;
import fch.fchData.FchChainInfo;
import initial.Initiator;
import javaTools.ObjectTools;
import javaTools.http.AuthType;
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
import java.util.Map;

import static constants.FieldNames.HEIGHT;

@WebServlet(name = ApiNames.ChainInfo, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version2 +"/"+ApiNames.ChainInfo)
public class ChainInfo extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FREE;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws ServletException, IOException {
        FcReplier replier = new FcReplier(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }

            String height=null;
            if(request.getParameter(HEIGHT)!=null)height = request.getParameter(HEIGHT);
            else if(null!=requestCheckResult.getRequestBody().getFcdsl().getOther()){
                Object other = requestCheckResult.getRequestBody().getFcdsl().getOther();
                Map<String,String> otherMap = ObjectTools.objectToMap(other,String.class,String.class);
                if(otherMap!=null)
                    height = otherMap.get(HEIGHT);
            }

            FchChainInfo freecashInfo = new FchChainInfo();
            if (height == null) {
                freecashInfo.infoBest(Initiator.naSaRpcClient);
                replier.setBestHeight(Long.valueOf(freecashInfo.getHeight()));
            } else {
                freecashInfo.infoByHeight(Long.parseLong(height), Initiator.esClient);
                replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
            }
            replier.replySingleDataSuccess(freecashInfo,jedis);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
