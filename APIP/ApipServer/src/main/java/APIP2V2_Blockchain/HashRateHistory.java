package APIP2V2_Blockchain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
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

import static constants.Strings.BEST_HEIGHT;
import static fch.fchData.FchChainInfo.MAX_REQUEST_COUNT;

@WebServlet(name = ApiNames.HashRateHistory, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version2 +"/"+ApiNames.HashRateHistory)
public class HashRateHistory extends HttpServlet {


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
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

            long startTime = 0;
            long endTime = 0;
            int count = 0;
            if(requestCheckResult.getRequestBody()!=null && requestCheckResult.getRequestBody().getFcdsl()!=null && requestCheckResult.getRequestBody().getFcdsl().getOther()!=null) {
                Object other =  requestCheckResult.getRequestBody().getFcdsl().getOther();
                Map<String, String> paramMap = ObjectTools.objectToMap(other,String.class,String.class);//DataGetter.getStringMap(other);
                String endTimeStr = paramMap.get("endTime");
                String startTimeStr = paramMap.get("startTime");
                String countStr = paramMap.get("count");
                if (startTimeStr != null) startTime = Long.parseLong(startTimeStr);
                if (endTimeStr != null) endTime = Long.parseLong(endTimeStr);
                if (countStr != null) count = Integer.parseInt(countStr);
            }

            if (count > MAX_REQUEST_COUNT){
                replier.replyOtherError( "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT,null,jedis);
                return;
            }


            Map<Long, String> hist = FchChainInfo.hashRateHistory(startTime, endTime, count, Initiator.esClient);

            if (hist == null){
                replier.replyOtherError( "Failed to get the hash rate history.",null,jedis);
                return;
            }

            replier.setGot((long) hist.size());
            long bestHeight = Long.parseLong(jedis.get(BEST_HEIGHT));
            replier.setTotal( bestHeight- 1);
            replier.reply0Success(hist,jedis, null);
        }
    }
}
