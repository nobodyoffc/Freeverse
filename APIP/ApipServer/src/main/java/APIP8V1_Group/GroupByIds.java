package APIP8V1_Group;

import constants.ApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import feip.feipData.Group;
import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.GID;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.GroupByIds, value = "/"+ApiNames.SN_8+"/"+ApiNames.Version2 +"/"+ApiNames.GroupByIds)
public class GroupByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.GROUP, Group.class, FieldNames.GID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.GROUP, Group.class, GID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}