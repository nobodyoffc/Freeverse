package APIP9V1_Team;

import apip.apipData.Sort;
import appTools.Settings;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.Team;
import initial.Initiator;
import tools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.*;
import server.FcdslRequestHandler;

@WebServlet(name = ApipApiNames.MY_TEAMS, value = "/"+ ApipApiNames.SN_9+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.MY_TEAMS)
public class MyTeams extends HttpServlet {
    private final Settings settings;
    private final FcdslRequestHandler fcdslRequestHandler;

    public MyTeams() {
        this.settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,false,null,null);
        doRequest(defaultSort,request,response,authType,settings);  }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,false,null,null);
        doRequest(defaultSort,request,response,authType,settings);
    }
    protected void doRequest( List<Sort> sortList,HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        List<Team> meetList = fcdslRequestHandler.doRequestForList(IndicesNames.TEAM, Team.class, null, null, null, null, sortList, request, response, authType);
        if (meetList == null) return;
        for(Team team : meetList){
            team.setMembers(null);
            team.setExMembers(null);
            team.setNotAgreeMembers(null);
            team.setInvitees(null);
            team.setTransferee(null);
        }
        fcdslRequestHandler.getReplyBody().reply0SuccessHttp(meetList,response);
    }
}