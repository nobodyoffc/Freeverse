package APIP2V2_Blockchain;

import constants.ApiNames;
import constants.IndicesNames;
import fch.fchData.P2SH;
import initial.Initiator;
import tools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.FID;
import static server.FcdslRequestHandler.doIdsRequest;

@WebServlet(name = ApiNames.P2shByIds, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version1 +"/"+ApiNames.P2shByIds)
public class P2shByIds extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.P2SH, P2SH.class, FID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.P2SH, P2SH.class, FID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
}
