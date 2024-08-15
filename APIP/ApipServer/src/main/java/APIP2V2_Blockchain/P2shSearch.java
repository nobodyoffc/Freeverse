package APIP2V2_Blockchain;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import fch.fchData.P2SH;
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

import static constants.FieldNames.BIRTH_HEIGHT;
import static constants.FieldNames.FID;


@WebServlet(name = ApiNames.P2shSearch, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version2 +"/"+ApiNames.P2shSearch)
public class P2shSearch extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(BIRTH_HEIGHT,false,FID,true,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.P2SH, P2SH.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(BIRTH_HEIGHT,false,FID,true,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.P2SH, P2SH.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}