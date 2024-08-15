package APIP3V1_CidInfo;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.FieldNames;
import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.FieldNames.FID;

@WebServlet(name = ApiNames.CidInfoSearch, value = "/"+ApiNames.SN_3+"/"+ApiNames.Version2 +"/"+ApiNames.CidInfoSearch)
public class CidInfoSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> sort = Sort.makeSortList(FieldNames.NAME_TIME,false,FID,true,null,null);
        FcdslRequestHandler.doCidInfoRequest(Initiator.sid,sort,false,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> sort = Sort.makeSortList(FieldNames.NAME_TIME,false,FID,true,null,null);
        FcdslRequestHandler.doCidInfoRequest(Initiator.sid, sort, false, request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
}