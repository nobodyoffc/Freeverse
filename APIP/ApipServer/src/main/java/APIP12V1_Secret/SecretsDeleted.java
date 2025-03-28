
package APIP12V1_Secret;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.Secret;
import initial.Initiator;
import utils.http.AuthType;
import server.FcdslRequestHandler;
import appTools.Settings;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.FieldNames.ID;
import static constants.FieldNames.LAST_HEIGHT;
import static constants.Strings.ACTIVE;
import static constants.Values.TRUE;


@WebServlet(name = ApipApiNames.SECRETS_DELETED, value = "/"+ ApipApiNames.SN_12+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.SECRETS_DELETED)
public class SecretsDeleted extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public SecretsDeleted() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.SECRET, Secret.class, null,null,ACTIVE,TRUE,defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.SECRET, Secret.class, null,null,ACTIVE,TRUE,defaultSort, request,response,authType);
    }
}