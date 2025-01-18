package APIP17V1_Crypto;

import constants.ApiNames;
import constants.FieldNames;
import crypto.KeyTools;
import fcData.FcReplierHttp;
import fcData.Signature;
import initial.Initiator;
import tools.http.AuthType;
import org.bitcoinj.core.ECKey;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.SignatureException;
import java.util.Map;


@WebServlet(name = ApiNames.Verify, value = "/"+ApiNames.SN_17+"/"+ApiNames.Version1 +"/"+ApiNames.Verify)
public class Verify extends HttpServlet {
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
            Map<String,String> other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis, Initiator.sessionHandler);
            if (other == null) return;
            //Do this request
            String rawSignJson = other.get(FieldNames.SIGN);
            Signature signature;
            try {
                signature = Signature.parseSignature(rawSignJson);
            }catch (Exception e){
                replier.replyOtherErrorHttp("Wrong signature format.",null,jedis);
                return;
            }

            if(signature==null){
                replier.replyOtherErrorHttp("Failed to parse signature.",null,jedis);
                return;
            }

            boolean isGoodSign;
            if(signature.getFid()!=null&& signature.getMsg()!=null && signature.getSign()!=null){
                String sign = signature.getSign().replace("\\u003d", "=");
                try {
                    String signPubKey = ECKey.signedMessageToKey(signature.getMsg(), sign).getPublicKeyAsHex();
                    isGoodSign= signature.getFid().equals(KeyTools.pubKeyToFchAddr(signPubKey));
                } catch (SignatureException e) {
                    isGoodSign = false;
                }
            }else{
                replier.replyOtherErrorHttp("FID, signature or message missed.",null,jedis);
                return;
            }
            replier.replySingleDataSuccess(isGoodSign,jedis);
        }
    }
}
