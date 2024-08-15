
package APIP16V1_Token;


import constants.ApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import feip.feipData.Token;
import initial.Initiator;
import javaTools.http.AuthType;

import static constants.FieldNames.Token_Id;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.TokenByIds, value = "/"+ApiNames.SN_16+"/"+ApiNames.Version2 +"/"+ApiNames.TokenByIds)
public class TokenByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.TOKEN, Token.class, Token_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.TOKEN, Token.class, Token_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}