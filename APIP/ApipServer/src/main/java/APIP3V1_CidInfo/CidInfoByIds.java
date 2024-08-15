package APIP3V1_CidInfo;

import constants.ApiNames;

import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = ApiNames.CidInfoByIds, value = "/"+ApiNames.SN_3+"/"+ApiNames.Version2 +"/"+ApiNames.CidInfoByIds)
public class CidInfoByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        FcdslRequestHandler.doCidInfoRequest(Initiator.sid, null, true,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        FcdslRequestHandler.doCidInfoRequest(Initiator.sid, null, true, request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
}
