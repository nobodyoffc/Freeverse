package APIP17V1_Crypto;

import constants.ApiNames;
import constants.FieldNames;
import crypto.Base58;
import crypto.Hash;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.Hex;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;


@WebServlet(name = ApiNames.HexToBase58, value = "/"+ApiNames.SN_17+"/"+ApiNames.Version2 +"/"+ApiNames.HexToBase58)
public class HexToBase58 extends HttpServlet {
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

            replier.replySingleDataSuccess(Base58.encode(Hex.fromHex(hex)),jedis);
        }
    }
}