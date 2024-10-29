import constants.ApiNames;
import initial.Initiator;
import javaTools.http.AuthType;
import fcData.FcReplierHttp;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/"+ApiNames.Version2 +"/"+ApiNames.Ping)
public class Ping extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {

        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);

        AuthType authType = AuthType.FC_SIGN_BODY;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false);
            if (requestCheckResult==null) return;
            replier.reply0SuccessHttp(null, jedis, null);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response){
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);
        AuthType authType = AuthType.FC_SIGN_URL;
        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false);
            if (requestCheckResult==null){
                return;
            }
            replier.reply0SuccessHttp(null, jedis, null);
        }
    }
}