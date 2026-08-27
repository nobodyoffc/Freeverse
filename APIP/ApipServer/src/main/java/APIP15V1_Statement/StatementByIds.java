
package APIP15V1_Statement;


import constants.ApipApiNames;
import constants.IndicesNames;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import data.feipData.Statement;
import initial.Initiator;
import utils.http.AuthType;
import config.Settings;
import static constants.FieldNames.ID;
import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.STATEMENT_BY_IDS, value = "/"+ ApipApiNames.SN_15+"/"+ ApipApiNames.STATEMENT_BY_IDS +"/"+ ApipApiNames.VER_1)
public class StatementByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public StatementByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.STATEMENT, Statement.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.STATEMENT, Statement.class, ID, request,response,authType);
    }
}