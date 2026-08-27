package SwapHall;

import config.Settings;
import constants.ApipApiNames;
import constants.IndicesNames;
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

import static constants.FieldNames.ID;


@WebServlet(name = ApipApiNames.SERVICE_BY_IDS+ ApipApiNames.SWAP, value = ApipApiNames.SwapHallPath + ApipApiNames.SERVICE_BY_IDS )
public class ServiceByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ServiceByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.SERVICE, Service.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.SERVICE, Service.class, ID, request,response,authType);
    }
}