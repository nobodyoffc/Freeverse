package APIP8V1_Group;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.Group;
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


@WebServlet(name = ApiNames.GroupSearch, value = "/"+ApiNames.SN_8+"/"+ApiNames.Version2 +"/"+ApiNames.GroupSearch)
public class GroupSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(T_CDD,false,GID,true,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.GROUP, Group.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(T_CDD,false,GID,true,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.GROUP, Group.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}