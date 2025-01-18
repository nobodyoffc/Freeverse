package APIP2V2_Blockchain;

import constants.ApiNames;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = ApiNames.TxByFid, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version1 +"/"+ApiNames.TxByFid)
public class TxByFid extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        FcdslRequestHandler.doFidTxMaskRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool, Initiator.sessionHandler);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        FcdslRequestHandler.doFidTxMaskRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool, Initiator.sessionHandler);
    }
}
