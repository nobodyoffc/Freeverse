package APIP6V1_Service;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.ServiceHistory;
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
import static constants.OpNames.RATE;
import static constants.Strings.OP;
import static constants.FieldNames.HEIGHT;
import static constants.FieldNames.INDEX;


@WebServlet(name = ApipApiNames.SERVICE_OP_HISTORY, value = "/"+ ApipApiNames.SN_6+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.SERVICE_OP_HISTORY)

public class ServiceOpHistory extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ServiceOpHistory() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.SERVICE_HISTORY, ServiceHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.SERVICE_HISTORY, ServiceHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
}