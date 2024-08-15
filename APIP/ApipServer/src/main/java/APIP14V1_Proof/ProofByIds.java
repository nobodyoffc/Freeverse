
package APIP14V1_Proof;


import constants.ApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import feip.feipData.Proof;
import initial.Initiator;
import javaTools.http.AuthType;

import static constants.FieldNames.Proof_Id;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.ProofByIds, value = "/"+ApiNames.SN_14+"/"+ApiNames.Version2 +"/"+ApiNames.ProofByIds)
public class ProofByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.PROOF, Proof.class, Proof_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.PROOF, Proof.class, Proof_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}