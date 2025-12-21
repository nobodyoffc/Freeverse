package APIP2V1_Blockchain;

import constants.ApipApiNames;
import data.apipData.Sort;
import data.fchData.P2SH;
import constants.IndicesNames;
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

import config.Settings;

import static constants.FieldNames.*;


@WebServlet(name = ApipApiNames.P2SH_SEARCH, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.P2SH_SEARCH +"/"+ ApipApiNames.VER_1)
public class P2shSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public P2shSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ID,true,null,null,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.P2SH, P2SH.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ID,true,null,null,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.P2SH, P2SH.class, defaultSort, request,response,authType);
    }
}
