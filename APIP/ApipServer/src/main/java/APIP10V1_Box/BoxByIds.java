package APIP10V1_Box;

import server.ApipApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import data.feipData.Box;
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

@WebServlet(name = ApipApiNames.BOX_BY_IDS, value = "/"+ ApipApiNames.SN_10+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BOX_BY_IDS)
public class BoxByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public BoxByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }   
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.BOX, Box.class, FieldNames.ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.BOX, Box.class, FieldNames.ID, request,response,authType);
    }
}