
package APIP16V1_Token;


import server.ApipApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import feip.feipData.Token;
import initial.Initiator;
import utils.http.AuthType;
import appTools.Settings;
import static constants.FieldNames.ID;
import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.TOKEN_BY_IDS, value = "/"+ ApipApiNames.SN_16+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TOKEN_BY_IDS)
public class TokenByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public TokenByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.TOKEN, Token.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.TOKEN, Token.class, ID, request,response,authType);
    }
}