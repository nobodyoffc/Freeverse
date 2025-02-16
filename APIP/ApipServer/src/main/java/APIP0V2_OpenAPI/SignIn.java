package APIP0V2_OpenAPI;

import apip.apipData.RequestBody;
import appTools.Settings;
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

@WebServlet(name = ApipApiNames.SIGN_IN, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.SIGN_IN)
public class SignIn extends HttpServlet {
    private final Settings settings;
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public SignIn() {
        this.settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcSession fcSession;
        SessionHandler sessionHandler = (SessionHandler) settings.getHandler(Handler.HandlerType.SESSION);
        boolean isOk = httpRequestChecker.checkSignInRequestHttp(request, response);
        if (!isOk) {
            return;
            }

        String fid = httpRequestChecker.getFid();
        RequestBody.SignInMode mode = httpRequestChecker.getRequestBody().getMode();

        if (sessionHandler.getSessionById(fid)==null || RequestBody.SignInMode.REFRESH.equals(mode)) {
            try {
                fcSession = sessionHandler.addNewSession(fid, null);
            } catch (Exception e) {
                replier.replyOtherErrorHttp("Something wrong when making sessionKey.\n" + e.getMessage(), response);
                return;
            }
        } else {
            fcSession = sessionHandler.getSessionById(fid);
            if (fcSession == null) {
                try {
                    fcSession = sessionHandler.addNewSession(fid, null);
                } catch (Exception e) {
                    replier.replyOtherErrorHttp("Something wrong when making sessionKey.\n" + e.getMessage(), response);
                    return;
                }
            }
        }
        replier.reply0SuccessHttp(fcSession,response);
        replier.clean();
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null);
    }
}
