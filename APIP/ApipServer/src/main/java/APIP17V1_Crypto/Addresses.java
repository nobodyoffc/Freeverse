package APIP17V1_Crypto;

import constants.ApiNames;
import constants.FieldNames;
import crypto.KeyTools;
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

@WebServlet(name = ApiNames.Addresses, value = "/"+ApiNames.SN_17+"/"+ApiNames.Version1 +"/"+ApiNames.Addresses)
public class Addresses extends HttpServlet {
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
            Map<String, String> other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis);
            if (other == null) return;
            //Do this request

            String addrOrPubKey = other.get(FieldNames.ADDR_OR_PUB_KEY);

            Map<String, String> addrMap;
            String pubKey;

            if(addrOrPubKey.startsWith("F")||addrOrPubKey.startsWith("1")||addrOrPubKey.startsWith("D")||addrOrPubKey.startsWith("L")){
                byte[] hash160 = KeyTools.addrToHash160(addrOrPubKey);
                addrMap = KeyTools.hash160ToAddresses(hash160);
            }else if (addrOrPubKey.startsWith("02")||addrOrPubKey.startsWith("03")){
                pubKey = addrOrPubKey;
                addrMap= KeyTools.pubKeyToAddresses(pubKey);
            }else if(addrOrPubKey.startsWith("04")){
                try {
                    pubKey = KeyTools.compressPk65To33(addrOrPubKey);
                    addrMap= KeyTools.pubKeyToAddresses(pubKey);
                } catch (Exception e) {
                    replier.replyOtherErrorHttp("Wrong public key.",null,jedis);
                    return;
                }
            }else{
                replier.replyOtherErrorHttp("FID or Public Key are needed.",null,jedis);
                return;
            }
            replier.replySingleDataSuccess(addrMap,jedis);
        }
    }
}