package APIP5V1_Code;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.Code;
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
import static constants.Strings.ACTIVE;


@WebServlet(name = ApipApiNames.CODE_SEARCH, value = "/"+ ApipApiNames.SN_5+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CODE_SEARCH)
public class CodeSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public CodeSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,ID,true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.CODE, Code.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,ID,true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.CODE, Code.class, defaultSort, request,response,authType);
    }
}