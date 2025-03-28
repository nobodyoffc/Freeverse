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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import appTools.Settings;
import server.FcdslRequestHandler;


@WebServlet(name = ApipApiNames.TEAM_MEMBERS, value = "/"+ ApipApiNames.SN_9+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TEAM_MEMBERS)
public class TeamMembers extends HttpServlet {
    private final Settings settings;
    private final FcdslRequestHandler fcdslRequestHandler;

    public TeamMembers() {
        this.settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request,response,authType,settings);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request,response,authType,settings);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        List<Team> meetList = fcdslRequestHandler.doRequestForList(IndicesNames.TEAM, Team.class, null, null, null, null, null, request, response, authType);
        if (meetList == null) return;
        //Make data
        Map<String,String[]> dataMap = new HashMap<>();
        for(Team team:meetList){
            dataMap.put(team.getId(),team.getMembers());
        }
        fcdslRequestHandler.getReplyBody().reply0SuccessHttp(dataMap,response);
    }
}