package APIP0V1_OpenAPI;

import data.apipData.RequestBody;
import config.Settings;
import constants.CodeMessage;
import data.fcData.FcSession;
import data.fcData.ReplyBody;
import handlers.Manager;
import handlers.SessionManager;
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
        SessionManager sessionHandler = (SessionManager) settings.getManager(Manager.ManagerType.SESSION);
        boolean isOk = httpRequestChecker.checkSignInRequestHttp(request, response);
        if (!isOk) {
            return;
            }

        String fid = httpRequestChecker.getFid();
        RequestBody.SignInMode mode = httpRequestChecker.getRequestBody().getMode();

        if (sessionHandler.getSessionByUserId(fid)==null || RequestBody.SignInMode.REFRESH.equals(mode)) {
            try {
                fcSession = sessionHandler.addNewSession(fid, null);
            } catch (Exception e) {
                replier.replyOtherErrorHttp("Something wrong when making sessionKey.\n" + e.getMessage(), response);
                return;
            }
        } else {
            fcSession = sessionHandler.getSessionByUserId(fid);
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
