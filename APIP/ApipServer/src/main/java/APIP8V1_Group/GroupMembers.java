package APIP8V1_Group;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import constants.IndicesNames;
import fcData.FcReplierHttp;
import feip.feipData.Group;
import initial.Initiator;
import tools.http.AuthType;
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


@WebServlet(name = ApiNames.GroupMembers, value = "/"+ApiNames.SN_8+"/"+ApiNames.Version1 +"/"+ApiNames.GroupMembers)
public class GroupMembers extends HttpServlet {
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
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        try (Jedis jedis = jedisPool.getResource()) {
            List<Group> meetList = doRequestForList(sid, IndicesNames.GROUP, Group.class, null, null, null, null, null, request, authType, esClient, replier, jedis, Initiator.sessionHandler);
            if (meetList == null) return;
            //Make data
            Map<String,String[]> dataMap = new HashMap<>();
            for(Group group:meetList){
                dataMap.put(group.getGid(),group.getMembers());
            }
            replier.reply0SuccessHttp(dataMap, jedis, null);
        }
    }
}