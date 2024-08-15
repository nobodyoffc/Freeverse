package APIP9V1_Team;

import constants.ApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import feip.feipData.Team;
import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.TID;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.TeamByIds, value = "/"+ApiNames.SN_9+"/"+ApiNames.Version2 +"/"+ApiNames.TeamByIds)
public class TeamByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.TEAM, Team.class, FieldNames.TID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.TEAM, Team.class, TID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}