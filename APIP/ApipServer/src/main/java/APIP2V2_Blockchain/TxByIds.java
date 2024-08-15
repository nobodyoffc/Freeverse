package APIP2V2_Blockchain;

import constants.ApiNames;

import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.TX_ID;

@WebServlet(name = ApiNames.TxByIds, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version2 +"/"+ApiNames.TxByIds)
public class TxByIds extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        FcdslRequestHandler.doTxInfoRequest(Initiator.sid,true,TX_ID,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        FcdslRequestHandler.doTxInfoRequest(Initiator.sid,true,TX_ID,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
}
