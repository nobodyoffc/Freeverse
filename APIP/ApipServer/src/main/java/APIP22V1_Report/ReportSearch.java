package APIP22V1_Report;

import constants.OpNames;
import data.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Report;
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

@WebServlet(name = ApipApiNames.REPORT_SEARCH, value = "/"+ ApipApiNames.SN_22+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.REPORT_SEARCH)
public class ReportSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ReportSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(DELETED,false,T_RATE,false,ID,true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.REPORT, Report.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(DELETED,false,T_RATE,false,ID,true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.REPORT, Report.class, defaultSort, request,response,authType);
    }
} 