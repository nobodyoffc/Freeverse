package APIP2V2_Blockchain;

import server.ApipApiNames;
import constants.IndicesNames;
import fch.fchData.P2SH;
import initial.Initiator;
import tools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import appTools.Settings;
import static constants.FieldNames.ID;

import server.FcdslRequestHandler;

@WebServlet(name = ApipApiNames.P_2_SH_BY_IDS, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.P_2_SH_BY_IDS)
public class P2shByIds extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public P2shByIds() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doIdsRequest(IndicesNames.P2SH, P2SH.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doIdsRequest(IndicesNames.P2SH, P2SH.class, ID, request,response,authType);
    }
}
