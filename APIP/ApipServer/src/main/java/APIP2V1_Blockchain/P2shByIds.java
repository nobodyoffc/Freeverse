package APIP2V1_Blockchain;

import constants.ApipApiNames;
import data.fchData.P2SH;
import constants.IndicesNames;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import config.Settings;
import static constants.FieldNames.ID;

import server.FcHttpRequestHandler;

@WebServlet(name = ApipApiNames.P2SH_BY_IDS, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.P2SH_BY_IDS +"/"+ ApipApiNames.VER_1)
public class P2shByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public P2shByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.P2SH, P2SH.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.P2SH, P2SH.class, ID, request,response,authType);
    }
}
