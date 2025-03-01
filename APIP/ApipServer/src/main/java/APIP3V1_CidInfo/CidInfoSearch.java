package APIP3V1_CidInfo;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.FieldNames;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import appTools.Settings;
import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.CID_INFO_SEARCH, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CID_INFO_SEARCH)
public class CidInfoSearch extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public CidInfoSearch() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> sort = Sort.makeSortList(FieldNames.LAST_HEIGHT,false,ID,true,null,null);
        fcdslRequestHandler.doCidInfoSearchRequest(sort, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> sort = Sort.makeSortList(FieldNames.LAST_HEIGHT,false,ID,true,null,null);
        fcdslRequestHandler.doCidInfoSearchRequest(sort, request, response, authType);
    }

    
}