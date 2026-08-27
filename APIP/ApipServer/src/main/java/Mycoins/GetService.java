package Mycoins;

import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import initial.Initiator;
import server.FcHttpRequestHandler;
import server.HttpRequestChecker;
import utils.http.AuthType;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = ApipApiNames.GET_SERVICE + ApipApiNames.MYCOINS, value = ApipApiNames.MycoinsPath + ApipApiNames.GET_SERVICE)
public class GetService extends HttpServlet {
    private final Settings settings;
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public GetService() {
        this.settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FREE;
        FcHttpRequestHandler.doGetService(request, response, authType, httpRequestChecker, replier, settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable, response);
    }
}
