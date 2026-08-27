package APIP4V1_Protocol;

import constants.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Protocol;
import initial.Initiator;
import utils.http.AuthType;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import config.Settings;
import server.FcHttpRequestHandler;

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.PROTOCOL_BY_IDS, value = "/"+ ApipApiNames.SN_4+"/"+ ApipApiNames.PROTOCOL_BY_IDS +"/"+ ApipApiNames.VER_1)
public class ProtocolByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ProtocolByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.PROTOCOL, Protocol.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.PROTOCOL, Protocol.class, ID, request,response,authType);
    }
}