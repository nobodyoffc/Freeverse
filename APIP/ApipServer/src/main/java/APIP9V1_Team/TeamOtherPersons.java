package APIP9V1_Team;

import appTools.Settings;
import constants.IndicesNames;
import feip.feipData.Team;
import initial.Initiator;
import server.ApipApiNames;
import server.FcdslRequestHandler;
import tools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@WebServlet(name = ApipApiNames.TEAM_OTHER_PERSONS, value = "/"+ ApipApiNames.SN_9+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TEAM_OTHER_PERSONS)
public class TeamOtherPersons extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public TeamOtherPersons() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request,response,authType);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType) throws ServletException, IOException {
        List<Team> meetList = fcdslRequestHandler.doRequestForList(IndicesNames.TEAM, Team.class, null, null, null, null, null, request, response, authType);
        if (meetList == null) return;
        //Make data
        Map<String, Team> dataMap = new HashMap<>();
        for(Team team:meetList) {
            team.setMembers(null);
            dataMap.put(team.getId(),team);
        }
        fcdslRequestHandler.getReplyBody().reply0SuccessHttp(dataMap,response);
    }
}