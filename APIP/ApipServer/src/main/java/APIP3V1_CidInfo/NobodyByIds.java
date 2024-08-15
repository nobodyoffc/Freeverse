package APIP3V1_CidInfo;

import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.Nobody;
import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.FID;
import static server.FcdslRequestHandler.doIdsRequest;

@WebServlet(name = ApiNames.NobodyByIds, value = "/"+ApiNames.SN_3+"/"+ApiNames.Version2 +"/"+ApiNames.NobodyByIds)
public class NobodyByIds extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.NOBODY, Nobody.class, FID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.NOBODY, Nobody.class, FID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}
