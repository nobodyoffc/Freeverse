package nerve;

import constants.ApiNames;
import constants.IndicesNames;
import data.fchData.Cash;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import config.Settings;
import static constants.FieldNames.ID;

@WebServlet(ApiNames.NERVE + ApiNames.CashByIds)
public class CashByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public CashByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.CASH, Cash.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.CASH, Cash.class, ID, request,response,authType);
    }
}