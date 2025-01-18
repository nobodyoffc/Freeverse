package APIP18V1_Wallet;

import constants.ApiNames;
import constants.CodeMessage;
import fcData.FcReplierHttp;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FcdslRequestHandler;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;


@WebServlet(name = ApiNames.BalanceByIds, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version1 +"/"+ApiNames.BalanceByIds)
public class BalanceByIds extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }


    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool)  {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult == null) {
                return;
            }
            if(requestCheckResult.getRequestBody()==null || requestCheckResult.getRequestBody().getFcdsl()==null){
                replier.replyHttp(CodeMessage.Code1003BodyMissed,null,jedis);
                return;
            }

            if (requestCheckResult.getRequestBody().getFcdsl().getIds()==null) {
                replier.replyHttp(CodeMessage.Code1015FidMissed,null,jedis);
                return;
            }

            List<String> fids = requestCheckResult.getRequestBody().getFcdsl().getIds();
            Map<String,Long> balanceMap = FcdslRequestHandler.sumCashValueByOwners(fids, Initiator.esClient);
            FcdslRequestHandler.updateAddressBalances(balanceMap, Initiator.esClient);
            replier.replySingleDataSuccess(balanceMap,jedis);
        }
    }
}
