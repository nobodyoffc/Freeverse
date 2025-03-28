package APIP10V1_Box;

import server.ApipApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import feip.feipData.Box;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import appTools.Settings;
import server.FcdslRequestHandler;

@WebServlet(name = ApipApiNames.BOX_BY_IDS, value = "/"+ ApipApiNames.SN_10+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BOX_BY_IDS)
public class BoxByIds extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public BoxByIds() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }   
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doIdsRequest(IndicesNames.BOX, Box.class, FieldNames.ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doIdsRequest(IndicesNames.BOX, Box.class, FieldNames.ID, request,response,authType);
    }
}