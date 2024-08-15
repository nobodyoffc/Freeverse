
package APIP12V1_Secret;


import constants.ApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import feip.feipData.Secret;
import initial.Initiator;
import javaTools.http.AuthType;

import static constants.FieldNames.Secret_Id;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.SecretByIds, value = "/"+ApiNames.SN_12+"/"+ApiNames.Version2 +"/"+ApiNames.SecretByIds)
public class SecretByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.SECRET, Secret.class, Secret_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.SECRET, Secret.class, Secret_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}