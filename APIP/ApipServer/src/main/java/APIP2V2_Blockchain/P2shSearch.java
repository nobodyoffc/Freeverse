package APIP2V2_Blockchain;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import fch.fchData.P2SH;
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

import appTools.Settings;

import static constants.FieldNames.*;


@WebServlet(name = ApipApiNames.P_2_SH_SEARCH, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.P_2_SH_SEARCH)
public class P2shSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public P2shSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(BIRTH_HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.P2SH, P2SH.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(BIRTH_HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.P2SH, P2SH.class, defaultSort, request,response,authType);
    }
}