package APIP17V1_Crypto;

import constants.ApiNames;
import constants.FieldNames;
import crypto.Hash;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;


@WebServlet(name = ApiNames.CheckSum4Hex, value = "/"+ApiNames.SN_17+"/"+ApiNames.Version1 +"/"+ApiNames.CheckSum4Hex)
public class CheckSum4Hex extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }

    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid,response);
        try(Jedis jedis = jedisPool.getResource()) {
            //Do FCDSL other request
            Map<String, String> other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis);
            if (other == null) return;
            //Do this request
            String hex = other.get(FieldNames.MESSAGE);
            if(hex==null){
                replier.replyOtherError("The hex missed.",null,jedis);
                return;
            }
            if (RequestChecker.isBadHex(replier, jedis, hex)) return;
            String sum = Hash.sha256x2(hex).substring(0,8);
            replier.replySingleDataSuccess(sum,jedis);
        }
    }
}