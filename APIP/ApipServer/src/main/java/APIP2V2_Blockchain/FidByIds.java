package APIP2V2_Blockchain;

import fch.fchData.Cid;
import server.ApipApiNames;
import constants.IndicesNames;
import initial.Initiator;
import tools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import appTools.Settings;
import server.FcdslRequestHandler;
import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.FID_BY_IDS, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.FID_BY_IDS)
public class FidByIds extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public FidByIds() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }   
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doIdsRequest(IndicesNames.CID, Cid.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doIdsRequest(IndicesNames.CID, Cid.class, ID, request,response,authType);
    }
}
