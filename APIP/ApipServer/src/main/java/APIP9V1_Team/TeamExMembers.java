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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import config.Settings;
import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.TEAM_EX_MEMBERS, value = "/"+ ApipApiNames.SN_9+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TEAM_EX_MEMBERS)
public class TeamExMembers extends HttpServlet {
    private final Settings settings;
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public TeamExMembers() {
        this.settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
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
        List<Team> meetList = fcHttpRequestHandler.doRequestForList(IndicesNames.TEAM, Team.class, null, null, null, null, null, request, response, authType);
        if (meetList == null) return;
        //Make data
        Map<String,String[]> dataMap = new HashMap<>();
        for(Team team:meetList){
            dataMap.put(team.getId(),team.getExMembers());
        }
        fcHttpRequestHandler.getReplyBody().reply0SuccessHttp(dataMap,response);
    }
}