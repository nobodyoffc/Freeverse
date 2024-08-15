package APIP9V1_Team;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import constants.IndicesNames;
import fcData.FcReplier;
import feip.feipData.Team;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static server.FcdslRequestHandler.doRequestForList;


@WebServlet(name = ApiNames.TeamOtherPersons, value = "/"+ApiNames.SN_9+"/"+ApiNames.Version2 +"/"+ApiNames.TeamOtherPersons)
public class TeamOtherPersons extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws ServletException, IOException {
        FcReplier replier = new FcReplier(sid,response);
        try (Jedis jedis = jedisPool.getResource()) {
            List<Team> meetList = doRequestForList(sid, IndicesNames.TEAM, Team.class, null, null, null, null, null, request, response, authType, esClient, replier, jedis);
            if (meetList == null) return;
            //Make data
            Map<String,apip.apipData.TeamOtherPersonsData> dataMap = new HashMap<>();
            for(Team team:meetList) {
                apip.apipData.TeamOtherPersonsData teamOtherPersons = new apip.apipData.TeamOtherPersonsData();
                teamOtherPersons.setInvitees(team.getInvitees());
                teamOtherPersons.setTransferee(team.getTransferee());
                teamOtherPersons.setNotAgreeMembers(team.getNotAgreeMembers());
                dataMap.put(team.getTid(),teamOtherPersons);
            }
            replier.reply0Success(dataMap, jedis, null);
        }
    }
}