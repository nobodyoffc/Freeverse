package APIP6V1_Service;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.Service;
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
import static constants.FieldNames.T_RATE;
import static constants.Strings.ACTIVE;
import static constants.FieldNames.ID;


@WebServlet(name = ApipApiNames.SERVICE_SEARCH, value = "/"+ ApipApiNames.SN_6+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.SERVICE_SEARCH)
public class ServiceSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ServiceSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,ID,true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.SERVICE, Service.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,ID,true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.SERVICE, Service.class, defaultSort, request,response,authType);
    }
}