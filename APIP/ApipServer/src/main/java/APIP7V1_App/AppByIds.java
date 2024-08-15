package APIP7V1_App;

import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.App;
import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.AID;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.AppByIds, value = "/"+ApiNames.SN_7+"/"+ApiNames.Version2 +"/"+ApiNames.AppByIds)
public class AppByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.APP, App.class, AID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.APP, App.class, AID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}