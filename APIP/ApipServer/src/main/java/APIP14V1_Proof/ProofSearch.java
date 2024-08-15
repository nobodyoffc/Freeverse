
package APIP14V1_Proof;


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

import feip.feipData.Proof;
import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import static constants.FieldNames.Proof_Id;
import static constants.FieldNames.LAST_HEIGHT;
import static constants.Strings.ACTIVE;
import static constants.Values.FALSE;


@WebServlet(name = ApiNames.ProofSearch, value = "/"+ApiNames.SN_14+"/"+ApiNames.Version2 +"/"+ApiNames.ProofSearch)
public class ProofSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Proof_Id,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.PROOF, Proof.class, null,null,ACTIVE,FALSE,defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Proof_Id,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.PROOF, Proof.class, null,null,ACTIVE,FALSE,defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}