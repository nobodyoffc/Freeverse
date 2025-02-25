package APIP0V2_OpenAPI;

import appTools.Settings;
import server.ApipApiNames;
import fcData.ReplyBody;
import initial.Initiator;
import tools.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name = ApipApiNames.PING, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.PING)
public class Ping extends HttpServlet {
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public Ping() {
        Settings settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {

        AuthType authType = AuthType.FC_SIGN_BODY;

        //Check authorization
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) return;
        replier.reply0SuccessHttp(response);

    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response){
        AuthType authType = AuthType.FREE;

        //Check authorization
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk){
                return;
        }
        replier.reply0SuccessHttp(true,response);
    }
}