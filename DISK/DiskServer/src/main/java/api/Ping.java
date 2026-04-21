package api;

import config.Settings;
import data.fcData.ReplyBody;
import initial.Initiator;
import server.FcHttpRequestHandler;
import server.DiskApiNames;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name = DiskApiNames.PING, value = "/"+ DiskApiNames.PING+"/"+ DiskApiNames.VER_1)
public class Ping extends HttpServlet {
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public Ping() {
        Settings settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.ENCRYPTED;
        FcHttpRequestHandler.doPingPost(request, response, authType,replier,httpRequestChecker);

    }

    public void doGet(HttpServletRequest request, HttpServletResponse response){
        AuthType authType = AuthType.FREE;
        FcHttpRequestHandler.doPingGet(request, response, authType,replier,httpRequestChecker);
    }

}