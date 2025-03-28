package api;

import server.ApipApiNames;
import fcData.ReplyBody;
import initial.Initiator;
import utils.http.AuthType;
import redis.clients.jedis.Jedis;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name = ApipApiNames.PING, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.PING)
public class Ping extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {

        ReplyBody replier = new ReplyBody(Initiator.settings);

        AuthType authType = AuthType.FC_SIGN_BODY;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(Initiator.settings, replier);
            httpRequestChecker.checkRequestHttp(request, response, authType);
            if (httpRequestChecker ==null) return;
            replier.reply0SuccessHttp(response);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response){
        ReplyBody replier = new ReplyBody(Initiator.settings);
        AuthType authType = AuthType.FREE;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(Initiator.settings, replier);
            httpRequestChecker.checkRequestHttp(request, response, authType);
            if (httpRequestChecker ==null){
                return;
            }
            replier.reply0SuccessHttp(response);
        }
    }
}