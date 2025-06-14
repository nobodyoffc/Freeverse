package APIP3V1_Cid;

import data.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.CidHist;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.FieldNames.HEIGHT;
import static constants.FieldNames.INDEX;

import config.Settings;

@WebServlet(name = ApipApiNames.HOMEPAGE_HISTORY, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.HOMEPAGE_HISTORY)
public class HomepageHistory extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public HomepageHistory() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.CID_HISTORY, CidHist.class,"sn","9", null, null, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.CID_HISTORY, CidHist.class,"sn","9", null, null, defaultSort, request,response,authType);
    }
}
