package APIP4V1_Protocol;

import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Protocol;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import config.Settings;
import server.FcHttpRequestHandler;

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.PROTOCOL_BY_IDS, value = "/"+ ApipApiNames.SN_4+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.PROTOCOL_BY_IDS)
public class ProtocolByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ProtocolByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.PROTOCOL, Protocol.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.PROTOCOL, Protocol.class, ID, request,response,authType);
    }
}