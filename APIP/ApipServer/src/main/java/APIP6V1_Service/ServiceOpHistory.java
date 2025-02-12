package APIP6V1_Service;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.ServiceHistory;
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


@WebServlet(name = ApiNames.ServiceOpHistory, value = "/"+ApiNames.SN_6+"/"+ApiNames.Version1 +"/"+ApiNames.ServiceOpHistory)

public class ServiceOpHistory extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList("height",false,"index",false,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.SERVICE_HISTORY, ServiceHistory.class, null,null,OP,RATE, defaultSort,request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList("height",false,"index",false,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.SERVICE_HISTORY, ServiceHistory.class, null,null,OP,RATE, defaultSort,request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
}