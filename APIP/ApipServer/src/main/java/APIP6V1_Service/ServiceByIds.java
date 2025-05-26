package APIP6V1_Service;

import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Service;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import config.Settings;
import static constants.FieldNames.ID;
import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.SERVICE_BY_IDS, value = "/"+ ApipApiNames.SN_6+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.SERVICE_BY_IDS)
public class ServiceByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ServiceByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.SERVICE, Service.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.SERVICE, Service.class, ID, request,response,authType);
    }
}