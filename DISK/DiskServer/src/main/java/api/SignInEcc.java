package api;

import constants.FieldNames;
import fcData.FcSession;
import apip.apipData.RequestBody;
import tools.RedisTools;
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
import java.util.Map;

import static constants.Strings.*;

@WebServlet(name = ApipApiNames.SIGN_IN_ECC, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.SIGN_IN_ECC)
public class SignInEcc extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        String sid = Initiator.sid;
        ReplyBody replier = new ReplyBody(Initiator.settings);

        FcSession fcSession;
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(Initiator.settings, replier);
        String pubKey;
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            replier.setBestHeight(Long.valueOf(jedis.get(BEST_HEIGHT)));
            Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(sid, PARAMS));
            long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid,SETTINGS), WINDOW_TIME);

            httpRequestChecker.checkSignInRequestHttp(request, response);
            pubKey = httpRequestChecker.getPubKey();
            String fid = httpRequestChecker.getFid();
            RequestBody.SignInMode mode = httpRequestChecker.getRequestBody().getMode();

            if ((!jedis.hexists(Settings.addSidBriefToName(sid, FieldNames.ID_SESSION_NAME), fid)) || RequestBody.SignInMode.REFRESH.equals(mode)) {
                fcSession = Initiator.sessionHandler.addNewSession(fid, pubKey);
            } else {
                fcSession = Initiator.sessionHandler.getSessionById(fid);
            }
            if(fcSession == null) {
                replier.replyOtherErrorHttp("Failed to get session.", response);
                return;
            }
            fcSession.setKey(null);
            replier.reply0SuccessHttp(fcSession,response);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier =new ReplyBody(Initiator.settings);
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null);
    }
}
