package APIP0V2_OpenAPI;

import constants.ApiNames;
import fcData.FcReplierHttp;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name = ApiNames.Ping, value = "/"+ApiNames.Version1 +"/"+ ApiNames.Ping)
public class Ping extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {

        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);

        AuthType authType = AuthType.FC_SIGN_BODY;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult==null) return;
            replier.reply0SuccessHttp(null, jedis, null);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response){
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);
        AuthType authType = AuthType.FREE;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult==null){
                return;
            }
            replier.reply0SuccessHttp(null, jedis, null);
        }
    }
}