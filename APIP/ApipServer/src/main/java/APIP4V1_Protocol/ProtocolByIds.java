package APIP4V1_Protocol;

import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.Protocol;
import initial.Initiator;
import tools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import appTools.Settings;
import static constants.FieldNames.PID;
import server.FcdslRequestHandler;


@WebServlet(name = ApipApiNames.PROTOCOL_BY_IDS, value = "/"+ ApipApiNames.SN_4+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.PROTOCOL_BY_IDS)
public class ProtocolByIds extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public ProtocolByIds() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doIdsRequest(IndicesNames.PROTOCOL, Protocol.class, PID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doIdsRequest(IndicesNames.PROTOCOL, Protocol.class, PID, request,response,authType);
    }
}