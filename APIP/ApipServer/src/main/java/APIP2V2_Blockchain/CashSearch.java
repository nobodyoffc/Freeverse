package APIP2V2_Blockchain;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import fch.fchData.Cash;
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

import static constants.FieldNames.CASH_ID;
import static constants.FieldNames.LAST_TIME;

@WebServlet(name = ApiNames.CashSearch, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version1 +"/"+ApiNames.CashSearch)
public class CashSearch extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_TIME,false,CASH_ID,true,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.CASH, Cash.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_TIME,false,CASH_ID,true,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.CASH, Cash.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
}