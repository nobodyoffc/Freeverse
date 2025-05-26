package APIP0V2_OpenAPI;

import config.Settings;
import server.FcHttpRequestHandler;
import server.ApipApiNames;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import initial.Initiator;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = ApipApiNames.GET_SERVICE, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GET_SERVICE)
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
        FcHttpRequestHandler.doGetService(request, response, authType,httpRequestChecker,replier,settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response){
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,response);
    }

}
