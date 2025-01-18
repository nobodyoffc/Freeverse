package APIP8V1_Group;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import constants.FieldNames;
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
import java.util.Map;

import static constants.FieldNames.GID;
import static server.FcdslRequestHandler.getTMap;


@WebServlet(name = ApiNames.GroupByIds, value = "/"+ApiNames.SN_8+"/"+ApiNames.Version1 +"/"+ApiNames.GroupByIds)
public class GroupByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doGroupIdsRequest( FieldNames.GID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doGroupIdsRequest( GID, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }

    public static void doGroupIdsRequest(String keyFieldName, String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, Group> meetMap = getTMap(IndicesNames.GROUP, Group.class, keyFieldName, sid, request, authType, esClient, replier, jedis, Initiator.sessionHandler);
            if (meetMap == null) return;

            for(Group group :meetMap.values()){
                group.setMembers(null);
            }
            replier.reply0SuccessHttp(meetMap, jedis, null);
        }
    }
}