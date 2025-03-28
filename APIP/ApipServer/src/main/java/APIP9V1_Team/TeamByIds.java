package APIP9V1_Team;

import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.Team;
import initial.Initiator;
import utils.http.AuthType;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import appTools.Settings;

import java.io.IOException;
import java.util.Map;

import static constants.FieldNames.ID;
import server.FcdslRequestHandler;


@WebServlet(name = ApipApiNames.TEAM_BY_IDS, value = "/"+ ApipApiNames.SN_9+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TEAM_BY_IDS)
public class TeamByIds extends HttpServlet {
    private final Settings settings;
    private final FcdslRequestHandler fcdslRequestHandler;

    public TeamByIds() {
        this.settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
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
        FcdslRequestHandler fcdslRequestHandler = FcdslRequestHandler.checkRequest(request, response, authType, settings);
        if (fcdslRequestHandler == null) return;
        Map<String, Team> meetMap = fcdslRequestHandler.doRequestForMap(IndicesNames.TEAM, Team.class, ID);
        if (meetMap == null) return;
        for(Team team :meetMap.values()){
            team.setMembers(null);
            team.setExMembers(null);
            team.setNotAgreeMembers(null);
            team.setInvitees(null);
            team.setTransferee(null);
        }
        fcdslRequestHandler.getReplyBody().reply0SuccessHttp(meetMap,response);
    }
}