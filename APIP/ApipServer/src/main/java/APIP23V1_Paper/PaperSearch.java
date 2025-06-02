package APIP23V1_Paper;

import data.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Paper;
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
import static constants.Strings.DELETED;

@WebServlet(name = ApipApiNames.PAPER_SEARCH, value = "/"+ ApipApiNames.SN_23+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.PAPER_SEARCH)
public class PaperSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public PaperSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(DELETED,false,T_RATE,false,ID,true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.PAPER, Paper.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(DELETED,false,T_RATE,false,ID,true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.PAPER, Paper.class, defaultSort, request,response,authType);
    }
} 