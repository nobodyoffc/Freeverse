
package APIP12V1_Secret;


import server.ApipApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import feip.feipData.Secret;
import initial.Initiator;
import tools.http.AuthType;
import appTools.Settings;
import static constants.FieldNames.Secret_Id;
import server.FcdslRequestHandler;


@WebServlet(name = ApipApiNames.SECRET_BY_IDS, value = "/"+ ApipApiNames.SN_12+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.SECRET_BY_IDS)
public class SecretByIds extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public SecretByIds() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doIdsRequest(IndicesNames.SECRET, Secret.class, Secret_Id, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doIdsRequest(IndicesNames.SECRET, Secret.class, Secret_Id, request,response,authType);
    }
}