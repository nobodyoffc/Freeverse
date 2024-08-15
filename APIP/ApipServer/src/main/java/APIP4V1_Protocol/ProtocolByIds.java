package APIP4V1_Protocol;

import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.Protocol;
import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.PID;
import static constants.Strings.SID;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.ProtocolByIds, value = "/"+ApiNames.SN_4+"/"+ApiNames.Version2 +"/"+ApiNames.ProtocolByIds)
public class ProtocolByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.PROTOCOL, Protocol.class, PID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.PROTOCOL, Protocol.class, PID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}