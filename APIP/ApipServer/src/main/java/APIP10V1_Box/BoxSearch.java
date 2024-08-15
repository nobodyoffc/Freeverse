package APIP10V1_Box;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.Box;
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

import static constants.FieldNames.BID;
import static constants.FieldNames.LAST_HEIGHT;

@WebServlet(name = ApiNames.BoxSearch, value = "/"+ApiNames.SN_10+"/"+ApiNames.Version2 +"/"+ApiNames.BoxSearch)
public class BoxSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,BID,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.BOX, Box.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,BID,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.BOX, Box.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}