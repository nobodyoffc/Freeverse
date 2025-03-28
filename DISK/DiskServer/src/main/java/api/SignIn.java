package api;

import constants.FieldNames;
import fcData.FcSession;
import apip.apipData.RequestBody;
import server.ApipApiNames;
import constants.CodeMessage;
import fcData.ReplyBody;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import server.HttpRequestChecker;
import appTools.Settings;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.Strings.*;

@WebServlet(name = ApipApiNames.SIGN_IN, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.SIGN_IN)
public class SignIn extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        ReplyBody replier = new ReplyBody(settings);

        FcSession fcSession;
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            replier.setBestHeight(Long.valueOf(jedis.get(BEST_HEIGHT)));
//            Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(Initiator.sid, PARAMS));
//            long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(Initiator.sid,SETTINGS), WINDOW_TIME);

            if(!httpRequestChecker.checkSignInRequestHttp(request, response))return;

            String fid = httpRequestChecker.getFid();
            RequestBody.SignInMode mode = httpRequestChecker.getRequestBody().getMode();

            if ((!jedis.hexists(Settings.addSidBriefToName(Initiator.sid, FieldNames.ID_SESSION_NAME), fid)) || RequestBody.SignInMode.REFRESH.equals(mode)) {
                fcSession = Initiator.sessionHandler.addNewSession(fid, null);
                if (fcSession == null) {
                    replier.replyOtherErrorHttp("Failed to create new session.", response);
                    return;
                }
            } else {
                fcSession = Initiator.sessionHandler.getSessionById(fid);
                if (fcSession == null) {
                    fcSession = Initiator.sessionHandler.addNewSession(fid, null);
                    if (fcSession == null) {
                        replier.replyOtherErrorHttp("Failed to create new session.", response);
                        return;
                    }
                }
            }
            replier.reply0SuccessHttp(fcSession,response);
            replier.clean();
        }
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier =new ReplyBody(Initiator.settings);
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,response);
    }
}
