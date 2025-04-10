package APIP3V1_Cid;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.FieldNames;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import appTools.Settings;
import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.CID_SEARCH, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CID_SEARCH)
public class CidInfoSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public CidInfoSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> sort = Sort.makeSortList(FieldNames.LAST_HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doCidInfoSearchRequest(sort, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> sort = Sort.makeSortList(FieldNames.LAST_HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doCidInfoSearchRequest(sort, request, response, authType);
    }

    
}