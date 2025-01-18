package APIP9V1_Team;

import apip.apipData.Sort;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import constants.IndicesNames;
import fcData.FcReplierHttp;
import feip.feipData.Team;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.*;
import static constants.Strings.ACTIVE;


@WebServlet(name = ApiNames.TeamSearch, value = "/"+ApiNames.SN_9+"/"+ApiNames.Version1 +"/"+ApiNames.TeamSearch)
public class TeamSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,TID,true);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.TEAM, Team.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE,false,T_RATE,false,TID,true);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.TEAM, Team.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }

    public static void doTeamSearchRequest(String sid, String filterField, String filterValue, String exceptField, String exceptValue, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        try (Jedis jedis = jedisPool.getResource()) {
            List<Team> meetList = FcdslRequestHandler.doRequestForList(sid, IndicesNames.TEAM, Team.class, filterField, filterValue, exceptField, exceptValue, sort, request, authType, esClient, replier, jedis, Initiator.sessionHandler);
            if (meetList == null) return;
            for(Team team :meetList){
                team.setMembers(null);
                team.setExMembers(null);
                team.setNotAgreeMembers(null);
                team.setInvitees(null);
                team.setTransferee(null);
            }
            replier.reply0SuccessHttp(meetList, jedis, null);
        }
    }
}