package APIP18V1_Wallet;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import fcData.FcReplierHttp;
import fch.Wallet;
import initial.Initiator;
import tools.NumberTools;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = ApiNames.FeeRate, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version1 +"/"+ApiNames.FeeRate)
public class FeeRate extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid, request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws IOException {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult == null) {
                return;
            }
            Double feeRate = new Wallet(null,Initiator.esClient,Initiator.naSaRpcClient).getFeeRate();
            if(feeRate==null){
                replier.replyOtherErrorHttp("Calculating fee rate wrong.",null,jedis);
                return;
            }
            replier.replySingleDataSuccess(NumberTools.roundDouble8(feeRate),jedis);
        }
    }
}
