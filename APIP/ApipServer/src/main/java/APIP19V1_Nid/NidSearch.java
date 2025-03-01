
package APIP19V1_Nid;


import apip.apipData.Sort;
import server.ApipApiNames;
import constants.FieldNames;
import constants.IndicesNames;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import feip.feipData.Nid;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;
import appTools.Settings;
import static constants.FieldNames.*;


@WebServlet(name = ApipApiNames.NID_SEARCH, value = "/"+ ApipApiNames.SN_19+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.NID_SEARCH)
public class NidSearch extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public NidSearch() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false, FieldNames.ID,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.NID, Nid.class,defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.NID, Nid.class,defaultSort, request,response,authType);
    }
}