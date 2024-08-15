package APIP5V1_Code;

import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.Code;
import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.CODE_ID;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.CodeByIds, value = "/"+ApiNames.SN_5+"/"+ApiNames.Version2 +"/"+ApiNames.CodeByIds)
public class CodeByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.CODE, Code.class, CODE_ID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.CODE, Code.class, CODE_ID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}