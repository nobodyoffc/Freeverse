import constants.FieldNames;
import fcData.FcSession;
import apip.apipData.RequestBody;
import tools.RedisTools;
import constants.ApiNames;
import constants.CodeMessage;
import initial.Initiator;
import fcData.FcReplierHttp;
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

@WebServlet(name = ApiNames.SignIn, value = "/"+ApiNames.Version2 +"/"+ ApiNames.SignIn)
public class SignIn extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);

        FcSession fcSession = new FcSession();
        RequestChecker requestChecker = new RequestChecker();
        try (Jedis jedis = Initiator.jedisPool.getResource()) {

            Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(Initiator.sid, PARAMS));
            long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(Initiator.sid,SETTINGS), WINDOW_TIME);
            RequestCheckResult  requestCheckResult = requestChecker.checkSignInRequest(Initiator.sid, request, replier, paramsMap, windowTime, jedis, Initiator.sessionHandler);
            if (requestCheckResult == null) {
                return;
            }

            String fid = requestCheckResult.getFid();
            fcSession.setId(fid);

            RequestBody.SignInMode mode = requestCheckResult.getRequestBody().getMode();

            if ((!jedis.hexists(Settings.addSidBriefToName(Initiator.sid,FieldNames.ID_SESSION_NAME), fid)) || RequestBody.SignInMode.REFRESH.equals(mode)) {
                try {
                    fcSession = FcSession.makeSession(Initiator.sid,jedis, fid);
                } catch (Exception e) {
                    replier.replyOtherErrorHttp("Some thing wrong when making sessionKey.\n" + e.getMessage(),null, jedis);
                }
            } else {
                jedis.select(0);
                String sessionName = jedis.hget(Settings.addSidBriefToName(Initiator.sid,FieldNames.ID_SESSION_NAME), fid);
                fcSession.setName(sessionName);
                jedis.select(1);
                String sessionKey = jedis.hget(sessionName, FieldNames.SESSION_KEY);
                if (sessionKey != null) {
                    fcSession.setKey(sessionKey);
                } else {
                    try {
                        fcSession = FcSession.makeSession(Initiator.sid,jedis, fid);
                    } catch (Exception e) {
                        replier.replyOtherErrorHttp("Some thing wrong when making sessionKey.\n" + e.getMessage(),null,jedis);
                        return;
                    }
                }
            }
            replier.reply0SuccessHttp(fcSession,jedis, null);
            replier.clean();
        }
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplierHttp replier =new FcReplierHttp(Initiator.sid,response);
        replier.setCode(CodeMessage.Code1017MethodNotAvailable);
        replier.setMessage(CodeMessage.Msg1017MethodNotAvailable);
        response.getWriter().write(replier.toNiceJson());
    }
}
