package APIP6V1_Service;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.Service;
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

import static constants.FieldNames.T_RATE;
import static constants.Strings.ACTIVE;
import static constants.Strings.SID;


@WebServlet(name = ApiNames.ServiceSearch, value = "/"+ApiNames.SN_6+"/"+ApiNames.Version2 +"/"+ApiNames.ServiceSearch)
public class ServiceSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,SID,true);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.SERVICE, Service.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,SID,true);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.SERVICE, Service.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}