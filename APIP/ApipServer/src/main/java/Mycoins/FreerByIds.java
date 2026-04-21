package Mycoins;

import constants.ApipApiNames;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import config.Settings;

@WebServlet(name = ApipApiNames.Freer_BY_IDS + ApipApiNames.MYCOINS, value = ApipApiNames.MycoinsPath + ApipApiNames.Freer_BY_IDS)
public class FreerByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public FreerByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doCidInfoByIdsRequestHttp(null, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FREE;
        fcHttpRequestHandler.doCidInfoByIdsRequestHttp(null, request, response, authType);
    }
}
