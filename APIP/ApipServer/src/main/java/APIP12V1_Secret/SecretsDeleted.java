
package APIP12V1_Secret;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.Secret;
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

import static constants.FieldNames.Secret_Id;
import static constants.FieldNames.LAST_HEIGHT;
import static constants.Strings.ACTIVE;
import static constants.Values.TRUE;


@WebServlet(name = ApiNames.SecretsDeleted, value = "/"+ApiNames.SN_12+"/"+ApiNames.Version2 +"/"+ApiNames.SecretsDeleted)
public class SecretsDeleted extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Secret_Id,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.SECRET, Secret.class, null,null,ACTIVE,TRUE,defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Secret_Id,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.SECRET, Secret.class, null,null,ACTIVE,TRUE,defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}