package APIP0V1_OpenAPI;

import config.Settings;
import constants.ApipApiNames;
import data.fcData.ReplyBody;
import initial.Initiator;
import server.FcHttpRequestHandler;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name = ApipApiNames.PING, value = "/"+ ApipApiNames.PING+ "/" + ApipApiNames.VER_1)
public class Ping extends HttpServlet {
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public Ping() {
        Settings settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        FcHttpRequestHandler.doPingPost(request, response, authType,replier,httpRequestChecker);

    }

    public void doGet(HttpServletRequest request, HttpServletResponse response){
        AuthType authType = AuthType.FREE;
        FcHttpRequestHandler.doPingGet(request, response, authType,replier,httpRequestChecker);
    }

}