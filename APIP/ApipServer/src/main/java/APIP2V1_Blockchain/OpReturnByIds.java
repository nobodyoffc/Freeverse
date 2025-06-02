package APIP2V1_Blockchain;

import server.ApipApiNames;
import constants.IndicesNames;
import data.fchData.OpReturn;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import config.Settings;
import server.FcHttpRequestHandler;

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.OP_RETURN_BY_IDS, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.OP_RETURN_BY_IDS)
public class OpReturnByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public OpReturnByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.OPRETURN, OpReturn.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.OPRETURN, OpReturn.class, ID, request,response,authType);
    }
}
