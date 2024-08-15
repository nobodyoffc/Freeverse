package APIP2V2_Blockchain;

import constants.ApiNames;
import constants.IndicesNames;
import fch.fchData.OpReturn;
import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.TX_ID;
import static server.FcdslRequestHandler.doIdsRequest;

@WebServlet(name = ApiNames.OpReturnByIds, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version2 +"/"+ApiNames.OpReturnByIds)
public class OpReturnByIds extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.OPRETURN, OpReturn.class, TX_ID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.OPRETURN, OpReturn.class, TX_ID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}
