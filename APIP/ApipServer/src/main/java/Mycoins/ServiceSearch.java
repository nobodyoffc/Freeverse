package Mycoins;

import config.Settings;
import constants.ApipApiNames;
import constants.IndicesNames;
import data.apipData.Sort;
import data.feipData.Service;
import initial.Initiator;
import server.FcHttpRequestHandler;
import utils.http.AuthType;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.FieldNames.ID;
import static constants.FieldNames.T_RATE;
import static constants.Strings.ACTIVE;


@WebServlet(name = ApipApiNames.SERVICE_SEARCH+ ApipApiNames.MYCOINS, value = ApipApiNames.MycoinsPath + ApipApiNames.SERVICE_SEARCH )
public class ServiceSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ServiceSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,ID,true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.SERVICE, Service.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FREE;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,ID,true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.SERVICE, Service.class, defaultSort, request,response,authType);
    }
}
