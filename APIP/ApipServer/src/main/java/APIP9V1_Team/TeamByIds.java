package APIP9V1_Team;

import constants.ApiNames;
import constants.IndicesNames;
import fcData.FcReplierHttp;
import feip.feipData.Team;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import java.io.IOException;
import java.util.Map;

import static constants.FieldNames.TID;
import static server.FcdslRequestHandler.getTMap;


@WebServlet(name = ApiNames.TeamByIds, value = "/"+ApiNames.SN_9+"/"+ApiNames.Version1 +"/"+ApiNames.TeamByIds)
public class TeamByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doTeamIdsRequest( Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doTeamIdsRequest(Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }

    public static void doTeamIdsRequest( String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, Team> meetMap = getTMap(IndicesNames.TEAM, Team.class, TID, sid, request, authType, esClient, replier, jedis, Initiator.sessionHandler);
            if (meetMap == null) return;
            for(Team team :meetMap.values()){
                team.setMembers(null);
                team.setExMembers(null);
                team.setNotAgreeMembers(null);
                team.setInvitees(null);
                team.setTransferee(null);
            }
            replier.reply0SuccessHttp(meetMap, jedis, null);
        }
    }
}