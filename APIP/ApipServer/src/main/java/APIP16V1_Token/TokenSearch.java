
package APIP16V1_Token;


import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import feip.feipData.Token;
import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import static constants.FieldNames.Token_Id;
import static constants.FieldNames.LAST_HEIGHT;


@WebServlet(name = ApiNames.TokenSearch, value = "/"+ApiNames.SN_16+"/"+ApiNames.Version2 +"/"+ApiNames.TokenSearch)
public class TokenSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Token_Id,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.TOKEN, Token.class,defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Token_Id,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.TOKEN, Token.class,defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}