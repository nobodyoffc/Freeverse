package api;

import server.FcHttpRequestHandler;
import server.ApipApiNames;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import initial.Initiator;
import server.HttpRequestChecker;
import config.Settings;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
        FcHttpRequestHandler.doSigInPost(request, response,replier,settings,httpRequestChecker);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null);
    }
}
