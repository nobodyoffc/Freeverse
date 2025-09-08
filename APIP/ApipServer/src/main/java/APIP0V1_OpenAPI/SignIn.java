package APIP0V1_OpenAPI;

import data.apipData.SignInMode;
import data.fcData.AlgorithmId;
import config.Settings;
import constants.CodeMessage;
import data.fcData.FcSession;
import data.fcData.ReplyBody;
import handlers.Manager;
import handlers.SessionManager;
import initial.Initiator;
import server.ApipApiNames;
import server.HttpRequestChecker;
import server.FcHttpRequestHandler;

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

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        FcHttpRequestHandler.doSigInPost(request, response,replier,settings,httpRequestChecker);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null);
    }
}
