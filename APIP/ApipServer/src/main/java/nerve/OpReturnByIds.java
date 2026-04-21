package nerve;

import constants.IndicesNames;
import data.fchData.OpReturn;
import initial.Initiator;
import utils.http.AuthType;
import constants.ApiNames;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import config.Settings;
import server.FcHttpRequestHandler;

import static constants.FieldNames.ID;

@WebServlet(ApiNames.NERVE + ApiNames.OpReturnByIds)
public class OpReturnByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public OpReturnByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.OPRETURN, OpReturn.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.OPRETURN, OpReturn.class, ID, request,response,authType);
    }
}
