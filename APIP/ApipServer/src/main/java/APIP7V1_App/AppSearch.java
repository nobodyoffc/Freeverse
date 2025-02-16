package APIP7V1_App;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.App;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.FieldNames.AID;
import static constants.FieldNames.T_RATE;
import static constants.Strings.ACTIVE;


@WebServlet(name = ApipApiNames.APP_SEARCH, value = "/"+ ApipApiNames.SN_7+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.APP_SEARCH)
public class AppSearch extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public AppSearch() {
        this.fcdslRequestHandler = new FcdslRequestHandler(Initiator.settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,AID,true);
        fcdslRequestHandler.doSearchRequest(IndicesNames.APP, App.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,AID,true);
        fcdslRequestHandler.doSearchRequest(IndicesNames.APP, App.class, defaultSort, request,response,authType);
    }
}