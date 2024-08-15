package APIP2V2_Blockchain;

import constants.ApiNames;
import constants.IndicesNames;
import fch.fchData.Cash;
import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.CASH_ID;
import static server.FcdslRequestHandler.doIdsRequest;

@WebServlet(name = ApiNames.CashByIds, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version2 +"/"+ApiNames.CashByIds)
public class CashByIds extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.CASH, Cash.class, CASH_ID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.CASH, Cash.class, CASH_ID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}
