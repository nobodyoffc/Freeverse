package Mycoins;

import config.Settings;
import constants.ApipApiNames;
import initial.Initiator;
import server.FcHttpRequestHandler;
import utils.http.AuthType;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.TX_BY_IDS + ApipApiNames.MYCOINS, value = ApipApiNames.MycoinsPath + ApipApiNames.TX_BY_IDS)
public class TxByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public TxByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doTxInfoRequest(true, ID, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FREE;
        fcHttpRequestHandler.doTxInfoRequest(true, ID, request, response, authType);
    }
}
