package APIP7V1_App;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.App;
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

import static constants.FieldNames.AID;
import static constants.FieldNames.T_RATE;
import static constants.Strings.ACTIVE;


@WebServlet(name = ApiNames.AppSearch, value = "/"+ApiNames.SN_7+"/"+ApiNames.Version2 +"/"+ApiNames.AppSearch)
public class AppSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,AID,true);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.APP, App.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,AID,true);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.APP, App.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}