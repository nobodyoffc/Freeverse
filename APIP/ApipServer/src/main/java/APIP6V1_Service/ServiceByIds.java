package APIP6V1_Service;

import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.Service;
import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.Strings.SID;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.ServiceByIds, value = "/"+ApiNames.SN_6+"/"+ApiNames.Version2 +"/"+ApiNames.ServiceByIds)
public class ServiceByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.SERVICE, Service.class, SID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.SERVICE, Service.class, SID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}