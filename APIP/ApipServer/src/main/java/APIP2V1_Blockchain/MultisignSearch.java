package APIP2V1_Blockchain;

import data.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import data.fchData.Multisign;
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


@WebServlet(name = ApipApiNames.MULTISIGN_SEARCH, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.MULTISIGN_SEARCH)
public class MultisignSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public MultisignSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(BIRTH_HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.MULTISIGN, Multisign.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(BIRTH_HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.MULTISIGN, Multisign.class, defaultSort, request,response,authType);
    }
}