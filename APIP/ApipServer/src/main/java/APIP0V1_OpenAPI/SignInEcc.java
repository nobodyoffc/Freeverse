package APIP0V1_OpenAPI;

import constants.CodeMessage;
import data.fcData.ReplyBody;
import initial.Initiator;
import constants.ApipApiNames;
import server.FcHttpRequestHandler;
import server.HttpRequestChecker;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import config.Settings;

@WebServlet(name = ApipApiNames.SIGN_IN_ECC, value = "/"+ ApipApiNames.SIGN_IN_ECC +"/"+ ApipApiNames.VER_1)
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
