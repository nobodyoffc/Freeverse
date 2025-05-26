package APIP9V1_Team;

import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Team;
import initial.Initiator;
import utils.http.AuthType;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import config.Settings;

import java.io.IOException;
import java.util.Map;

import static constants.FieldNames.ID;
import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.TEAM_BY_IDS, value = "/"+ ApipApiNames.SN_9+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TEAM_BY_IDS)
public class TeamByIds extends HttpServlet {
    private final Settings settings;
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public TeamByIds() {
        this.settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doTeamIdsRequest( request,response,authType,settings);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doTeamIdsRequest( request,response,authType,settings);
    }

    public void doTeamIdsRequest( HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        FcHttpRequestHandler fcHttpRequestHandler = FcHttpRequestHandler.checkRequest(request, response, authType, settings);
        if (fcHttpRequestHandler == null) return;
        Map<String, Team> meetMap = fcHttpRequestHandler.doRequestForMap(IndicesNames.TEAM, Team.class, ID);
        if (meetMap == null) return;
        for(Team team :meetMap.values()){
            team.setMembers(null);
            team.setExMembers(null);
            team.setNotAgreeMembers(null);
            team.setInvitees(null);
            team.setTransferee(null);
        }
        fcHttpRequestHandler.getReplyBody().reply0SuccessHttp(meetMap,response);
    }
}