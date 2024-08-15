package APIP3V1_CidInfo;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import fch.fchData.Cash;
import feip.feipData.Nobody;
import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.FieldNames.*;

@WebServlet(name = ApiNames.NobodySearch, value = "/"+ApiNames.SN_3+"/"+ApiNames.Version2 +"/"+ApiNames.NobodySearch)
public class NobodySearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(DEATH_HEIGHT,false, DEATH_TX_INDEX,true,FID,true);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.NOBODY, Nobody.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_TIME,false,CASH_ID,true,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.CASH, Cash.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}
