package APIP10V1_Box;

import constants.ApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import feip.feipData.Box;
import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.BID;
import static server.FcdslRequestHandler.doIdsRequest;

@WebServlet(name = ApiNames.BoxByIds, value = "/"+ApiNames.SN_10+"/"+ApiNames.Version2 +"/"+ApiNames.BoxByIds)
public class BoxByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.BOX, Box.class, FieldNames.BID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.BOX, Box.class, BID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}