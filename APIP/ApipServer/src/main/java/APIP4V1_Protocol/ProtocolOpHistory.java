package APIP4V1_Protocol;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.ProtocolHistory;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.OpNames.RATE;
import static constants.Strings.OP;


@WebServlet(name = ApiNames.ProtocolOpHistory, value = "/"+ApiNames.SN_4+"/"+ApiNames.Version1 +"/"+ApiNames.ProtocolOpHistory)

public class ProtocolOpHistory extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList("height",false,"index",false,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.PROTOCOL_HISTORY, ProtocolHistory.class, null,null,OP,RATE, defaultSort,request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList("height",false,"index",false,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.PROTOCOL_HISTORY, ProtocolHistory.class, null,null,OP,RATE, defaultSort,request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
}