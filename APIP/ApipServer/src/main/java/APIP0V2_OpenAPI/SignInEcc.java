package APIP0V2_OpenAPI;

import apip.apipData.RequestBody;
import constants.CodeMessage;
import fcData.FcSession;
import fcData.ReplyBody;
import handlers.Handler;
import handlers.SessionHandler;
import initial.Initiator;
import server.ApipApiNames;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import appTools.Settings;

@WebServlet(name = ApipApiNames.SIGN_IN_ECC, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.SIGN_IN_ECC)
public class SignInEcc extends HttpServlet {
    private final Settings settings;
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public SignInEcc() {
        this.settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {

        FcSession fcSession;
        String pubKey;
        SessionHandler sessionHandler = (SessionHandler) settings.getHandler(Handler.HandlerType.SESSION);
        boolean isOk = httpRequestChecker.checkSignInRequestHttp(request, response);
        if (!isOk) {
            return;
        }
        pubKey = httpRequestChecker.getPubKey();
        String fid = httpRequestChecker.getFid();
        RequestBody.SignInMode mode = httpRequestChecker.getRequestBody().getMode();

        if (sessionHandler.getSessionById(fid)==null || RequestBody.SignInMode.REFRESH.equals(mode)) {
            fcSession = sessionHandler.addNewSession(fid, pubKey);
        } else {
            fcSession = sessionHandler.getSessionById(fid);
        }
        if(fcSession == null){
            replier.replyOtherErrorHttp("Failed to get session.", response);
            return;
        }
        fcSession.setKey(null);
        replier.reply0SuccessHttp(fcSession,response);
        replier.clean();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null);
    }
}
