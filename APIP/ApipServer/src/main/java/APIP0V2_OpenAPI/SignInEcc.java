package APIP0V2_OpenAPI;

import apip.apipData.RequestBody;
import apip.apipData.Session;
import clients.redisClient.RedisTools;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import constants.Strings;
import fcData.FcReplier;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;
import server.Settings;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static constants.Constants.DEFAULT_SESSION_DAYS;
import static constants.Strings.*;

@WebServlet(name = ApiNames.SignInEcc, value = "/"+ApiNames.Version2 +"/"+ ApiNames.SignInEcc)
public class SignInEcc extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        String sid = Initiator.sid;
        FcReplier replier = new FcReplier(sid,response);

        Session session = new Session();
        RequestChecker requestChecker = new RequestChecker();
        String pubKey;
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            replier.setBestHeight(Long.valueOf(jedis.get(BEST_HEIGHT)));
            Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(sid, PARAMS));
            long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid,SETTINGS), WINDOW_TIME);

            RequestCheckResult requestCheckResult = requestChecker.checkSignInRequest(sid, request, replier, paramsMap, windowTime, jedis);
            if (requestCheckResult == null) {
                return;
            }
            pubKey = requestCheckResult.getPubKey();
            String fid = requestCheckResult.getFid();
            session.setFid(fid);

            RequestBody.SignInMode mode = requestCheckResult.getRequestBody().getMode();

            String sessionDaysStr = paramsMap.get(SESSION_DAYS);
            long sessionDays;
            if(sessionDaysStr==null)sessionDays=DEFAULT_SESSION_DAYS;
            else sessionDays = Long.parseLong(sessionDaysStr);

            if ((!jedis.hexists(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid)) || RequestBody.SignInMode.REFRESH.equals(mode)) {
                session = Session.makeNewSession(sid, replier, pubKey, jedis, fid, sessionDays);
            } else {
                session = Session.getSessionFromJedis(sid, replier, pubKey, jedis, fid, sessionDays);
            }
            if(session==null)return;
            replier.reply0Success(session,jedis, null);
            replier.clean();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplier replier =new FcReplier(Initiator.sid,response);
        replier.reply(ReplyCodeMessage.Code1017MethodNotAvailable,null,null);
    }
}
