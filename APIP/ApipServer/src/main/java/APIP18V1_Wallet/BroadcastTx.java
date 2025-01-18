package APIP18V1_Wallet;

import constants.ApiNames;
import fcData.FcReplierHttp;
import initial.Initiator;
import tools.Hex;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static constants.FieldNames.RAW_TX;


@WebServlet(name = ApiNames.BroadcastTx, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version1 +"/"+ApiNames.BroadcastTx)
public class BroadcastTx extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FREE;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }

    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        try(Jedis jedis = jedisPool.getResource()) {
            //Do FCDSL other request
            Map<String, String> other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis, Initiator.sessionHandler);
            if (other == null) return;
            //Do this request
            String rawTx = other.get(RAW_TX);
            String result = Initiator.naSaRpcClient.sendRawTransaction(rawTx);
            if(result.startsWith("\""))result=result.substring(1);
            if(result.endsWith("\""))result=result.substring(0,result.length()-1);

            if(!Hex.isHexString(result))
                replier.replyOtherErrorHttp(result,null,jedis);
            else replier.reply0SuccessHttp(result, jedis, null);
        }
    }
}
