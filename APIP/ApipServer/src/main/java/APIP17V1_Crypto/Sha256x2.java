package APIP17V1_Crypto;

import constants.ApiNames;
import constants.FieldNames;
import crypto.Hash;
import fcData.FcReplierHttp;
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


@WebServlet(name = ApiNames.Sha256x2, value = "/"+ApiNames.SN_17+"/"+ApiNames.Version1 +"/"+ApiNames.Sha256x2)
public class Sha256x2 extends HttpServlet {
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
            Map<String,String> other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis);
            if (other == null) return;
            //Do this request
            String text;
            try {
                text = other.get(FieldNames.MESSAGE);
            }catch (Exception e){
                replier.replyOtherErrorHttp("Can not get parameters correctly from Json string.",null,jedis);
                e.printStackTrace();
                return;
            }
            replier.replySingleDataSuccess(Hash.sha256x2(text),jedis);
        }
    }
}