package APIP2V2_Blockchain;

import server.ApipApiNames;
import server.FcHttpRequestHandler;
import constants.IndicesNames;
import data.fchData.Cash;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import config.Settings;

import java.io.IOException;

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.CASH_BY_IDS, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CASH_BY_IDS)
public class CashByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public CashByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.CASH, Cash.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.CASH, Cash.class, ID, request,response,authType);
    }
}
