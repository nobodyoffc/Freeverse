package APIP9V1_Team;

import apip.apipData.MyTeamData;
import apip.apipData.Sort;
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
import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.*;
import static server.FcdslRequestHandler.doRequestForList;

@WebServlet(name = ApiNames.MyTeams, value = "/"+ApiNames.SN_9+"/"+ApiNames.Version2 +"/"+ApiNames.MyTeams)
public class MyTeams extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,TID,false,null,null);
        doRequest(Initiator.sid,defaultSort,request,response,authType,Initiator.esClient,Initiator.jedisPool);  }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,TID,false,null,null);
        doRequest(Initiator.sid,defaultSort,request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    protected void doRequest(String sid, List<Sort> sortList,HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws ServletException, IOException {
        FcReplier replier = new FcReplier(sid,response);
        try (Jedis jedis = jedisPool.getResource()) {
            List<Team> meetList = doRequestForList(sid, IndicesNames.TEAM, Team.class, null, null, null, null, sortList, request, response, authType, esClient, replier, jedis);
            if (meetList == null) return;
            //Make data
            List<MyTeamData> dataList = new ArrayList<>();
            for(Team team: meetList){
                MyTeamData data = new MyTeamData();
                data.setStdName(team.getStdName());
                data.setMemberNum(team.getMemberNum());
                data.setDesc(team.getDesc());
                data.setTid(team.getTid());
                dataList.add(data);
            }
            replier.reply0Success(dataList, jedis, null);
        }
    }
}