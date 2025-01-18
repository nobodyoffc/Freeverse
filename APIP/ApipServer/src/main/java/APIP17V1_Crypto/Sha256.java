package APIP17V1_Crypto;

import constants.ApiNames;
import constants.FieldNames;
import crypto.Hash;
import fcData.FcReplierHttp;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;


@WebServlet(name = ApiNames.Sha256, value = "/"+ApiNames.SN_17+"/"+ApiNames.Version1 +"/"+ApiNames.Sha256)
public class Sha256 extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
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
            String text = other.get(FieldNames.MESSAGE);
            if(text==null){
                replier.replyOtherErrorHttp("The hex missed.",null,jedis);
                return;
            }
            replier.replySingleDataSuccess(Hash.sha256(text),jedis);
        }
    }
}