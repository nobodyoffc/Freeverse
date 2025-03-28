package APIP7V1_App;

import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.App;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.ID;

import server.FcdslRequestHandler;


@WebServlet(name = ApipApiNames.APP_BY_IDS, value = "/"+ ApipApiNames.SN_7+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.APP_BY_IDS)
public class AppByIds extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public AppByIds() {
        this.fcdslRequestHandler = new FcdslRequestHandler(Initiator.settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doIdsRequest(IndicesNames.APP, App.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doIdsRequest(IndicesNames.APP, App.class, ID, request,response,authType);
    }
}