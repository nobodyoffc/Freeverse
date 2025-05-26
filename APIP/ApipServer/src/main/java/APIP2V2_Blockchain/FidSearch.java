package APIP2V2_Blockchain;

import data.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import data.fchData.Cid;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;
import config.Settings;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.FieldNames.*;

@WebServlet(name = ApipApiNames.FID_SEARCH, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.FID_SEARCH)
public class FidSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public FidSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.CID, Cid.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.CID, Cid.class, defaultSort, request,response,authType);
    }
}

