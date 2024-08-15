
package APIP15V1_Statement;


import constants.ApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import feip.feipData.Statement;
import initial.Initiator;
import javaTools.http.AuthType;

import static constants.FieldNames.Statement_Id;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.StatementByIds, value = "/"+ApiNames.SN_15+"/"+ApiNames.Version2 +"/"+ApiNames.StatementByIds)
public class StatementByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.STATEMENT, Statement.class, Statement_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.STATEMENT, Statement.class, Statement_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}