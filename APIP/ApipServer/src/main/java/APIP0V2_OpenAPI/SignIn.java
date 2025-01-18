package APIP0V2_OpenAPI;

import fcData.FcSession;
import apip.apipData.RequestBody;
import tools.RedisTools;
import constants.ApiNames;
import constants.CodeMessage;
import constants.Strings;
import fcData.FcReplierHttp;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;
import appTools.Settings;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static constants.Strings.*;
import static initial.Initiator.sessionHandler;

@WebServlet(name = ApiNames.SignIn, value = "/"+ApiNames.Version1 +"/"+ ApiNames.SignIn)
public class SignIn extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);

        FcSession fcSession;
        RequestChecker requestChecker = new RequestChecker();
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            replier.setBestHeight(Long.valueOf(jedis.get(BEST_HEIGHT)));
            Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(Initiator.sid, PARAMS));
            long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(Initiator.sid,SETTINGS), WINDOW_TIME);
            RequestCheckResult requestCheckResult = requestChecker.checkSignInRequest(Initiator.sid, request, replier, paramsMap, windowTime, jedis, sessionHandler);
            if (requestCheckResult == null) {
                return;
            }

            String fid = requestCheckResult.getFid();
            RequestBody.SignInMode mode = requestCheckResult.getRequestBody().getMode();

            if ((!jedis.hexists(Settings.addSidBriefToName(Initiator.sid,Strings.ID_SESSION_NAME), fid)) || RequestBody.SignInMode.REFRESH.equals(mode)) {
                try {
                    fcSession = sessionHandler.addNewSession(fid, null);
                } catch (Exception e) {
                    replier.replyOtherErrorHttp("Something wrong when making sessionKey.\n" + e.getMessage(), null, jedis);
                    return;
                }
            } else {
                fcSession = sessionHandler.getSessionById(fid);
                if (fcSession == null) {
                    try {
                        fcSession = sessionHandler.addNewSession(fid, null);
                    } catch (Exception e) {
                        replier.replyOtherErrorHttp("Something wrong when making sessionKey.\n" + e.getMessage(), null, jedis);
                        return;
                    }
                }
            }
            replier.reply0SuccessHttp(fcSession, jedis, null);
            replier.clean();
        }
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplierHttp replier =new FcReplierHttp(Initiator.sid,response);
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null,null);
    }
}
